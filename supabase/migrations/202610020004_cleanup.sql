create index swiggy_connection_expiry on public.swiggy_connections(expires_at);
create index swiggy_state_expiry on public.swiggy_oauth_states(expires_at);
create index hungii_tracker_expiry on public.hungii_state(updated_at);
create extension if not exists pg_cron with schema pg_catalog;
select cron.schedule('hungii-privacy-purge','*/10 * * * *','select public.purge_hungii_expired()');
