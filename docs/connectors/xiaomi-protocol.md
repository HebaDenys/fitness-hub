# FH-XIA-01 — Xiaomi scale protocol boundary

Implemented prerequisite, not a working cloud login. All samples/tests are synthetic. No Xiaomi account was contacted and no subject binding or Room table was added by this increment.

## Pinned interoperability reference

SmartScaleConnect commit `a9e5c04f1079b65d456c8a5fd296775a1ef29e8f` (0.4.2), inspected 6 October 2026:

- [client.go](https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/pkg/xiaomi/client.go), especially `GetModelWeights` and `unmarshalScaleData`.
- [auth.go](https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/pkg/xiaomi/auth.go): serviceLogin -> serviceLoginAuth2 -> serviceLogin3, request encryption/signature. These auth operations are NOT implemented here.
- [MIT license](https://github.com/AlexxIT/SmartScaleConnect/blob/a9e5c04f1079b65d456c8a5fd296775a1ef29e8f/LICENSE), Alexey Khit 2025. The complete notice is bundled in `app/src/main/assets/licenses/SmartScaleConnect-MIT.txt`. This notice does not select a license for Fitness Hub itself.

## What runs

`XiaomiScaleRequest.history` constructs only the read request descriptor, with the separate base URL and signature path. CN uses `/app/eco/scale/getData`, nested param, numeric uid; DE/I2/RU/SG/US use `/app/eco/common/scale/getUserDataByPage`, string uid and accountId 0, matching upstream. `did=0` is the upstream account/model request convention, not verified physical-device discovery. The model header is validated. No HTTP is performed and no arbitrary host can be supplied through the scope.

`XiaomiScaleProtocol` accepts a decrypted `{code:0,result:[...]}` envelope or its already-extracted result array. A vendor error, missing result, unexpected shape or malformed JSON fails explicitly, never as an empty successful import. The small internal JSON reader rejects duplicate keys, invalid numbers/escapes, unpaired surrogates, trailing documents and bounded depth/size/node-limit violations. It is private to this protocol, not a new application-wide framework.

`fromSource` 1/2 use outer createTime milliseconds because upstream documents inconsistent units in inner time. Source 3 uses inner time milliseconds and embedded bodyResData. Outer createTime is always retained separately for pagination. Values outside the declared post-2000 scale timestamp contract are unresolved; no implicit seconds-to-ms conversion. Future measurements are flagged without rewriting their timestamps. A source format is NOT inferred from CN/global region.

Weight, BMI, body-fat/water percentages, bone/muscle/skeletal/lean/fat/water/protein masses, protein/muscle/bone percentages, body age/type/score, visceral index, heart rate and BMR have separate per-field observations. Imported composition stays VENDOR_ESTIMATE. Weight/heart rate are only vendor-reported: no assumption that every record was physically measured rather than edited. The BMR period is explicitly unverified, so it is not yet eligible for an HC power mapping. Model variants other than the pinned ms103/ms104 mappings remain unverified; S400 Pro compatibility is not claimed.

## Identity, extensions and privacy

Connection/region/login UID, outer subject UID/accountId, device, serial, dataVersion and createTime are kept as distinct fields. No names or weight-based profile guesses. Missing or conflicting subject IDs remain unresolved. `dataVersion` is not assumed to be a revision counter and `sn` not assumed globally unique. Stable IDs and confirmed local-person binding belong to FH-DATA-01/02.

Known numeric raw fields retain their lexical representation independently of normalized values. Numeric unknown extensions (including arrays/nested objects), booleans and nulls are retained without invented units. Arbitrary text/user objects and sensitive key families are excluded with an explicit issue; short unit-label fields have a bounded format. This is intentionally not a claim of lossless preservation of every unknown textual field. Future vendor text fields require a reviewed allowlist. Source metadata can itself be private; DTO/JSON `toString()` is redacted and no response/header/body is logged. Do not persist whole HTTP responses or authentication objects in the health store.

## Pagination contract

Upstream assumes pages of 20 and finishes on a short page. We retain this as an explicit termination HEURISTIC, not proof that the server returned all history. Descending createTime is required. An oversized, unordered or non-advancing full page fails before commit. The next cursor is the last createTime, never minus one: equal-time boundary records must not silently disappear.

`XiaomiHistoryReader` executes fetch -> bounded off-main-thread parse -> injected commit -> next request. The committer must eventually persist records/checkpoint atomically. Failure/cancellation does not advance the local cursor. A page-budget stop returns Paused with its cursor rather than Completed; rowsReceived is not an imported-record count. No implementation of durable storage, retry scheduler, auth transport or global deduplication is claimed by this interface.

## Verification and next dependencies

Contract tests cover numeric/string variants, three formats, errors, profile identity, preserved unknown numerics, incompatible models/units, invalid metrics, secret-like fields, malformed/deep JSON, cursor boundaries, commit sequencing, failure and cancellation. They are NOT device/cloud or Room tests. The next persistence slice must add subject binding, provenance envelope, migration and backup representation before wiring these records to UI. SAFE gates and a privately signed channel precede real credentials.
