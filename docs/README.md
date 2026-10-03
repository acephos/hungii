# Hungii documentation

Start with [current status and launch gates](approval-readiness.md). The repository contains an Android preview, a separate synthetic Simulator and an earlier web exploration. Only Food is implemented; Instamart, Dineout and Scenes remain future scope.

## Setup and maintenance

| Document | Purpose |
| --- | --- |
| [Android](../android-prototype/README.md) | Build variants, device behavior and assets |
| [Simulator](../demo/README.md) | Local MCP, Tailscale, opt-in cloud inference and restartable services |
| [Real service setup](swiggy-setup.md) | WorkOS/Supabase configuration and Swiggy access gates |
| [Contributing](../CONTRIBUTING.md) | Reproducible checks, secrets and PR workflow |
| [Operating runbook](incident-runbook.md) | Simulator connection recovery and service incidents |
| [Preview releases](releases.md) | Signing, tags, downloadable builds and published assets |
| [Security reporting](../SECURITY.md) | Private vulnerability reporting and supported scope |

## Product and contracts

- [Product specification](meal-planner-product.md): desired behavior; current status identifies what is implemented.
- [Glossary](../GLOSSARY.md): shared terms, including finalist, winner, food allowance and consumption.
- [Swiggy contract](swiggy-integration-contract.md): dated source verification; re-fetch primary pages before changing tools/auth/rates.
- [Privacy notice draft](privacy-notice-draft.md): current data paths; publication still requires founder contact and processor review.
- Architecture decisions: [native Android and Mumbai backend](adr/0001-native-android-and-mumbai-backend.md), [durable MCP session](adr/0002-durable-mumbai-mcp-session.md), [WorkOS identity](adr/0003-workos-with-supabase.md).

## Research and historical evidence

Research is dated, informs decisions and does not certify current vendor behavior. Reverify its primary sources before implementation or spending decisions.

- API research: [initial Swiggy inventory](research/swiggy-research.md), [meal feasibility](research/meal-planner-api-feasibility.md), [testing options](research/swiggy-testing-options.md), [synthetic MCP contract](research/mock-mcp-contract-research.md), [protocol/security sources](research/approval-technical-sources.md).
- Design videos: [mobile UI fundamentals](research/mobile-ui-video-research.md), [UX psychology](research/ux-psychology-design-research.md), [product-page redesign](research/product-page-design-research.md). Timestamped guidance is mapped to Hungii choices; empirical video claims are not independently verified.
- Design and stack research: [FamApp direction](research/famapp-design-research.md), [Assistant orb](research/ai-orb-design-research.md), [free cloud inference](research/free-agent-inference-research.md), [free stack](research/stack-free-tier-research.md).
- Historical snapshots: [initial compliance audit](archive/swiggy-application-compliance.md), [early access proposal](archive/swiggy-access-application.md), [sent staging request](archive/swiggy-staging-email.md), [saved unsent review draft](archive/swiggy-review-email-draft.md), [initial push/release review](archive/push-review.md).
- [Verification records and screenshots](../artifacts/README.md). Each record applies to its named checkpoint; screenshots are not proof of live provider access.

Current setup instructions stay here or in component READMEs. Preserve original sent correspondence and historical results; add a new dated record when behavior changes. Never turn an old proposed capability into a present-tense claim.
