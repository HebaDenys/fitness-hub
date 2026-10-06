# Agent instructions

Fitness Hub is a local-first Android health and fitness application.

## Non-negotiable principles

- Kotlin + Jetpack Compose + Room + Health Connect.
- Local-first and privacy-first.
- No mandatory backend or user account.
- Never fabricate health measurements.
- Missing data remains missing; estimated data must be explicitly tagged.
- Preserve timestamps, source provenance and idempotent synchronization.
- Keep vendor/device integrations behind connector boundaries.
- Never commit personal health exports, Xiaomi credentials, production signing keys or API secrets.
- AI must remain optional and BYOK/local-first compatible.
- Run unit tests, lint and APK build after meaningful changes.

## Current project state

The repository already contains working/prototype implementations for:

- Health Connect ingestion and background/incremental sync.
- Material 3 UI and trend charts.
- Nutrition logging, barcode/OCR and Open Food Facts.
- Xiaomi S400 passive BLE capture.
- Xiaomi Home historical import through SmartScaleConnect-compatible CSV.
- Workout/strength logging and PR calculations.
- Cross-domain analytics.
- Encrypted local backup/restore and CSV export.
- Optional BYOK AI plumbing.

Do not assume that a feature is production-ready just because code exists. Audit reachability, real-device behavior, data integrity and error handling before extending it.

## Current priorities

1. Keep `main` build/test/lint green.
2. Validate Health Connect and Xiaomi S400 behavior on real Android hardware.
3. Make existing features fully usable from the UI before adding more surface area.
4. Preserve the dual S400 strategy:
   - historical data: Xiaomi Home -> SmartScaleConnect-compatible CSV -> Fitness Hub;
   - future/live data: passive local BLE listener where supported.
5. Improve data portability, analytics correctness and recovery paths.
6. Harden release/test distribution and security boundaries.
7. Only then expand additional connectors or cloud integrations.

## Xiaomi-specific constraints

- Xiaomi Home must remain usable with the S400.
- Do not require replacing Xiaomi Home.
- Do not mix measurements belonging to different Xiaomi users.
- Direct Xiaomi-cloud login inside Fitness Hub is not currently required.
- If implementing direct Xiaomi cloud access later, research licensing and authentication separately and do not copy Xiaomi Home Assistant integration code whose license forbids reuse in other apps.
- SmartScaleConnect is MIT-licensed and may be used as an interoperability reference/export source, subject to its license.

## Release signing

The repository contains a deliberately public test-only signing key representation for rolling debug APKs. It must never be reused for production or Play Store signing.
