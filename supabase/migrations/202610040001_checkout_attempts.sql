-- Checkout recovery is backend-only and encrypted. Unknown placements block retries.
create table public.hungii_checkouts (
  user_id uuid not null references public.hungii_accounts(id) on delete cascade,
  request_id uuid not null,
  phase text not null check(phase in ('placing','pending','confirmed','failed','unresolved')),
  ciphertext text not null,
  created_at timestamptz not null default now(),
  primary key(user_id,request_id)
);
create unique index one_active_checkout on public.hungii_checkouts(user_id) where phase in ('placing','pending','unresolved');
alter table public.hungii_checkouts enable row level security;
revoke all on public.hungii_checkouts from public, anon, authenticated;
grant select,insert,update,delete on public.hungii_checkouts to service_role;
create trigger active_owner before insert or update on public.hungii_checkouts for each row execute function public.require_active_hungii_account();
create function public.begin_hungii_checkout(owner_id uuid,checkout_id uuid,encrypted_attempt text)
returns boolean language plpgsql security definer set search_path='' as $$
begin
  perform 1 from public.hungii_accounts where id=owner_id and status='active' for update;
  if not found then return false; end if;
  if exists(select 1 from public.hungii_checkouts where user_id=owner_id and (request_id=checkout_id or phase in ('placing','pending','unresolved'))) then return false; end if;
  insert into public.hungii_checkouts(user_id,request_id,phase,ciphertext) values(owner_id,checkout_id,'placing',encrypted_attempt);
  return true;
end $$;
revoke all on function public.begin_hungii_checkout(uuid,uuid,text) from public, anon, authenticated;
grant execute on function public.begin_hungii_checkout(uuid,uuid,text) to service_role;
-- Unresolved attempts intentionally have no TTL: deletion is explicit, never a retry unlock.

-- Hold the account row lock while erasing recovery, so in-flight attempts cannot
-- recreate records between deletion and the ownership tombstone.
create function public.erase_hungii_checkout_on_deletion()
returns trigger language plpgsql security definer set search_path='' as $$
begin
  if new.status in ('deleting','deleted') then
    delete from public.hungii_checkouts where user_id=new.id;
  end if;
  return new;
end $$;
create trigger erase_checkout_on_deletion after update of status on public.hungii_accounts
for each row execute function public.erase_hungii_checkout_on_deletion();
revoke all on function public.erase_hungii_checkout_on_deletion() from public,anon,authenticated;
