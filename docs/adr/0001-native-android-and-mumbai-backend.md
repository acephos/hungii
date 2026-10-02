# Native Android and a Mumbai backend

Authentication choice superseded by [WorkOS with Supabase](0003-workos-with-supabase.md); native Android and Mumbai hosting remain selected.

Hungii uses Kotlin and Jetpack Compose, Room for device-local food tracking, and Supabase Postgres/Auth with a TypeScript MCP adapter running explicitly in Mumbai. We chose this over Convex/WorkOS because Supabase provides an India region for Swiggy-response processing and one account service alongside the database; Swiggy account authorization remains a separate OAuth connection. AI interpretation and speech are replaceable adapters, while money, nutrition accounting and ranking remain deterministic.
