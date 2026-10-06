package io.github.hebadenys.fitnesshub.core.sync

import io.github.hebadenys.fitnesshub.core.healthconnect.HealthDataSource

/** Runtime collaborators of [HealthSyncRepository], grouped as one sync-scoped value. */
class SyncEnvironment(
    val source: HealthDataSource,
    val tokens: SyncTokenStore,
    val logger: AppLogger
)
