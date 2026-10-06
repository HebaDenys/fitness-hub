# Open Health Hub

Open Health Hub is a privacy-first, local-first Android application designed to become a single personal hub for health, body composition, nutrition, activity, sleep, workouts, goals, analytics and reports.

The project is being built first around a real-world Xiaomi setup, but the architecture is intentionally generic so additional devices, services and contributors can be supported later.

## Goal

The long-term goal is simple:

> Keep all personal health and fitness data in one application, normalize it into one local data model, make it useful through analytics and reports, and still interoperate with Android Health Connect.

Today health data is fragmented across multiple apps. A wearable may store activity and sleep in one app, a smart scale may keep body composition in another, nutrition may live in a third app, and workout history somewhere else.

Open Health Hub aims to unify all of that.

## Principles

- Free for personal/non-commercial use.
- Source available and community-friendly.
- No advertisements.
- No subscriptions required for core functionality.
- No mandatory cloud account.
- No mandatory backend.
- Local-first and offline-first wherever practical.
- No behavioral tracking or analytics SDKs.
- User owns the data.
- Import/export must be possible.
- External AI is optional, never required for core operation.
- Health data must never be fabricated.
- Device integrations should remain isolated behind connector interfaces.

## Initial real-world setup

The first supported setup is:

### Xiaomi Mi Band

Primary data flow:

```
Mi Band
  -> Mi Fitness
  -> Android Health Connect
  -> Open Health Hub
  -> local Room database
```

The application should read whichever metrics Mi Fitness actually exposes through Health Connect, such as:

- steps
- distance
- active calories
- exercise sessions
- heart rate
- resting heart rate
- SpO2
- sleep
- sleep stages
- weight/body metrics when available

The app must handle missing metrics gracefully instead of inventing values.

### Xiaomi Body Composition Scale S400

Desired future data flow:

```
Xiaomi S400
  -> Xiaomi Home
  -> Xiaomi services/cloud
  -> Open Health Hub Xiaomi connector
  -> local database
  -> optional Health Connect export
```

The objective is to KEEP Xiaomi Home as the normal scale application while importing existing and future measurements into Open Health Hub.

The S400 connector is not implemented yet.

## Product scope

Open Health Hub is intended to evolve into a complete health and fitness application with these main areas:

### Dashboard

A unified daily view of:

- weight/body composition
- calories and macros
- steps/activity
- sleep
- heart/vitals
- recent workouts
- goals and progress

### Body composition

History and trends for:

- weight
- BMI
- body-fat percentage
- lean/muscle mass
- body water
- visceral fat
- bone mass
- BMR
- other supported measurements

### Nutrition

Planned functionality includes:

- calorie and macro tracking
- meals and recipes
- custom foods
- barcode scanning
- Open Food Facts integration
- nutrition-label OCR
- local food cache/database
- optional photo-based AI meal estimation
- optional natural-language meal entry

Unknown products should be learnable locally: scan barcode, photograph the label, confirm parsed values, then reuse the product later without depending on a commercial database.

### Training

Planned support includes:

- gym training
- powerlifting
- bodybuilding
- calisthenics
- cardio
- custom exercises
- sets/reps/load
- RPE/RIR
- personal records
- estimated 1RM
- volume/progression analytics

### Sleep and activity

Health Connect will be used as the main Android interoperability layer for wearable data.

### Analytics

A major goal of the project is cross-domain analytics, including:

- calorie intake vs weight change
- body-fat trends
- rolling weight averages
- sleep consistency
- steps/activity trends
- training volume and strength progression
- sleep vs workout performance
- bodyweight vs strength
- protein intake vs training/body-composition trends

Correlation must not be presented as causation, and the application is not intended to provide medical diagnosis.

## Architecture

Current architecture:

```
External sources
      |
      v
Health Connect <------> Open Health Hub
                           |
                           v
                        Room DB
                           |
                           v
                     Analytics / UI
```

Room/SQLite is the local analytical store.

Health Connect is an interoperability layer, not the application's only database.

Current package responsibilities include:

- local Room persistence
- Health Connect integration
- synchronization/deduplication
- Compose UI
- future isolated connectors

The project currently uses a single Android Gradle module to avoid premature multi-module complexity.

## Current status

### Phase 1 — Foundation

Implemented:

- Kotlin
- Jetpack Compose
- Material 3
- Hilt
- Room
- navigation
- local-first architecture
- initial tests
- CI pipeline
- APK generation

### Phase 2 — Health Connect

Implemented foundation for:

- Health Connect availability
- granular read permissions
- activity import
- sleep import
- heart/vitals import
- weight/body-fat import
- daily aggregation
- exercise-session import
- idempotent daily synchronization
- exercise deduplication
- Dashboard
- Activity
- Sleep
- Body
- Settings

This is still an early test build and must be validated on real devices and real Mi Fitness data.

### Next planned phases

Phase 3:
- nutrition tracker
- food database
- Open Food Facts
- barcode scanner
- OCR nutrition labels

Phase 4:
- Xiaomi S400 cloud connector
- historical import
- incremental synchronization
- body-composition mapping

Phase 5:
- workout/training tracker
- exercise history
- strength analytics

Phase 6:
- advanced analytics
- goals
- reports
- cross-domain correlations

Phase 7:
- optional AI features

## Privacy

There is currently:

- no backend
- no account system
- no advertising SDK
- no behavioral telemetry
- no mandatory cloud storage

Health data is stored locally.

Any future external AI/cloud integration must be optional and must clearly disclose which data leaves the device.

## APK test builds

GitHub Actions automatically builds a debug APK from the current development branch.

The latest test APK is published in the GitHub Releases section under the `test-latest` prerelease.

These builds are for development/testing and are not production releases.

## Licensing direction

The project is intended to be source-available and free for personal/non-commercial use.

The intended model is:

- personal/non-commercial use: free
- community contributions: allowed under compatible project terms
- commercial use or monetization: requires a separate commercial license

This means the final license will likely NOT be a standard OSI open-source license such as MIT or Apache 2.0.

The exact license terms still need final review before a public production release.

## Potential sustainability

The project may eventually be supported through:

- GitHub Sponsors
- individual donations
- corporate sponsorship
- sponsored development of connectors/features
- commercial licensing
- enterprise integration/support
- optional hosted/cloud services

The core local application should remain useful without paid services.

## Build

Requirements:

- JDK 17
- Android SDK with API 36
- Android Studio or Gradle

The CI pipeline runs:

- unit tests
- lint
- debug APK build
- artifact upload
- rolling prerelease publication

## Development rules

See [AGENTS.md](AGENTS.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

The project should remain compilable after each milestone, and new phases should be implemented incrementally rather than as one large rewrite.
