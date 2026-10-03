# Verification evidence

These are dated implementation checks and prototype screenshots. They do not establish Swiggy approval, live payment capability or production security certification. APKs and device recordings are generated locally or attached to [GitHub Releases](https://github.com/acephos/hungii/releases); they are excluded from Git.

| Record | Scope |
| --- | --- |
| [Mobile design verification](mobile-ui-verification.md) | v0.7.5 transcript-guided navigation, meal selection, sheets and basket checks |
| [Repository hardening](repository-hardening.md) | Current cleanup, regression checks and remaining limits |
| [Service recovery](simulator-service-recovery.md) | Host reboot diagnosis, automatic startup and connection smoke test |
| [Simulator connection fix](simulator-connection-fix.md) | v0.7.2 Android offline/recovery behavior |
| [Simulator verification](simulator-verification.md) | v0.7.1 synthetic MCP checkout and Groq Assistant walkthrough |
| [Account and service verification](swiggy-verification.md) | v0.6.2 WorkOS/cloud-sync evidence, with earlier dated sections |
| [Android design verification](android-verification.md) | Earlier UI exploration |

Screenshots beginning `android-` show the earlier visual prototype. Screenshots beginning `swiggy-` or `hungii-` document the named test flow; review the accompanying record before drawing conclusions about its backend or build. Stock food photos represent synthetic examples.
