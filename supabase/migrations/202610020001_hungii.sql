-- User data and credentials are separate; API response bodies are not retained.
create table public.hungii_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  state jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.hungii_state enable row level security;
create policy own_state on public.hungii_state for all to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table public.swiggy_connections (
  user_id uuid primary key references auth.users(id) on delete cascade,
  encrypted_token text not null,
  expires_at timestamptz not null,
  address_id text,
  connected_at timestamptz not null default now()
);
alter table public.swiggy_connections enable row level security;
revoke all on public.swiggy_connections from anon, authenticated;

create table public.swiggy_oauth_states (
  state_hash text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  encrypted_verifier text not null,
  expires_at timestamptz not null
);
alter table public.swiggy_oauth_states enable row level security;
revoke all on public.swiggy_oauth_states from anon, authenticated;

create function public.consume_swiggy_oauth_state(state_digest text)
returns setof public.swiggy_oauth_states language sql security definer
set search_path = public as $$
  delete from public.swiggy_oauth_states
  where state_hash = state_digest and expires_at > now()
  returning *;
$$;
revoke all on function public.consume_swiggy_oauth_state(text) from public, anon, authenticated;
grant execute on function public.consume_swiggy_oauth_state(text) to service_role;
