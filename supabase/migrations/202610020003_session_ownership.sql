-- Backend-only access: even tracker ciphertext must pass the authenticated API.
revoke all on public.hungii_state from anon, authenticated;
grant select, insert, update, delete on public.hungii_state, public.swiggy_connections, public.swiggy_oauth_states to service_role;

create table public.swiggy_consent_epochs (
  user_id uuid primary key references auth.users(id) on delete cascade,
  generation bigint not null default 0
);
alter table public.swiggy_consent_epochs enable row level security;
revoke all on public.swiggy_consent_epochs from anon, authenticated;
grant select, insert, update, delete on public.swiggy_consent_epochs to service_role;
alter table public.swiggy_oauth_states add column generation bigint not null default 0;
alter table public.swiggy_connections add column generation bigint not null default 0;

create table public.swiggy_mcp_sessions (
  user_id uuid primary key references public.swiggy_connections(user_id) on delete cascade,
  generation bigint not null,
  phase text not null default 'new' check (phase in ('new','initializing','ready','blocked')),
  encrypted_metadata text,
  lease_owner uuid,
  lease_until timestamptz,
  request_times timestamptz[] not null default '{}',
  cooldown_until timestamptz,
  blocked_code text,
  expires_at timestamptz not null,
  updated_at timestamptz not null default now()
);
alter table public.swiggy_mcp_sessions enable row level security;
revoke all on public.swiggy_mcp_sessions from anon, authenticated;
grant select, insert, update, delete on public.swiggy_mcp_sessions to service_role;

create function public.begin_swiggy_consent(owner_id uuid, state_digest text, verifier_ciphertext text, privacy_version text)
returns bigint language plpgsql security definer set search_path = '' as $$
declare epoch bigint;
begin
  insert into public.swiggy_consent_epochs(user_id) values(owner_id) on conflict do nothing;
  update public.swiggy_consent_epochs set generation=generation+1 where user_id=owner_id returning generation into epoch;
  delete from public.swiggy_oauth_states where user_id=owner_id;
  insert into public.swiggy_oauth_states(state_hash,user_id,encrypted_verifier,expires_at,consent_version,consented_at,generation)
  values(state_digest,owner_id,verifier_ciphertext,now()+interval '10 minutes',privacy_version,now(),epoch);
  return epoch;
end $$;

create function public.store_swiggy_connection(owner_id uuid, expected_generation bigint, token_ciphertext text, expiry timestamptz, privacy_version text, consent_time timestamptz)
returns boolean language plpgsql security definer set search_path = '' as $$
declare epoch bigint;
begin
  select generation into epoch from public.swiggy_consent_epochs where user_id=owner_id for update;
  if epoch is null or epoch <> expected_generation then return false; end if;
  delete from public.swiggy_mcp_sessions where user_id=owner_id;
  insert into public.swiggy_connections(user_id, generation, encrypted_token, expires_at, consent_version, consented_at)
  values(owner_id, epoch, token_ciphertext, expiry, privacy_version, consent_time)
  on conflict(user_id) do update set generation=excluded.generation, encrypted_token=excluded.encrypted_token,
    expires_at=excluded.expires_at, consent_version=excluded.consent_version, consented_at=excluded.consented_at,
    encrypted_address_id=null, connected_at=now();
  return true;
end $$;

