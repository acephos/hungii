# Working on Hungii

Use JDK 17, Android SDK 37 and the checked-in Gradle wrapper; use Deno 2.9.6 for the backend and Python 3.11+ for repository scripts and the Assistant. See [Android setup](android-prototype/README.md) and [Simulator setup](demo/README.md). The repo has no production license grant yet; do not assume bundled third-party asset licenses cover Hungii source.

## Check a change

From the repository root:

```sh
python3 scripts/check-repo.py
deno fmt --check supabase/functions demo scripts
deno lint --config supabase/functions/deno.json supabase/functions demo scripts
deno check --config supabase/functions/deno.json supabase/functions/hungii-api/index.ts demo/gateway.ts scripts/operator.ts
deno test --config supabase/functions/deno.json --allow-env supabase/functions/ demo/simulator_test.ts scripts/
```

`deno fmt` (without `--check`) applies the formatting. Handler tests inject a fake database, account service and Food session, so they need no network.

For the cloud Assistant, install its pinned dependencies once in an ignored virtual environment, then run the no-network regressions:

```sh
python3 -m venv .venv-agent
.venv-agent/bin/pip install -r demo/requirements-agent.txt
.venv-agent/bin/python -m unittest demo.agent_test -v
```

Android checks, from `android-prototype/`:

```sh
./gradlew --no-daemon spotlessCheck :app:testRealDebugUnitTest :app:testDemoDebugUnitTest :app:assembleRealDebug :app:assembleDemoDebug :app:assembleRealRelease :app:lintRealDebug :app:lintDemoDebug :app:lintRealRelease
```

Run `./gradlew spotlessApply` to format Kotlin. These tests use authored fixtures and need no Swiggy access, Groq key or live database.

SQL tests under `supabase/tests/` each roll back their synthetic rows. CI applies the migrations to a disposable local Supabase database and runs them; locally, with Docker and the [Supabase CLI](https://supabase.com/docs/guides/local-development):

```sh
supabase db start
for test in supabase/tests/*.sql; do psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -v ON_ERROR_STOP=1 -q -f "$test"; done
supabase stop --no-backup
```

CI never applies migrations to the deployed project. Never claim a local unit test proves deployed service behavior.

## Change boundaries

Consult the [glossary](GLOSSARY.md), [architecture decisions](docs/README.md) and [current status](docs/approval-readiness.md). Before editing Swiggy code, fetch [the authoritative index](https://mcp.swiggy.com/builders/llms.txt), then relevant `.md` tool/auth/error/rate pages. Preserve runtime schema validation and live-write gates. Treat synthetic cart members and nutrition as Hungii assumptions, not provider facts.

Keep secrets in ignored local files or the intended backend secret store. Android and build CI receive public identifiers only. Do not commit APKs, signing keys, request recordings, user data or credential files. `scripts/check-repo.py` checks common private-key formats, generated/private paths, local Markdown links and a size budget for committed screenshots; it is a guard, not a complete secret scanner or security audit. Screenshots stay in git history permanently, so keep new ones small (crop, downscale or compress them) and attach recordings or large evidence to the PR or release instead.

Use a branch and PR for changes to `main`. Describe behavior, useful before/after evidence and remaining limits. Run required checks before merging. Published release tags/assets must remain unchanged; ship fixes under a new version. Preserve dated evidence and sent email snapshots, and update the current docs/index when implementation changes. Provider messages, applications and legal acceptance require their own explicit instruction.

`main` requires an up-to-date PR with successful `check` and `assistant` jobs from GitHub Actions and resolved review conversations, including for administrators. Force pushes and branch deletion are disabled. No mandatory peer approval is configured for this single-maintainer preview. Merged branches are deleted automatically. Private vulnerability reporting and Dependabot alerts are enabled, and `.github/dependabot.yml` opens weekly update PRs for Gradle, GitHub Actions and the Assistant's pip dependencies; Deno imports are updated by hand. Report vulnerabilities through [Security](SECURITY.md).
