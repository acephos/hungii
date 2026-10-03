# Working on Hungii

Use JDK 17, Android SDK 35 and the checked-in Gradle wrapper; use Deno 2.9.6 for the backend and Python 3.11+ for repository scripts and the Assistant. See [Android setup](android-prototype/README.md) and [Simulator setup](demo/README.md). The repo has no production license grant yet; do not assume bundled third-party asset licenses cover Hungii source.

## Check a change

From the repository root:

```sh
python3 scripts/check-repo.py
deno check --config supabase/functions/deno.json supabase/functions/hungii-api/index.ts demo/gateway.ts
deno test --config supabase/functions/deno.json --allow-env supabase/functions/_shared/ demo/simulator_test.ts
```

For the cloud Assistant, install its pinned dependencies once in an ignored virtual environment, then run the no-network regressions:

```sh
python3 -m venv .venv-agent
.venv-agent/bin/pip install -r demo/requirements-agent.txt
.venv-agent/bin/python -m unittest demo.agent_test -v
```

Android checks, from `android-prototype/`:

```sh
./gradlew --no-daemon :app:testRealDebugUnitTest :app:testDemoDebugUnitTest :app:assembleRealDebug :app:assembleDemoDebug :app:lintRealDebug :app:lintDemoDebug
```

These tests use authored fixtures and need no Swiggy access, Groq key or live database. SQL tests under `supabase/tests/` require an explicitly configured disposable database or a reviewed transaction against the intended environment; CI does not apply migrations to the deployed project. Never claim a local unit test proves deployed service behavior.

## Change boundaries

Consult the [glossary](GLOSSARY.md), [architecture decisions](docs/README.md) and [current status](docs/approval-readiness.md). Before editing Swiggy code, fetch [the authoritative index](https://mcp.swiggy.com/builders/llms.txt), then relevant `.md` tool/auth/error/rate pages. Preserve runtime schema validation and live-write gates. Treat synthetic cart members and nutrition as Hungii assumptions, not provider facts.

Keep secrets in ignored local files or the intended backend secret store. Android and build CI receive public identifiers only. Do not commit APKs, signing keys, request recordings, user data or credential files. `scripts/check-repo.py` checks common private-key formats, generated/private paths and local Markdown links; it is a guard, not a complete secret scanner or security audit.

Use a branch and PR for changes to `main`. Describe behavior, useful before/after evidence and remaining limits. Run required checks before merging. Published release tags/assets must remain unchanged; ship fixes under a new version. Preserve dated evidence and sent email snapshots, and update the current docs/index when implementation changes. Provider messages, applications and legal acceptance require their own explicit instruction.
