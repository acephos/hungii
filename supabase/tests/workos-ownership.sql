-- Only Hungii's project. Synthetic account records roll back.
begin;
do $$
declare a public.hungii_accounts; same public.hungii_accounts; b public.hungii_accounts; denied boolean:=false;
begin
  select * into a from public.resolve_hungii_account(repeat('x',43));
  select * into same from public.resolve_hungii_account(repeat('x',43));
  select * into b from public.resolve_hungii_account(repeat('y',43));
  assert a.id=same.id and a.id<>b.id,'Identity mapping not isolated/idempotent';
  assert not has_table_privilege('authenticated','public.hungii_accounts','SELECT'),'Client can read identity table';
  assert not has_function_privilege('authenticated','public.resolve_hungii_account(text)','EXECUTE'),'Client can choose identity';
  insert into public.hungii_state(user_id,state) values(a.id,'{"ciphertext":"synthetic"}');
  perform public.begin_swiggy_consent(a.id,'synthetic-deletion-state','cipher','2026-10-02.1');
  assert public.begin_hungii_account_deletion(a.id),'Deletion failed';
  assert not exists(select from public.hungii_state where user_id=a.id),'Tracker survived deletion';
  assert not exists(select from public.swiggy_oauth_states where user_id=a.id),'OAuth survived deletion';
  assert not exists(select from public.swiggy_consent_epochs where user_id=a.id),'Epoch survived deletion';
  begin
    insert into public.hungii_state(user_id,state) values(a.id,'{"ciphertext":"late"}');
  exception when raise_exception then denied:=true;
  end;
  assert denied,'In-flight write revived deleted data';
  select * into same from public.resolve_hungii_account(repeat('x',43));
  assert same.status='deleting' and same.id=a.id,'Pending deletion recreated an active account';
  assert (select status='active' from public.hungii_accounts where id=b.id),'Other account erased';
  update public.hungii_accounts set status='deleted',delete_after=now()-interval '1 second' where id=a.id;
  perform public.purge_hungii_expired();
  assert not exists(select from public.hungii_accounts where id=a.id),'Expired deletion fence retained';
end $$;
rollback;
select 'WorkOS account ownership, deletion fences and expiry passed' as verification;
