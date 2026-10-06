# Xiaomi authenticated transport and protected session

Task scope: FH-XIA-02/03/04 + SAFE. Initial implementation: 0.3.4.

## Reference and licensing

Protocol adapted from AlexxIT/SmartScaleConnect, commit `a9e5c04f1079b65d456c8a5fd296775a1ef29e8f`, `pkg/xiaomi/auth.go` and `client.go`. The upstream revision was rechecked before implementation. Its MIT notice is already included in `app/src/main/assets/licenses/SmartScaleConnect-MIT.txt`; this does not change Fitness Hub's pending project license.

- https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/pkg/xiaomi/auth.go
- https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/pkg/xiaomi/client.go
- Callback/challenge evidence: https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor/issues/129 (read as protocol evidence, not copied code or fixture).
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

## Implemented normal path

`XiaomiAuthentication` performs the xiaomiio three-step exchange: serviceLogin -> serviceLoginAuth2 -> validated service ticket. The password is hashed in memory for the vendor form and is not persisted. The broader account `passToken` returned by Xiaomi is deliberately not stored or reused.

`XiaomiAuthenticatedPageSource` signs and encrypts the bounded read-only scale request and decrypts the response envelope before the existing strict parser. CN/global history routes remain distinct. Region, connection ID, login UID, model and request descriptor must match; this is not an arbitrary signed Xiaomi API client.

`XiaomiCloudClient.readHistory` connects the authenticated page source to `RoomXiaomiArchive.read`. Binding remains independently enforced in Room. Existing selected-subject/device isolation, snapshot deduplication, checkpoint transactions and resume behavior are not replaced.

## Wire cryptography is not storage cryptography

The reference protocol uses MD5 for the submitted password hash, SHA-1 for the request signature, SHA-256 for the signed nonce and RC4 with 1024 discarded bytes for payload encoding. These legacy algorithms are used only for compatibility under verified HTTPS. They do not provide modern standalone authentication/encryption and are never used to protect the local session file.

A golden request vector was generated independently using Go standard-library crypto/rc4, crypto/sha1 and crypto/sha256 with synthetic bytes, and recorded in the Kotlin test. The wire algorithm is not validated by an encrypt/decrypt round-trip alone.

## Network restrictions

Exact HTTPS origins and paths only: two account endpoints, the STS callback, CN scale history or one of the five regional history endpoints. No wildcard vendor domains, userinfo, custom ports, fragments or encoded paths. No automatic redirects. A new legitimate vendor redirect currently fails closed and needs a separately reviewed allowlist update.

The HttpsURLConnection adapter retains default certificate and hostname checks, disables caching and automatic redirects, bounds response bytes and headers, limits concurrent IO to two slots, and sets connection/read/whole-operation timeouts. Cancellation disconnects the in-flight call. No trust-all SSL, body logger or global cookie jar is installed. Error bodies are not retained.

Cookies are obtained only from the validated STS response; only serviceToken/userId/cUserId can enter the session. Account cookies/hash/passToken are not forwarded to STS. Duplicate identity cookies, conflicting userId or untrusted cookie domains are rejected. API cookies are used only by the scope-bound scale source.

HTTP 401/403, 429 and 5xx have static reason codes. Unestablished vendor JSON codes are not guessed and private vendor descriptions never become UI/log text. Safe access exceptions survive the paginated reader rather than becoming a misleading empty import.

## Challenges and limitations

CAPTCHA and additional-verification responses are detected and stop the flow. Completing these challenges is NOT implemented. There is no bypass, automatic repeated password submission, invented OAuth client or QR flow. STS redirects are deliberately rejected in this increment, even if a future vendor rollout needs them.

No claim is made that the flow works with the user's account, region, S400 Pro firmware or current Xiaomi anti-bot rules. There has been no real Xiaomi login/request in development or CI.

## Session lifecycle

`XiaomiSession` stores connection, region, login UID, service secret/cookies and timestamps. Local reuse is limited to the earlier of the service cookie's positive Max-Age and 24 hours; 24 hours is an APP policy, not a claimed server expiry. Missing cookie expiry does not mean unlimited reuse. Clock rollback beyond a small tolerance requires login again. No passToken refresh is implemented.

`XiaomiProtectedSessionStore` uses an AtomicFile in Android `noBackupFilesDir` and an independent AndroidKeyStore AES-256-GCM key. An authenticated version prefix/AAD and fresh IV protect the serialized session. Read never creates a missing key. Corruption/missing key invalidates only the session and requires fresh login. File/keystore IO is outside the UI thread and serialized.

The session is outside Room and outside the portable database backup. Logout cancels running and queued client operations, prevents late authentication from saving a session, removes session ciphertext/key and leaves all measured data and bindings intact. No remote health record is modified. A different account/region/connection cannot silently replace an existing session.

A single runtime instance must be reused by the app. No periodic Xiaomi WorkManager job is registered yet. Password CharArrays and temporary byte buffers are cleared where practical, but JVM Strings, HTTP internals and garbage collection do not permit a promise of complete memory erasure.

## Release gate

`XiaomiCloudRuntime.create` uses `AwaitingPrivateSigning`. It cannot make a real login in the publicly signed test APK. There is no permissive build flag or UI switch. Tests explicitly inject synthetic transports and a test gate.

FH-SAFE-03 remains: owner-approved private signing/custody/migration, then onboarding UI and real-device tests. The existing test package/key is unchanged. Do not ask for account passwords in chat, commits, CI secrets or fixtures.

## Verification levels

Tests cover independent cryptographic vector, form/URL validation, three-step synthetic login, challenge/error handling, bounded transport adapter with fake HTTPS connections, encrypted store/reopen/tamper/key-loss, cancellation/late responses, and authenticated synthetic HTTP -> real generated Room/native SQLite -> replay/logout.

Fake HTTPS connections do not test real TLS handshakes. Injected AES keys do not test Redmi hardware Keystore or OEM backup behavior. The session/transport is a runnable prerequisite, not completed phone onboarding or a production-ready integration. Check PROGRESS.md for actual CI outcomes.
