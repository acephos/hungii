# Security reporting

Hungii is a development preview. Live Swiggy order/payment writes are disabled. The unauthenticated Simulator is intended for loopback or a private Tailscale network; its synthetic session IDs isolate fixtures and are not production authentication.

Report vulnerabilities through the repository's [private vulnerability reporting page](https://github.com/acephos/hungii/security/advisories/new). If unavailable, contact the repository owner through a verified private channel. Do not put keys, access tokens, personal addresses, request bodies or private signing material in public issues.

Include the affected commit/build, steps to reproduce with synthetic data, impact and minimal redacted evidence. No response-time SLA or independent security certification is claimed. Only the latest source/preview is maintained; older APKs remain historical checkpoints. Production identity setup, provider agreements, processor review, alert routing and recovery validation remain [launch gates](docs/approval-readiness.md).
