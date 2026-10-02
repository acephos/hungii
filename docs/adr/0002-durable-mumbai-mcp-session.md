# ADR 0002: Durable Food sessions in free Mumbai Edge Functions

Accepted for staging evaluation, 2 October 2026. Use Supabase Free in Mumbai and preserve encrypted MCP session IDs, protocol versions and tool schemas in backend-only Postgres; serialize HTTP requests with durable leases, credential generations, cooldowns and request budgets so a new worker resumes a logical session without initializing per tool. Live Food access remains disabled until Swiggy confirms and staging demonstrates cross-worker session reuse and request accounting; no paid container or infrastructure is authorized.
