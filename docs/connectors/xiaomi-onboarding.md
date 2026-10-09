# Xiaomi source onboarding and local history

Scope: FH-XIA-05/06/10, first UI increment in 0.3.5. Check PROGRESS.md for actual verification.

## Entry point and runtime

Settings -> Xiaomi Home -> sources/xiaomi is a normal Compose navigation route in the same APK. XiaomiModule supplies exactly one runtime/client/session store and one source repository through Hilt. No additional Activity, bridge, backend or root is required.

The runtime checks the actual APK certificate, package and non-debuggable status. Only the approved privately signed release can render active login controls; the public debug/TEST APK remains blocked and has no INTERNET permission. Local archive metadata remains readable without a service session.

## Explicit onboarding

Region is not inferred from GPS, locale or residence. A new connection has no automatic region selection. Reconnecting an existing archive uses its bound region and expected account UID. A successful login with another UID cannot replace the old archive session silently.

The model identifier is chosen explicitly from the two known protocol models or supplied in the validated vendor identifier format. Known protocol format is not hardware certification; S400 Pro/regional variants remain unverified. Unknown model units stay unverified in the existing parser.

Discovery reads one page at a time from the existing model-specific history endpoint. It is NOT a household/device catalogue. Only people/devices observed in those responses are shown. Older pages are an explicit action; counts of examined rows/pages and unresolved identities are visible. No measurement is persisted during discovery. The operation is bounded to 250 pages/512 candidates per in-memory discovery and does not claim completeness at a short page.

Names, when available, are ephemeral data.user.name labels documented in SmartScaleConnect a9e5c04 client.go. Control/bidi/format characters and overlong labels are not displayed. Names never select ownership: the key contains connection, region, login UID, model, subject UID/accountId and device ID. Names are not added to persisted snapshots.

The user must select a candidate and confirm a second time before creating the fixed binding. Even one candidate is not preselected. Arbitrary keys and stale selections after logout are rejected. Confirmation rechecks the currently valid service session. Room remains the final owner/device guard; existing binding is not replaceable through this flow.

## Actions and status

Login, discovery, confirmation, manual sync, continuation, cancellation, local refresh and disconnect are connected to gateway/client/Room operations, not simulated success buttons. Live access is enabled only for the approved private release; tests inject synthetic HTTP.

Manual sync has a 20-page budget per click and uses the existing durable checkpoint for interrupted history. Completed means reaching the source page boundary, not proof that every historical vendor record was exposed. The UI shows last page committed, persisted range and snapshot count. Snapshot count is not advertised as a number of deduplicated weighings; changed versions remain separate.

Only one ViewModel action runs at a time. Leaving the screen cancels foreground operations; completed pages remain. Late results cannot replace cancelled state. Logout cancels client work and deletes only session material, never health records or the immutable binding. Errors use static translated categories, not exception strings, raw URLs, JSON or cookies.

## Local history and privacy

Saved snapshots are queried in pages of 20. Details show source metric path, numeric value, unit, vendor method and quality flags without recalculation. Invalid/unreadable records get an explicit label and are not deleted or silently corrected. This source-specific history does not yet replace the canonical Body/Dashboard/Insights resolver.

The source screen applies FLAG_SECURE while visible and restores the previous window flag on disposal. Credentials use remember (not saveable state), password masking and bounded inputs; fields clear on stop/disposal and submission. ViewModel/gateway state never exposes XiaomiSession, serviceToken or ssecurity. JVM String copies are not guaranteed erasable; these controls are not a hardware security claim.

No fake accounts, demo measurements or permissive access switch are shipped. Compose test fixtures live in src/test only.

## Verification and remaining work

Tests added for discovery identity/name handling, native Room selection/isolation/history paging, ViewModel cancellation/password clearing, and actual Compose semantics/actions under Robolectric. These are not physical-device, live Xiaomi, real TLS, CAPTCHA or hardware Keystore tests.

Remaining: successfully signed private APK and owner installation; real account/region/model verification; CAPTCHA/additional-verification continuation and separately reviewed STS redirects; richer region/model help; shared canonical body repository; periodic Xiaomi WorkManager sync; Health Connect write-back; full accessible/localized phone validation.

References used:
- SmartScaleConnect pinned history and user label contract: https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/pkg/xiaomi/client.go
- Lifecycle effects: https://developer.android.com/topic/libraries/architecture/lifecycle
- Compose tests: https://developer.android.com/develop/ui/compose/testing
- Robolectric/Compose testing: https://robolectric.org/blog/2026/07/03/designing-local-testing-libraries/

Upstream MIT notices remain in their existing assets; no project licensing decision is made by this increment.
