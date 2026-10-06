# Fitness Hub — Product Roadmap

Fitness Hub has moved beyond its original linear "Phase 1 -> Phase 7" implementation plan. Most major domains now have prototype implementations. The priority is therefore **stabilization, real-device validation, data integrity and release readiness**, not adding more disconnected feature surface.

---

## Current state

### Implemented / prototype

- Android local-first foundation: Kotlin, Compose, Material 3, Hilt, Room.
- Health Connect ingestion with granular permissions.
- Background/incremental Health Connect synchronization.
- Activity, sleep, body and dashboard views.
- Nutrition logging.
- Barcode scanning and nutrition-label OCR.
- Optional Open Food Facts lookup with local caching.
- Xiaomi S400 passive BLE prototype.
- Xiaomi Home history import through SmartScaleConnect-compatible CSV.
- Workout/strength logging.
- Estimated 1RM and personal-record detection.
- Cross-domain analytics.
- Encrypted local backup/restore.
- CSV export.
- Optional BYOK AI infrastructure.
- GitHub Actions test/lint/APK pipeline and rolling test release.

"Implemented" here means code exists and is reachable. It does **not** automatically mean the feature is production-ready.

---

## Priority S0 — Keep main releasable

This is permanent.

### Requirements

- Every change on `main` must pass:
  - unit tests;
  - Android lint;
  - `assembleDebug`.
- `test-latest` must point to the newest successful `main` build.
- Test APKs use the repository's deliberately public test-only signing key.
- Production signing material must never be committed.
- Database migrations must be explicit and schema exports tracked.
- Existing user data must not be silently destroyed by an upgrade.

---

## Priority S1 — Real-device validation

Code-level tests cannot prove vendor/device behavior.

### Health Connect

Validate on the real Android device:

- Mi Fitness permission flow;
- which Mi Band metrics actually reach Health Connect;
- steps and calorie aggregation;
- sleep sessions/stages;
- heart-rate series;
- resting heart rate;
- SpO2;
- exercise sessions;
- historical access;
- incremental synchronization after new records arrive.

Document missing Mi Fitness exports as limitations rather than synthesizing them.

### Xiaomi S400

Validate both paths:

#### Historical

```text
Xiaomi Home -> SmartScaleConnect CSV -> Fitness Hub
```

Test:

- older measurements;
- vendor body-fat/body-water/BMR values;
- duplicate import;
- CSV with Denys + Dahiana or any other multi-user case;
- explicit user filtering;
- timestamps/time zone.

#### Live

```text
S400 -> passive BLE -> Fitness Hub
```

Test:

- Android runtime Bluetooth permissions;
- real S400 advertisements;
- regional firmware variants;
- Xiaomi Home operating concurrently;
- bindkey decryption;
- weight;
- impedance;
- heart rate;
- duplicate broadcasts.

Do not mark BLE support stable before a physical weigh-in passes end-to-end.

---

## Priority S2 — Canonical body-data model

Body data can currently originate from:

- Health Connect;
- imported Xiaomi history;
- direct S400 BLE;
- Fitness Hub estimates.

The product must resolve these consistently.

### Required behavior

- Prefer genuine measured/imported vendor values over local estimates.
- Never replace a genuine measurement with an estimate.
- Keep source/provenance visible.
- Ensure Dashboard, Body and Insights use the same resolved body history.
- Prevent same-day duplicate sources from inflating analytics.
- Define deterministic precedence when several measurements exist on one day.

---

## Priority S3 — Data portability and lossless recovery

Backup/restore is a core product feature, not an optional extra.

### Already available

- passphrase-derived AES-256-GCM encrypted backup;
- Android file picker;
- CSV weight export.

### Required hardening

A complete backup must include all meaningful user-owned data:

- daily Health Connect summaries;
- detailed heart/vitals samples where intentionally retained;
- local nutrition foods;
- nutrition entries and daily totals;
- raw scale measurements;
- imported scale measurements;
- body-composition estimates;
- local user profile;
- workout exercises;
- workout sessions;
- workout sets;
- templates;
- relevant local preferences/configuration where safe.

Restore must:

- validate integrity before writes;
- remain idempotent where possible;
- preserve relationships;
- report skipped/conflicting records;
- be tested across database versions.

---

## Priority S4 — Nutrition completion

Validate the complete real workflow:

```text
barcode
 -> local cache
 -> optional Open Food Facts
 -> unknown product
 -> label photo/OCR
 -> user review
 -> local product
 -> meal log
 -> daily totals
```

### Remaining work

- real-camera validation;
- OCR parsing across Paraguay/Spanish, Italian and English labels;
- serving-size handling;
- recipes/saved meals;
- editing existing foods/log entries;
- broader micronutrients;
- Health Connect nutrition export where mapping is appropriate;
- better search and recent/favorite foods.

The user must always be able to correct OCR/network data before it becomes trusted local data.

---

## Priority S5 — Workout completion

### Existing prototype

- exercise catalogue;
- sessions;
- sets/reps/load;
- RPE/RIR;
- rest timer;
- estimated 1RM;
- personal records.

### Remaining work

- reusable workout templates;
- editing completed sessions;
- richer exercise catalogue;
- bodyweight/assisted exercise semantics;
- cardio/duration/distance models;
- Health Connect exercise export;
- progression charts;
- volume by muscle/exercise/week.

---

## Priority S6 — Analytics integrity

Cross-domain analysis is valuable only when source data is coherent.

### Next targets

- make imported/direct scale measurements part of the canonical weight series;
- calorie intake vs smoothed weight trend;
- steps vs weight change;
- sleep vs training performance;
- bodyweight vs strength;
- resting HR vs sleep/activity;
- configurable 7/14/30-day smoothing;
- minimum sample-size thresholds;
- transparent correlation coefficients and sample counts.

Never present correlation as causation.

---

## Priority S7 — AI as optional enhancement

AI is never required for core Fitness Hub behavior.

### Rules

- disabled by default;
- BYOK/provider abstraction;
- API key encrypted locally;
- exact outgoing payload visible before sending;
- meal/photo estimates require user confirmation;
- health/fitness summaries must not be presented as medical diagnosis;
- local/on-device alternatives preferred when practical.

### Future

- structured natural-language meal entry;
- structured workout entry;
- optional photo meal estimation;
- natural-language questions over local analytics using minimized/redacted context.

---

## Priority S8 — Release readiness

Before a public production release:

- finalize source-available/non-commercial license;
- implement contributor licensing policy/CLA if needed;
- formal privacy policy;
- Google Play Health Connect declarations;
- production signing;
- R8/release build verification;
- dependency/license audit;
- OWASP MASVS-oriented security review;
- accessibility review;
- localization review;
- reproducible migration tests;
- diagnostic export without remote telemetry.

---

## Distribution

### Test channel

Every successful relevant `main` build publishes the rolling `test-latest` prerelease APK.

The test APK is signed with a deliberately public, repository-owned **test-only** key so new test builds can update previous test builds.

### Production

Production releases will use a separate private signing identity and versioned immutable releases. The public CI test key must never be reused.

---

## Architecture rule

Do not add a backend merely because a feature could use one.

A backend must solve a concrete requirement that cannot be reasonably met by the local-first architecture, and must remain optional unless the project's direction is explicitly changed.
