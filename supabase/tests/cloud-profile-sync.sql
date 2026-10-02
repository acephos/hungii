-- Own project only; synthetic account/data roll back.
begin;
do $$
declare a public.hungii_accounts; first_save record; next_save record; refused record; denied boolean:=false;
begin
  select * into a from public.resolve_hungii_account(repeat('z',43));
  assert not has_function_privilege('authenticated','public.save_hungii_state(uuid,jsonb,timestamptz)','EXECUTE'),'Client can choose an owner';
  select * into first_save from public.save_hungii_state(a.id,'{"ciphertext":"first"}',null);
  assert first_save.saved and first_save.updated_at is not null,'Initial profile not saved';
  select * into refused from public.save_hungii_state(a.id,'{"ciphertext":"unobserved"}',null);
  assert not refused.saved,'New device overwrote an unseen profile';
  select * into next_save from public.save_hungii_state(a.id,'{"ciphertext":"second"}',first_save.updated_at);
  assert next_save.saved and next_save.updated_at<>first_save.updated_at,'Observed revision did not save';
  select * into refused from public.save_hungii_state(a.id,'{"ciphertext":"stale"}',first_save.updated_at);
  assert not refused.saved,'Stale device overwrote a newer profile';
  assert (select state->>'ciphertext'='second' from public.hungii_state where user_id=a.id),'Conflict changed data';
  perform public.begin_hungii_account_deletion(a.id);
  begin
    perform public.save_hungii_state(a.id,'{"ciphertext":"after-deletion"}',null);
  exception when raise_exception then denied:=true;
  end;
  assert denied,'Deleted profile was restored';
end $$;
rollback;
select 'Cloud profile revision and deletion safeguards passed' as verification;
