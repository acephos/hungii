-- WorkOS owns sign-in; Hungii owns stable UUIDs and backend-only data.
create table public.hungii_accounts (
  id uuid primary key default gen_random_uuid(),
  identity_hash text not null unique,
  status text not null default 'active' check(status in ('active','deleting','deleted')),
  delete_after timestamptz,
  created_at timestamptz not null default now()
);
alter table public.hungii_accounts enable row level security;
revoke all on public.hungii_accounts from public, anon, authenticated;
grant select,insert,update,delete on public.hungii_accounts to service_role;

-- Preserve existing UUIDs without silently linking an unrelated WorkOS identity.
insert into public.hungii_accounts(id,identity_hash)
select id,'legacy-supabase:'||id::text from auth.users
where id in (select user_id from public.hungii_state union select user_id from public.swiggy_connections
  union select user_id from public.swiggy_oauth_states union select user_id from public.swiggy_consent_epochs);
alter table public.hungii_state drop constraint hungii_state_user_id_fkey;
alter table public.swiggy_connections drop constraint swiggy_connections_user_id_fkey;
alter table public.swiggy_oauth_states drop constraint swiggy_oauth_states_user_id_fkey;
alter table public.swiggy_consent_epochs drop constraint swiggy_consent_epochs_user_id_fkey;
alter table public.hungii_state add foreign key(user_id) references public.hungii_accounts(id) on delete cascade;
alter table public.swiggy_connections add foreign key(user_id) references public.hungii_accounts(id) on delete cascade;
alter table public.swiggy_oauth_states add foreign key(user_id) references public.hungii_accounts(id) on delete cascade;
alter table public.swiggy_consent_epochs add foreign key(user_id) references public.hungii_accounts(id) on delete cascade;
drop policy if exists own_state on public.hungii_state;

create function public.resolve_hungii_account(subject_hash text)
returns setof public.hungii_accounts language plpgsql security definer set search_path='' as $$
begin
  if subject_hash !~ '^[A-Za-z0-9_-]{43}$' then raise exception 'Invalid identity hash'; end if;
  insert into public.hungii_accounts(identity_hash) values(subject_hash) on conflict do nothing;
  return query select * from public.hungii_accounts where identity_hash=subject_hash;
end $$;

-- A shared row lock prevents requests already in flight from restoring erased data.
create function public.require_active_hungii_account()
returns trigger language plpgsql security definer set search_path='' as $$
declare account_status text;
begin
  select status into account_status from public.hungii_accounts where id=new.user_id for share;
  if account_status is distinct from 'active' then raise exception 'Account is not active'; end if;
  return new;
end $$;
create trigger active_owner before insert or update on public.hungii_state for each row execute function public.require_active_hungii_account();
create trigger active_owner before insert or update on public.swiggy_connections for each row execute function public.require_active_hungii_account();
create trigger active_owner before insert or update on public.swiggy_oauth_states for each row execute function public.require_active_hungii_account();
create trigger active_owner before insert or update on public.swiggy_consent_epochs for each row execute function public.require_active_hungii_account();

create function public.begin_hungii_account_deletion(owner_id uuid)
returns boolean language plpgsql security definer set search_path='' as $$
begin
  update public.hungii_accounts set status='deleting' where id=owner_id and status in ('active','deleting');
  if not found then return false; end if;
  delete from public.hungii_state where user_id=owner_id;
  delete from public.swiggy_oauth_states where user_id=owner_id;
  delete from public.swiggy_connections where user_id=owner_id;
  delete from public.swiggy_consent_epochs where user_id=owner_id;
  return true;
end $$;

create or replace function public.purge_hungii_expired()
returns void language plpgsql security definer set search_path='' as $$
begin
  delete from public.swiggy_oauth_states where expires_at<=now();
  delete from public.swiggy_connections where expires_at<=now();
  delete from public.hungii_state where updated_at<now()-interval '90 days';
  delete from public.hungii_accounts where status='deleted' and delete_after<=now();
end $$;
revoke all on function public.resolve_hungii_account(text),public.require_active_hungii_account(),public.begin_hungii_account_deletion(uuid) from public,anon,authenticated;
grant execute on function public.resolve_hungii_account(text),public.begin_hungii_account_deletion(uuid) to service_role;