create function public.withdraw_swiggy_consent(owner_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into public.swiggy_consent_epochs(user_id,generation) values(owner_id,1)
  on conflict(user_id) do update set generation=public.swiggy_consent_epochs.generation+1;
  delete from public.swiggy_oauth_states where user_id=owner_id;
  delete from public.swiggy_connections where user_id=owner_id;
end $$;

-- This transaction claims a durable lease; a row lock alone cannot span HTTP.
create function public.claim_swiggy_session(owner_id uuid, expected_generation bigint, lease_id uuid)
returns setof public.swiggy_mcp_sessions language plpgsql security definer set search_path = '' as $$
declare conn public.swiggy_connections; session public.swiggy_mcp_sessions;
begin
  select * into conn from public.swiggy_connections where user_id=owner_id for update;
  if not found or conn.generation<>expected_generation or conn.expires_at<=now() then return; end if;
  insert into public.swiggy_mcp_sessions(user_id,generation,expires_at)
  values(owner_id,conn.generation,conn.expires_at) on conflict do nothing;
  select * into session from public.swiggy_mcp_sessions where user_id=owner_id for update;
  if session.generation<>expected_generation then return; end if;
  if session.lease_until>now() then return; end if;
  if session.phase='initializing' then
    update public.swiggy_mcp_sessions set phase='blocked', blocked_code='HUNGII_INITIALIZATION_UNCERTAIN', lease_owner=null, lease_until=null where user_id=owner_id;
    return query select * from public.swiggy_mcp_sessions where user_id=owner_id;
    return;
  end if;
  update public.swiggy_mcp_sessions set lease_owner=lease_id,lease_until=now()+interval '45 seconds',
    phase=case when phase='new' then 'initializing' else phase end,updated_at=now() where user_id=owner_id;
  return query select * from public.swiggy_mcp_sessions where user_id=owner_id;
end $$;

create function public.reserve_swiggy_request(owner_id uuid, expected_generation bigint, lease_id uuid)
returns integer language plpgsql security definer set search_path = '' as $$
declare session public.swiggy_mcp_sessions; times timestamptz[]; burst integer;
begin
  select * into session from public.swiggy_mcp_sessions where user_id=owner_id for update;
  if not found or session.generation<>expected_generation or session.lease_owner is distinct from lease_id or session.lease_until<=now() then return -1; end if;
  if session.phase='blocked' then return -1; end if;
  if session.cooldown_until>now() then return greatest(1,ceil(extract(epoch from session.cooldown_until-now()))::integer); end if;
  select coalesce(array_agg(t),'{}'::timestamptz[]) into times from unnest(session.request_times) t where t>now()-interval '60 seconds';
  select count(*) into burst from unnest(times) t where t>now()-interval '10 seconds';
  if cardinality(times)>=55 or burst>=12 then return 10; end if;
  update public.swiggy_mcp_sessions set request_times=array_append(times,now()) where user_id=owner_id;
  return 0;
end $$;

create function public.finish_swiggy_session(owner_id uuid, expected_generation bigint, lease_id uuid, metadata_ciphertext text, failure_code text, retry_seconds integer)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  update public.swiggy_mcp_sessions set encrypted_metadata=coalesce(metadata_ciphertext,encrypted_metadata),
    phase=case when failure_code is not null then 'blocked' when metadata_ciphertext is not null then 'ready' else phase end,
    blocked_code=failure_code,cooldown_until=case when retry_seconds>0 then now()+make_interval(secs=>least(retry_seconds,86400)) else cooldown_until end,
    lease_owner=null,lease_until=null,updated_at=now()
  where user_id=owner_id and generation=expected_generation and lease_owner=lease_id and lease_until>now();
  return found;
end $$;

-- Expiry purges happen on access as well; paused Free projects cannot run Cron.
create function public.purge_hungii_expired()
returns void language plpgsql security definer set search_path = '' as $$
begin
  delete from public.swiggy_oauth_states where expires_at<=now();
  delete from public.swiggy_connections where expires_at<=now();
  delete from public.hungii_state where updated_at<now()-interval '90 days';
end $$;

revoke all on function public.begin_swiggy_consent(uuid,text,text,text), public.store_swiggy_connection(uuid,bigint,text,timestamptz,text,timestamptz), public.withdraw_swiggy_consent(uuid), public.claim_swiggy_session(uuid,bigint,uuid), public.reserve_swiggy_request(uuid,bigint,uuid), public.finish_swiggy_session(uuid,bigint,uuid,text,text,integer), public.purge_hungii_expired() from public, anon, authenticated;
grant execute on function public.begin_swiggy_consent(uuid,text,text,text), public.store_swiggy_connection(uuid,bigint,text,timestamptz,text,timestamptz), public.withdraw_swiggy_consent(uuid), public.claim_swiggy_session(uuid,bigint,uuid), public.reserve_swiggy_request(uuid,bigint,uuid), public.finish_swiggy_session(uuid,bigint,uuid,text,text,integer), public.purge_hungii_expired() to service_role;
