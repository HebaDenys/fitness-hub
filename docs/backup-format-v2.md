# Portable database backup v2

## Scope and format

The outer encrypted container stays `BackupCrypto` (AES-256-GCM with passphrase-derived key). The JSON inside has `payloadVersion: 2`, `databaseVersion`, a declared coverage/exclusion list and ordered `tables` entries. Each entry has a trusted table name, an exact column list and arrays of cell values. Every primary key, nullable field and persisted relation is retained.

The 19 domain tables are registered in `DatabaseBackupService.DOMAIN_TABLES`. Schema metadata is read from the local compiled database; no SQL supplied by an archive is executed. A new unregistered persistent table makes export fail rather than silently omit a feature. Operational checkpoints, Room/SQLite metadata, preferences, credential stores and media are excluded. This is database-data coverage, not a clone of the app environment.

## Export

Read all registered tables in one Room transaction so concurrent writes do not create inconsistent parent/child snapshots. Encrypt only after the snapshot transaction completes. Bound total plaintext to 32 MiB, total cells to two million and individual text cells to two MiB; a larger archive fails explicitly. No truncation is presented as successful backup. The current in-memory representation still needs real-device memory benchmarking.

## Restore

Authenticate/decrypt before database writes. Validate nesting, format, exact current database version, complete table/column coverage, nullable/required fields and SQLite-compatible numeric types. No float-to-integer coercion or silently ignored future table is allowed.

Within one transaction: identical primary-key rows are skipped, absent rows are inserted with their original IDs, and any conflicting row aborts the entire restore. Unique constraints and foreign-key checks remain enabled. Validate Xiaomi binding/snapshot consistency before commit, then discard operational cloud checkpoints so a new sync cannot skip history based on imported stale state. Restore does not contact the vendor or export to HC.

There is no destructive replace mode or automatic remapping of independent databases in this version. Independent phones with colliding integer IDs may be rejected safely. Cross-database-version v2 restore is also rejected until explicit archive migrations exist.

## Compatibility

Version-1 archives are still decoded through the legacy adapter inside a transaction. They remain partial: old exports did not capture every column/table or relation and are not guaranteed idempotent. UI marks this explicitly. No new code can recover fields absent from the original archive.

## User interaction and secrets

Document operations run on IO workers. File reads are bounded before creating an unbounded string. Large base64 content is not rendered in a text field; optional paste is limited to 64 KiB. Passphrase fields are masked and not saved in the Android instance-state bundle; submitted character arrays are wiped in finally blocks, while JVM immutable-string copies cannot be guaranteed erased.

A selected document provider may be cloud-backed. Automatic OS backup exclusions and user-initiated document export are separate policies. Physical upgrade/transfer, screen privacy, preferences and attachment portability remain validation/future work.
