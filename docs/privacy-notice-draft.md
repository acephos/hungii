# Hungii privacy notice — founder review draft

Version 2026-10-02.1. Public publication is pending the founder's confirmed support contact and processor review. This notice describes the current implementation; it is not a legal certification or a statement that Swiggy has approved Hungii.

Hungii helps you choose a meal and keep an entered food/spending tracker. App account login and optional Swiggy authorization are separate. You can use a local tracker without connecting Swiggy. Missing restaurant nutrition is displayed as unknown; orders are not automatically counted as food eaten.

## Data and purposes

Hungii account authentication uses Supabase. Supabase Auth manages your account identity and sessions; the app stores its session credentials encrypted using Android Keystore. Connecting Swiggy takes you to Swiggy's own browser authorization. Hungii does not collect your Swiggy password or OTP.

With your separate permission, Hungii retains an encrypted Swiggy access token, your selected address identifier and logical Food session metadata for requested meal searches. It shows your available addresses to let you choose delivery location, then uses that address for the search you request. Menu, cart and offer responses are processed for the immediate task and are not stored as a complete catalogue or order history. Provider phone numbers are removed from Hungii's normalized address display.

The device tracker holds your entered targets, daily totals, allowance, spend and meal opportunities. Optional cloud sync sends only these structured fields after you allow it. Arbitrary provider payloads, address details and free-text histories are rejected from cloud tracker storage.

Saving a meal asks separate permission to retain its name, restaurant and menu identifiers on your device as a shortcut for another search. Saved snapshots omit prices, offers and photos. You must search again for current availability/pricing. No saved food history is imported automatically from Swiggy.

## Storage and retention

The selected Supabase primary database is Mumbai. API processing is forced and checked in Mumbai. Additional provider/subprocessor and egress assessment remains a launch gate; region choice alone is not a blanket residency guarantee.

Hungii-controlled tracker, connection, selected-address, verifier and session payloads use AES-256-GCM. Device data uses per-owner Keystore keys; account identifiers in the device database are hashed. Android backup is disabled. Photos loaded for the task have app disk/memory caching disabled.

Connection/session retention is bounded by the token expiry, capped at five days. Pending OAuth states expire in ten minutes. Inactive cloud tracker copies expire after 90 days without an update. Consented local meal shortcuts expire after 30 days. Database cleanup runs every ten minutes and on API access. A paused Supabase Free project cannot execute scheduled cleanup; access cleanup resumes with the service. Local expiry is applied when stored data is loaded by the app. Device data otherwise stays until erased or the app is removed.

## Voice, AI and diagnostics

Tap-to-talk uses an available on-device Android recognizer; if unavailable, type instead. Hungii has no cloud speech or LLM service, advertising integration or training pipeline. It does not store audio recordings. The orb currently reacts to recognition levels and a local phrase parser.

Food diagnostics are minimized to hashed user/session identifiers, tool name, elapsed time and outcome. Provider arguments/results, addresses, phone numbers and tokens are not logged by Hungii. Supabase's separate platform/Auth logging requires processor review before launch.

## Choices and deletion

Use Accounts to disconnect Swiggy, erase the cloud tracker, delete your Hungii account, or erase the current device tracker and saved meals. Destructive actions require confirmation. Disconnecting removes Hungii's provider credentials/state and saved provider shortcuts. Remote revocation is attempted separately when allowed; Hungii tells you if success could not be confirmed. Use Swiggy account controls for its own records/access.

Deleting a Hungii account hard-deletes the authenticated account and cascades its cloud tracker, connection, OAuth states, session records and consent epoch. The app erases the current account's device data/key and authentication credentials. Other devices' private local copies require deletion on those devices; Hungii does not claim it can remotely erase a disconnected phone.

Use Saved to remove shortcuts and withdraw saving permission. Device erasure removes the corresponding data key, so retained ciphertext cannot be decrypted with that key. Cloud-only deletion leaves the local tracker available. Swiggy data access/correction/erasure requests that concern Swiggy's own account must be addressed to Swiggy; Hungii will handle its own permitted copies through the app controls and confirmed support contact.

## Contact and launch status

Founder must supply and approve the public privacy/support contact before publication. Agreements, approved Swiggy staging/production access, actual processor/egress assessment, Google user login and operational support/alert routing are still pending. This draft is for review and must not be represented as an approved production privacy policy.
