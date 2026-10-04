-- Run only in a disposable database or reviewed transaction. No provider calls.
-- Apply checkout_attempts migration in the SAME outer transaction, then rollback.
do $$
declare owner uuid; other uuid; first uuid:=gen_random_uuid(); second uuid:=gen_random_uuid();
begin
  insert into public.hungii_accounts(identity_hash) values('checkout-test-'||gen_random_uuid()) returning id into owner;
  insert into public.hungii_accounts(identity_hash) values('checkout-test-'||gen_random_uuid()) returning id into other;
  if not public.begin_hungii_checkout(owner,first,'test-ciphertext') then raise exception 'First checkout rejected'; end if;
  if public.begin_hungii_checkout(owner,first,'replay') then raise exception 'Replay accepted'; end if;
  if public.begin_hungii_checkout(owner,second,'overlap') then raise exception 'Overlapping checkout accepted'; end if;
  if not public.begin_hungii_checkout(other,second,'other-owner') then raise exception 'Owners interfere'; end if;
  update public.hungii_checkouts set phase='unresolved' where user_id=owner;
  if public.begin_hungii_checkout(owner,second,'retry') then raise exception 'Unknown placement unlocked'; end if;
  update public.hungii_checkouts set phase='confirmed' where user_id=owner;
  if not public.begin_hungii_checkout(owner,second,'new-confirmed-attempt') then raise exception 'New order after confirmation rejected'; end if;
  if has_table_privilege('anon','public.hungii_checkouts','select') or has_table_privilege('authenticated','public.hungii_checkouts','select') then raise exception 'Direct access granted'; end if;
  if has_function_privilege('anon','public.begin_hungii_checkout(uuid,uuid,text)','execute') then raise exception 'Anonymous claim granted'; end if;
  update public.hungii_accounts set status='deleting' where id=owner;
  if exists(select 1 from public.hungii_checkouts where user_id=owner) then raise exception 'Recovery survived deletion tombstone'; end if;
  if public.begin_hungii_checkout(owner,gen_random_uuid(),'deleted-owner') then raise exception 'Deleting owner accepted'; end if;
  delete from public.hungii_accounts where id=owner;
  if exists(select 1 from public.hungii_checkouts where user_id=owner) then raise exception 'Recovery survived account deletion'; end if;
end $$;
