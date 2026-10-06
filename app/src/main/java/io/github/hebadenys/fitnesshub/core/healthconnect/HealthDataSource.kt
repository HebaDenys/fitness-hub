package io.github.hebadenys.fitnesshub.core.healthconnect

import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.HealthChangesPage
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import java.time.Instant
import java.time.LocalDate

/**
 * Normalized read interface over the external health data source.
 * All methods are free of Health Connect types so sync logic stays JVM-testable.
 * Missing or permission-denied data is represented as null / empty, never as zero.
 */
interface HealthDataSource {

    /** Metric keys currently granted for reading. Empty means nothing may be imported. */
    suspend fun grantedMetrics(): Set<String>

    /** Whether the optional READ_HEALTH_DATA_HISTORY permission is granted. */
    suspend fun historyAccessGranted(): Boolean

    /** Per-day aggregate values; a metric with no recorded data stays null. */
    suspend fun aggregateDay(date: LocalDate, metrics: Set<String>): DayAggregate

    /** All records overlapping the range, for the granted [metrics] only. */
    suspend fun readRange(rangeStart: Instant, rangeEnd: Instant, metrics: Set<String>): RangePayload

    /** One page of changed records for an incremental sync. */
    suspend fun readChanges(token: String): HealthChangesPage

    /** Mints a new Changes token for subsequent incremental syncs. */
    suspend fun createChangesToken(): String
}
