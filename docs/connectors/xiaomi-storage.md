# Xiaomi archive: identity, snapshots and durable page commits

Implemented scope: FH-DATA-01/02 cloud persistence increment, building on FH-XIA-01. It is NOT the live login or complete cross-source canonical resolver.

## Identity

`source_identity` stores a random local profile identifier. `xiaomi_bindings` explicitly binds one connection/region/model/login UID/subject UID/accountId/device to that archive. Display names and measured weights are never identity keys. The first binding is immutable in this increment: replacing account, subject, device or connection ID is rejected rather than silently mixing archives. A deliberate migration/rebinding UI is future work.

The new binding does not automatically assign ownership to pre-existing CSV, BLE, nutrition or HC tables. Those legacy sources require explicit migration/reconciliation later. Creating a local identity is not claiming the provenance of historical legacy rows.

## Persistence and provenance

`xiaomi_snapshots` stores a canonical JSON envelope with selected identity metadata, original timestamps, separate transport/method/unit/quality for each metric, original numeric representations and sanitized numeric vendor extensions. `receivedAtMillis` is local acquisition time, not measurement time. Missing/invalid values and vendor estimates retain their annotations.

A content hash makes an identical protocol snapshot idempotent. A candidate `eventKey` groups similar envelope identities but is NOT a proven Xiaomi ID. Changed contents remain available separately; the app does not invent revision ordering from `dataVersion`, or assume `sn` is globally unique. Exactly identical vendor rows without a reliable event ID are indistinguishable; this limitation remains visible in future canonicalization work.

## Transaction boundaries

`RoomXiaomiArchive.read` uses the existing bounded reader with a concrete Room committer. Fetch/parse happens outside a database transaction. The committer checks the binding and cursor generation, stores only selected records, and advances the checkpoint in one transaction. A storage failure or cancellation rolls back both. Restart resumes the saved next cursor. A completed pass starts a fresh generation for the next backfill; an older worker cannot commit into it.

Same-page acknowledgement requires both request cursor and page digest to match. A changed page with the same old cursor is rejected. Known other subjects/devices are counted and excluded; ambiguous subject/device identity fails the entire page instead of guessing or skipping silently. This is conservative and may require future quarantine/explicit-resolution UX.

The upstream short-page termination remains a protocol heuristic, not proof of complete vendor history. There is no live network transport in this increment.

## Recovery

Schema 5→6 adds four tables without rewriting existing data. Portable backup v2 includes identity, binding and snapshots, not operational cursors or login tokens. Restore validates the compiled schema, IDs, snapshot hashes and selected-person consistency inside a rollback-capable transaction. A new sync starts without trusting old cursor state.

The v2 backup covers registered database tables, not preferences/media/credentials. Its format and limits are documented in [backup-format-v2.md](../backup-format-v2.md). Generated Room schema must be adopted from actual KSP output, not hand-invented identity hashes.
