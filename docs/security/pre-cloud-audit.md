# FH-SAFE-01/02 — Initial pre-cloud review, 6 October 2026

This is a bounded engineering inventory, not a completed security certification. Baseline inspected: 81b4f85 (application code 0.3.1). No real credentials were collected or used. Local-first is not a claim that optional vendor/catalog/AI operations never use the network.

| Area / inspected file | Finding | Action / residual gate |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | OS backup policy was implicit. App entry/rationale are exported; HC usage alias is permission-protected. | Explicit allowBackup=false, legacy backup exclusions and modern cloud/device-transfer exclusions for all documented domains. Cleartext explicitly disabled. No change of exported components or app ID. |
| `core/sync/AppLogger.kt` | Comment claimed safe logs, but Map<String, Any> and arbitrary event/ID were interpolated without enforcement. | Route through OperationalLogLine: fixed event/field/type/value allowlists, unknown objects never stringified, sanitized event and correlation ID. Review direct Log calls and UI error strings separately. Counts still require trustworthy callers; this is not a malicious-caller information-flow proof. |
| `core/ai/AiKeyStore.kt` | AES-GCM ciphertext and IV in private shared preferences, key in Android Keystore. | OS export excluded by new rules. Key invalidation/re-auth, UI masking, lifecycle and hardware behavior still need device tests. Do not claim StrongBox/hardware protection merely because Keystore is used. |
| `core/scale/BindkeyStore.kt` | Same AES-GCM/Keystore design. Direct logging uses exception class only, not message. | Preferences excluded from OS backup. BLE keys remain optional and separate from future Xiaomi sessions. |
| `core/ai/AiTransport.kt` | Timeouts exist, but unbounded response read, implicit redirect behavior, generic exception catch and success DTO contain raw body. | FH-SAFE-04/05 and FH-AI work remain open. Do not reuse this transport as Xiaomi auth/HTTP implementation. |
| `core/backup/BackupService.kt` (baseline review) | Portable encryption does not imply complete table/relationship coverage. | No table changes in this increment. Lossless backup/restore remains a PORT gate; do not advise uninstall/reinstall to resolve signing errors. |
| `app/build.gradle.kts`, `ci/fitnesshub-test.jks.b64` | Public test-only key currently signs rolling APKs. | Kept unchanged. It is not publisher authentication; private signing/migration needs explicit owner decision before routine sensitive login. |
| New `core/xiaomi` boundary | Only decrypted synthetic response parsing and injected contracts, no live transport or session. | Redacted DTO toString, no payload in exception causes, bounded JSON, no arbitrary string extensions. Authentication/binding/Room integration remain unimplemented. |

## OS backup policy

The explicit policy is no automatic OS cloud backup and no automatic OS device transfer of this app's private data/keys. On Android <=30 use full-backup-content plus allowBackup=false; Android >=31 uses data-extraction-rules with separate cloud-backup and device-transfer exclusions. Credential- and device-protected domains are both covered. This does not delete any existing backup on the user's account, nor does it affect files explicitly exported by the user through the document picker. A provider chosen in that picker can itself be cloud-backed.

Source XML regression checks run in CI. Actual backup/restore behavior, merged-manifest inspection and OEM transfer testing (including the user's Redmi) remain required. Automatic recovery is now explicitly opted out: until complete portable backup is verified, retain original vendor data and do not uninstall builds holding irreplaceable local entries.

## Before real Xiaomi login

1. Confirm private signing strategy and controlled upgrade/data migration; no private signing key in Git.
2. Implement a separate Keystore session store, logout cancellation and invalidation paths.
3. Implement allowlisted HTTPS auth/read hosts, restricted redirects, bounded responses, cancellation and sanitized errors. Verify actual challenge flow; never invent an OAuth/QR route or bypass vendor challenges.
4. Bind source subject to the intended local person; enforce before committing observations.
5. Align runtime privacy/backup wording and secret forms with behavior. The current UI still needs this review.
6. Test on a real supported Android device without exporting credentials or personal measurements to CI/issues.

## Primary references

- Android Auto Backup and XML domains: https://developer.android.com/identity/data/autobackup (checked 6 October 2026).
- Android log information disclosure: https://developer.android.com/privacy-and-security/risks/log-info-disclosure (checked 6 October 2026).
- Xiaomi protocol reference: [pinned source and license](../connectors/xiaomi-protocol.md).
