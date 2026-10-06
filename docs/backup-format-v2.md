# Portable database backup v2

## Scope and format

The outer encrypted container stays `BackupCrypto` (AES-256-GCM with passphrase-derived key). JSON inside has `payloadVersion: 2`, `databaseVersion`, coverage/exclusion metadata and ordered `tables`. Each table has an exact column list and arrays of cell values. All stored primary keys, nullable fields and persisted domain relationships are retained.

The 19 domain tables are registered in `DatabaseBackupService.DOMAIN_TABLES`. Column definitions come from the local compiled database, sorted by name so physical column order after ALTER TABLE does not change the portable format. No SQL from an archive is executed. A new unregistered application table makes export fail rather than silently omit it; SQLite-reserved internal tables and Room metadata are not domain data.

Credentials, shared preferences, media and operational checkpoints are excluded. This is database-data coverage, not a clone of the entire app environment.

## Export

Read every registered table in one Room transaction so concurrent writes do not create inconsistent parent/child snapshots. Encrypt after that snapshot transaction completes. Bound plaintext to 32 MiB, total cells to two million and individual text cells to two MiB. Larger archives fail explicitly; no truncation is presented as successful backup. The in-memory representation still needs real-device memory benchmarking.

## Restore

Authenticate/decrypt before any writes. Validate JSON nesting, format, exact current database version, complete table/column coverage, required/null values and SQLite-compatible numeric types. No float-to-integer coercion or silently ignored future table is allowed.

One transaction inserts absent primary keys, skips identical rows and aborts on conflicting rows without replacing data. Check declared foreign keys plus legacy domain relationships that lack SQL foreign-key declarations. Check Xiaomi singleton binding, snapshot hashes and selected-subject consistency before commit. Discard operational Xiaomi checkpoints after a successful v2 restore; a later sync must not trust stale imported cursor state.

No destructive replace mode, independent-database ID remapping or cross-schema archive migration is implemented. Two independent phones with colliding integer IDs may be rejected safely. Same data and IDs restore idempotently in the tested v2 path. Physical transfer and production-scale input remain to be exercised.

## Legacy compatibility

Version-1 archives use the old partial adapter inside a transaction, only when the health archive is empty. Existing exercise catalogues and local profile settings may remain; other domain tables must be empty. This protects an existing archive from the legacy adapter's incomplete ID/upsert semantics.

V1 did not capture every table/column/relation and cannot recover absent fields. It does not inherit all v2 idempotency or full-coverage guarantees. The UI explicitly labels old restores as partial. Do not suggest uninstalling an app with valuable data solely to satisfy an empty-archive requirement.

## User interaction and secrets

Document operations run on IO workers. File reads have an encoded-size limit before unrestricted allocation. Large base64 content is not rendered in a text field; optional paste is limited to 64 KiB. Passphrases are masked and not stored in the Android instance-state bundle; submitted character arrays are wiped in finally blocks. JVM immutable-string copies cannot be guaranteed erased.

A selected document provider may be cloud-backed. Automatic OS backup exclusions and user-initiated file exports are separate policies. A lost device without a separately saved archive still loses its local data.

## Verified evidence

[CI 37525676439](https://github.com/HebaDenys/fitness-hub/actions/runs/37525676439), software SHA `55ae7c0`: native SQLite under Robolectric with generated Room code. Tests compare all registered database columns/relationships, duplicate restore, conflicts, wrong passphrase/tampering, unsupported schema, orphaned relations, forged Xiaomi identity and unsafe legacy merge. API 28 plus one API 35 backup round-trip. Not physical-device testing; see [PROGRESS.md](PROGRESS.md) for full scope and remaining gates.
