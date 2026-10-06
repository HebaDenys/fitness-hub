# Architecture

Single Android module initially with package boundaries for database, Health Connect, sync and features.

Data flow: Mi Band -> Mi Fitness -> Health Connect -> HealthConnectManager -> HealthSyncRepository -> Room -> Compose UI.

Room is the local analytical store. Daily aggregates use date as an idempotency key; exercise records retain Health Connect IDs for deduplication. Health Connect remains an interoperability layer, not the sole database.

Future S400 flow: S400 -> Xiaomi Home/cloud -> isolated Xiaomi connector -> Room -> Health Connect.
