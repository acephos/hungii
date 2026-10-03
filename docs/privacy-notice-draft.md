# Hungii privacy notice — founder review draft

Tracker/connection consent version 2026-10-02.3; this draft was updated 3 October 2026 to describe the Simulator Assistant's separate opt-in. Public publication is pending the founder's confirmed support contact and processor review. This notice describes the current implementation; it is not a legal certification or a statement that Swiggy has approved Hungii.

Hungii helps you choose a meal and keep an entered food/spending tracker. App account login and optional Swiggy authorization are separate. You can use a local tracker without connecting Swiggy. Missing restaurant nutrition is displayed as unknown; orders are not automatically counted as food eaten.

## Data and purposes

WorkOS AuthKit manages Hungii email authentication, account identity and sessions. WorkOS receives the email address and sign-in information needed for authentication; its processor/location terms remain subject to founder review. The app keeps its rotating refresh token encrypted using Android Keystore and its access token in memory. Supabase hosts Hungii's backend and database in Mumbai; Hungii stores a hash of the verified identity to associate its own account UUID with your tracker, without copying your email or raw WorkOS identity into that database. Connecting Swiggy takes you to Swiggy's own browser authorization. Hungii does not collect your Swiggy password or OTP.

With your separate permission, Hungii retains an encrypted Swiggy access token, your selected address identifier and logical Food session metadata for requested meal searches. It shows your available addresses to let you choose delivery location, then uses that address for the search you request. Menu, cart and offer responses are processed for the immediate task and are not stored as a complete catalogue or order history. Provider phone numbers are removed from Hungii's normalized address display.

The device tracker holds your entered targets, daily totals, allowance, spend and meal opportunities. Optional cloud sync also includes your optional name, selected tastes, dietary/budget/delivery filters and entered search preference, after you allow it. Signing in does not itself upload a profile. Local-only mode works without an account; an authenticated user can also decline or stop cloud sync. Consented edits sync automatically; a new sign-in restores the cloud copy, while conflicting device edits require a choice. Arbitrary provider payloads, address details and free-text histories are rejected from cloud tracker storage.

Saving a meal asks separate permission to retain its name, restaurant and menu identifiers on your device as a shortcut for another search. Saved snapshots omit prices, offers and photos. You must search again for current availability/pricing. No saved food history is imported automatically from Swiggy.

## Storage and retention

The selected Supabase primary database is Mumbai. API processing is forced and checked in Mumbai. Additional provider/subprocessor and egress assessment remains a launch gate; region choice alone is not a blanket residency guarantee.

Hungii-controlled tracker, connection, selected-address, verifier and session payloads use AES-256-GCM. Device data uses per-owner Keystore keys; account identifiers in the device database are hashed. From preview 0.7.4, Android backup is disabled and explicit exclusion rules cover cloud backup and device-to-device transfer, including shared preferences, databases and device-protected storage. Optional Hungii cloud sync is a separate explicit choice. Photos loaded for the task have app disk/memory caching disabled.

Connection/session retention is bounded by the token expiry, capped at five days. Pending OAuth states expire in ten minutes. Inactive cloud tracker copies expire after 90 days without an update. Consented local meal shortcuts expire after 30 days. Database cleanup runs every ten minutes and on API access. A paused Supabase Free project cannot execute scheduled cleanup; access cleanup resumes with the service. Local expiry is applied when stored data is loaded by the app. Device data otherwise stays until erased or the app is removed.

## Voice, AI and diagnostics

Tap-to-talk uses an available on-device Android recognizer; if unavailable, type instead. Hungii does not store audio recordings or operate a cloud speech service. The real-service build uses local interpretation and has no connected cloud Assistant.

The separately installed Simulator offers an optional Google ADK Assistant using Groq Free cloud inference. It sends your typed or transcribed message, up to four recent conversation messages, and selected entered tracker/app context to Groq after you allow cloud processing. It can also read the authored synthetic MCP fixtures. This processing takes place outside India. The key stays on the computer; no model is installed on the phone. Conversations are not persisted by the Hungii Assistant server and are discarded after each turn; the app holds its short conversation in memory until restart. Groq's own processing and retention terms require review and are not controlled by Hungii's in-memory storage. Free inference availability and quotas are not guaranteed.

The Simulator does not automatically send real Swiggy account credentials, returned provider payloads, real delivery addresses or payment credentials to inference. Free-text messages can still contain information you type yourself; avoid entering secrets or real provider records. There is no advertising or model-training integration in Hungii. Live provider-data inference remains a separate contractual and launch gate. Assistant changes are proposals; an explicit review/apply action changes the tracker, and ordering remains separate from logging food eaten.

Food diagnostics are minimized to hashed user/session identifiers, tool name, elapsed time and outcome. Provider arguments/results, addresses, phone numbers and tokens are not logged by Hungii. WorkOS and Supabase's separate platform/authentication logging requires processor review before launch.

## Choices and deletion

Use Accounts to stop cloud sync on this device, restore a cloud profile, disconnect Swiggy, erase the cloud profile/tracker, delete your Hungii account, or erase the current device tracker and saved meals. Destructive actions require confirmation. Disconnecting removes Hungii's provider credentials/state and saved provider shortcuts. Remote revocation is attempted separately when allowed; Hungii tells you if success could not be confirmed. Use Swiggy account controls for its own records/access.

Deleting a Hungii account first erases its cloud tracker, connection, OAuth states, session records and consent epoch, while blocking writes that race deletion. Hungii then requests deletion of the WorkOS account. If that fails, the app reports the deletion as pending and permits a retry; erased tracker/provider data is not restored. On success, only a hashed identity fence remains for 24 hours to prevent an already-authorized request from recreating erased data, then cleanup removes it. The app erases the current account's device data/key and authentication credentials when deletion completes. Other devices' private local copies require deletion on those devices; Hungii does not claim it can remotely erase a disconnected phone.

Use Saved to remove shortcuts and withdraw saving permission. Device erasure removes the corresponding data key, so retained ciphertext cannot be decrypted with that key. Cloud-only deletion leaves the local tracker available. Swiggy data access/correction/erasure requests that concern Swiggy's own account must be addressed to Swiggy; Hungii will handle its own permitted copies through the app controls and confirmed support contact.

## Contact and launch status

Founder must supply and approve the public privacy/support contact before publication. Agreements, approved Swiggy staging/production access, actual processor/egress assessment, production WorkOS configuration and operational support/alert routing are still pending. This draft is for review and must not be represented as an approved production privacy policy.
