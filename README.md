# Open Health Hub

Privacy-first, source-available Android health and fitness hub.

## Phase 1 + 2

Kotlin, Jetpack Compose, Room, Hilt and Android Health Connect. The app reads supported activity, exercise, sleep, heart/vitals and body data into a local analytical store and exposes Dashboard, Activity, Sleep, Body and Settings screens.

Primary wearable flow: Mi Band -> Mi Fitness -> Health Connect -> Open Health Hub.

No backend, account, ads or behavioral telemetry. Missing health data remains unavailable; the app does not fabricate measurements.

Xiaomi S400 cloud import, nutrition, barcode/OCR, full workout authoring and AI are intentionally deferred.

## Build

Requires JDK 17 and Android SDK. Open in Android Studio or run the CI workflow.

## License

Commercial use is not intended to be automatically granted. Final source-available/non-commercial terms must be reviewed before public release; do not assume commercial-use rights until a final LICENSE is committed.
