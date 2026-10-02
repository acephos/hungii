-- Existing sessions must be reconnected to record versioned retention permission.
alter table public.swiggy_connections add column encrypted_address_id text;
alter table public.swiggy_connections add column consent_version text;
alter table public.swiggy_connections add column consented_at timestamptz;
alter table public.swiggy_connections drop column address_id;
alter table public.swiggy_oauth_states add column consent_version text;
alter table public.swiggy_oauth_states add column consented_at timestamptz;

-- New tracker writes contain application-encrypted ciphertext. The initial
-- prototype was never deployed; older plaintext rows, if present, must be
-- exported/encrypted by their owner before production rather than silently read.
comment on column public.hungii_state.state is 'Envelope containing AES-256-GCM ciphertext; no raw tracker fields.';
