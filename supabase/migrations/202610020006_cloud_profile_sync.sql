-- Compare the last observed cloud revision to avoid overwriting another device.
create function public.save_hungii_state(owner_id uuid, encrypted_state jsonb, expected_update timestamptz)
returns table(saved boolean, updated_at timestamptz)
language plpgsql security definer set search_path='' as $$
declare current_update timestamptz; next_update timestamptz;
begin
  perform 1 from public.hungii_accounts where id=owner_id and status='active' for share;
  if not found then raise exception 'Account is not active'; end if;
  -- Serialize the absent-row case as well as existing records for this account.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text,614));
  select s.updated_at into current_update from public.hungii_state s where s.user_id=owner_id for update;
  if current_update is distinct from expected_update then
    return query select false,current_update; return;
  end if;
  next_update=clock_timestamp();
  insert into public.hungii_state(user_id,state,updated_at) values(owner_id,encrypted_state,next_update)
  on conflict(user_id) do update set state=excluded.state,updated_at=excluded.updated_at;
  return query select true,next_update;
end $$;
revoke all on function public.save_hungii_state(uuid,jsonb,timestamptz) from public,anon,authenticated;
grant execute on function public.save_hungii_state(uuid,jsonb,timestamptz) to service_role;
