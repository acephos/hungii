-- Run only on Hungii's project. All synthetic users and rows are rolled back.
begin;
do $$
declare
  a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); nonce uuid:=gen_random_uuid(); rival uuid:=gen_random_uuid();
  epoch bigint; s public.swiggy_mcp_sessions; i integer; result integer;
begin
  insert into public.hungii_accounts(id,identity_hash) values(a,repeat('a',43)),(b,repeat('b',43));
  epoch:=public.begin_swiggy_consent(a,'synthetic-state','encrypted-verifier','2026-10-02.1');
  assert (select count(*) from public.consume_swiggy_oauth_state('synthetic-state'))=1, 'OAuth state missing';
  assert (select count(*) from public.consume_swiggy_oauth_state('synthetic-state'))=0, 'OAuth replay accepted';
  assert public.store_swiggy_connection(a,epoch,'encrypted-token',now()+interval '1 day','2026-10-02.1',now()), 'Connection not stored';
  select * into s from public.claim_swiggy_session(a,epoch,nonce);
  assert s.lease_owner=nonce and s.phase='initializing', 'Initial lease missing';
  assert (select count(*) from public.claim_swiggy_session(a,epoch,rival))=0, 'Concurrent owner admitted';
  assert (select count(*) from public.claim_swiggy_session(b,epoch,rival))=0, 'Other account borrowed connection';
  assert public.reserve_swiggy_request(a,epoch,rival)=-1, 'Wrong nonce admitted';
  for i in 1..12 loop
    result:=public.reserve_swiggy_request(a,epoch,nonce);assert result=0, 'Budget too low';
  end loop;
  assert public.reserve_swiggy_request(a,epoch,nonce)=10, 'Burst not bounded';
  assert public.finish_swiggy_session(a,epoch,nonce,'encrypted-metadata',null,31), 'Lease completion failed';
  select * into s from public.claim_swiggy_session(a,epoch,rival);
  assert s.phase='ready' and s.encrypted_metadata='encrypted-metadata', 'Worker lost initialized session';
  assert public.reserve_swiggy_request(a,epoch,rival)>=30, 'Cooldown forgotten across workers';
  assert not public.finish_swiggy_session(a,epoch,nonce,'stale',null,0), 'Stale completion accepted';
  perform public.withdraw_swiggy_consent(a);
  assert not public.store_swiggy_connection(a,epoch,'late-token',now()+interval '1 day','2026-10-02.1',now()), 'Callback revived withdrawn consent';
  assert not public.finish_swiggy_session(a,epoch,rival,'late-metadata',null,0), 'Deleted lease revived';
  assert not exists(select from public.swiggy_connections where user_id=a), 'Connection survived withdrawal';
  assert not exists(select from public.swiggy_mcp_sessions where user_id=a), 'Session survived withdrawal';
  epoch:=public.begin_swiggy_consent(a,'synthetic-state-two','encrypted-verifier','2026-10-02.1');
  perform public.store_swiggy_connection(a,epoch,'encrypted-token',now()+interval '1 day','2026-10-02.1',now());
  perform public.claim_swiggy_session(a,epoch,nonce);
  update public.swiggy_mcp_sessions set lease_until=now()-interval '1 second' where user_id=a;
  select * into s from public.claim_swiggy_session(a,epoch,rival);
  assert s.phase='blocked' and s.blocked_code='HUNGII_INITIALIZATION_UNCERTAIN', 'Crash caused another initialization';
  insert into public.hungii_state(user_id,state,updated_at) values(a,'{"ciphertext":"synthetic"}',now()-interval '91 days');
  perform public.purge_hungii_expired();
  assert not exists(select from public.hungii_state where user_id=a), 'Tracker expiry failed';
  delete from public.hungii_accounts where id=a;
  assert not exists(select from public.swiggy_oauth_states where user_id=a), 'Account deletion left OAuth data';
  assert not exists(select from public.swiggy_connections where user_id=a), 'Account deletion left connection';
  assert not exists(select from public.swiggy_mcp_sessions where user_id=a), 'Account deletion left session';
  assert not exists(select from public.swiggy_consent_epochs where user_id=a), 'Account deletion left consent';
end $$;
rollback;
select 'OAuth replay, isolation, lease fencing, quotas, cooldown, withdrawal, crash recovery, expiry and cascades passed' as verification;
