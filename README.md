# Fitness Hub

[![Android CI](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml/badge.svg)](https://github.com/HebaDenys/fitness-hub/actions/workflows/android.yml)

A local-first, privacy-focused Android application for health metrics, body composition, activity, sleep, nutrition, and workout analytics.

> [!WARNING]
> **Early Test Build**: Fitness Hub is currently in active pre-alpha development. The application ID has changed to `io.github.hebadenys.fitnesshub`; if you previously installed an "Open Health Hub" build, please uninstall it prior to installing this version.

---

## Why

Personal health data is routinely fragmented across proprietary apps and locked behind mandatory cloud accounts or subscriptions. Fitness Hub unifies this data into a single local analytical store under your control.

- **Local-first & offline-first**: No mandatory accounts, cloud backends, advertisements, or tracking SDKs.
- **Data sovereignty**: You own your health data; local import and export are a core requirement (planned, Phase 6).
- **Zero fabricated data**: Missing measurements remain `null` or unavailable; estimated values (e.g., body composition derived from impedance) are explicitly tagged with algorithm provenance.
- **Isolated connectors**: Hardware and external services interface through decoupled adapters.
- **Optional AI**: Any future machine learning or LLM assistance is strictly opt-in and never required for core operation.

---

## Features & Status

| Feature / Component | Status | Details |
|---|---|---|
| **Architecture Foundation** | Implemented | Single-module skeleton with Kotlin, Jetpack Compose, Material 3, Hilt, Room SQLite, and Navigation. |
| **Health Connect Ingestion** | Partial (prototype) | Reads steps, distance, active/total calories, resting HR (latest), SpO2 (latest), weight, body fat, sleep duration, and exercise sessions with Health Connect deduplication. *M1 hardening fixes missing value handling (`null` vs `0`), granular permissions, midnight sleep attribution, HR series, and provenance.* |
| **User Interface** | Partial (prototype) | Basic screens for Dashboard, Activity, Sleep, Body, and Settings. *M2 will introduce the complete Material 3 design system, charts, and metric breakdown cards.* |
| **Background & Incremental Sync** | Planned | Periodic background sync via WorkManager and incremental ingestion using Health Connect Changes tokens (Milestone M1). |
| **Xiaomi S400 Scale Connector** | Planned | Passive local BLE MiBeacon reception with AES-CCM bindkey decryption; no cloud dependency (Phase 4). |
| **Nutrition Tracking** | Planned | Local food database, on-device barcode scanner, label OCR, and Open Food Facts connector (Phase 3). |
| **Training & Workout Tracking** | Planned | Custom exercises, sets/reps/load, RPE, 1RM calculations, and strength progression (Phase 5). |
| **Cross-Domain Analytics** | Planned | Rolling averages, caloric balance vs. weight, sleep vs. performance, and PDF/CSV reports (Phase 6). |
| **Encrypted Backup & Restore** | Planned | On-device AES-GCM encrypted backup and JSON/CSV import/export (Phase 6). |
| **Optional AI Estimations** | Planned | Natural language meal logging and photo-based meal estimation (Phase 7). |

---

## Supported Devices & Data Flows

```mermaid
flowchart LR
    subgraph Wearables
        MB["Xiaomi Mi Band"] --> MF["Mi Fitness App"]
        MF --> HC["Health Connect"]
    end

    subgraph Scale ["Xiaomi S400 Scale"]
        S400["S400 Scale"] -- "Encrypted BLE Advertisements" --> BLE["Fitness Hub BLE Connector<br/>(Passive / AES-CCM)"]
        S400 -. "Normal weigh-in" .-> XH["Xiaomi Home App"]
    end

    HC --> FH["Fitness Hub Engine"]
    BLE --> FH
    FH --> ROOM[("Local Room DB<br/>(Analytical Store)")]
    ROOM --> UI["Compose UI & Analytics"]
```

- **Xiaomi Mi Band**: Synchronizes activity, sleep, heart rate, and workouts via the official Mi Fitness app into Android Health Connect, which Fitness Hub imports locally.
- **Xiaomi Body Composition Scale S400 (Planned)**: Passive local BLE reception of encrypted MiBeacon advertisements (AES-CCM). Does not require disconnecting the scale from Xiaomi Home, never contacts the Xiaomi cloud, and keeps credentials off the device. See [docs/connectors/xiaomi-s400.md](docs/connectors/xiaomi-s400.md).
  - *Interim Workaround*: Third-party bridge utilities (e.g., *MiScale Sync*) can sync S400 weight and body fat into Health Connect, which Fitness Hub already reads today (unendorsed community option).

---

## Install the Test APK

Automated test builds are generated on every push to the repository:

1. Download the latest debug APK from the rolling [test-latest prerelease](https://github.com/HebaDenys/fitness-hub/releases/tag/test-latest).
2. Install the APK on your Android device (ensure "Install unknown apps" permission is granted for your browser or file manager).
3. If upgrading from an older "Open Health Hub" build, uninstall the old app first to accommodate the updated package identifier (`io.github.hebadenys.fitnesshub`).

---

## Setup

1. **Verify Health Connect**: Built into Android 14+. On Android 9 through 13, install [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) from Google Play.
2. **Configure Companion App**: In your wearable companion app (e.g., Mi Fitness), enable data synchronization to Health Connect.
3. **Grant Permissions**: Open Fitness Hub, navigate to **Settings**, tap **Grant permissions**, and approve the requested read permissions.
4. **Initial Sync**: Tap **Sync now** to perform your first health data import.

---

## Build from Source

**Requirements:**
- JDK 17 (e.g., Eclipse Temurin 17)
- Android SDK Platform 36 (Build-Tools 35.x or 36.x)

```bash
# Linux / macOS
./gradlew testDebugUnitTest lintDebug assembleDebug

# Windows (PowerShell)
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The resulting debug APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Architecture

Fitness Hub is structured as a single Android Gradle module with strict internal package boundaries (`core/database`, `core/healthconnect`, `core/sync`, `core/model`, `feature/*`, `di`). Room serves as the local analytical data store, while Health Connect functions as an interoperability layer. External devices interact exclusively through decoupled connector interfaces.

For design patterns, data models, and idempotency guarantees, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Privacy

Fitness Hub contains:
- No backend server
- No user accounts or login systems
- No advertising networks
- No behavioral analytics or telemetry SDKs

All health records reside strictly on your device. Future local secrets (such as the S400 BLE bindkey, Phase 4) will be stored encrypted via Android Keystore.

---

## Medical Disclaimer

Fitness Hub is designed for personal fitness tracking and wellness informational purposes only. It is not a medical device, does not provide medical diagnoses or treatment recommendations, and must not replace professional healthcare consultation. Statistical trends and correlations displayed by the app reflect mathematical associations, not clinical causation.

---

## Roadmap

- **M0**: Rename, documentation overhaul, Gradle wrapper toolchain.
- **M1**: Phase 2 hardening (null handling, granular permissions, midnight sleep attribution, HR series, provenance, KSP, Room schemas).
- **M2**: UI foundation (Material 3 design system, Vico charts, trend cards).
- **Phase 3**: Nutrition tracking *(requires explicit approval)*.
- **Phase 4**: Xiaomi S400 local BLE connector *(requires explicit approval)*.
- **Phase 5**: Workout and strength tracking *(requires explicit approval)*.
- **Phase 6**: Cross-domain analytics, reports, and encrypted backups *(requires explicit approval)*.
- **Phase 7**: Optional AI enhancements *(requires explicit approval)*.
- **Phase 8**: Release readiness and security verification *(requires explicit approval)*.

For detailed scope and completion criteria, consult [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for code standards, testing practices, and guidelines on architectural constraints.

---

## License

**License: TBD**

The intended licensing model is source-available and free for personal, non-commercial use, with commercial usage governed by a separate license (evaluating PolyForm Noncommercial 1.0.0 and Business Source License 1.1). A Contributor License Agreement (CLA) will likely be required for external contributions. Until an explicit license file is published in the repository, all rights are reserved by default.

---

## Sustainability

Fitness Hub is committed to keeping core personal tracking completely free and local-first. For our planned sponsorship and sustainability framework, see [docs/SUSTAINABILITY.md](docs/SUSTAINABILITY.md).
