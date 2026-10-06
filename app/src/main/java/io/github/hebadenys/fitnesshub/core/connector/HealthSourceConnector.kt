package io.github.hebadenys.fitnesshub.core.connector

import java.time.Instant

/**
 * Contract every external measurement source must satisfy.
 *
 * Connectors normalise vendor payloads into [RawMeasurement] values and never
 * touch the presentation layer or the database directly. Raw values keep their
 * original timestamp and the identifier of the device that produced them, so
 * ingestion stays idempotent and provenance is never lost.
 */
interface HealthSourceConnector {

    /** Stable identifier stored as the data origin of everything this connector produces. */
    val sourceId: String

    /** Whether the connector is configured and able to deliver measurements. */
    suspend fun isReady(): Boolean

    /**
     * Latest measurements, newest first. Returns an empty list when nothing has
     * been captured yet; it never invents a placeholder measurement.
     */
    suspend fun recentMeasurements(since: Instant? = null): List<RawMeasurement>
}

/**
 * One authentic physical reading from an external device.
 *
 * Only hardware-reported values belong here. Anything computed from these values
 * is a [DerivedMeasurement] and must carry an algorithm identifier.
 */
data class RawMeasurement(
    val measuredAt: Instant,
    val sourceId: String,
    val deviceAddress: String?,
    val weightKg: Double?,
    val impedanceOhms: Double?,
    val heartRateBpm: Long?,
    val profileSlot: Int?
)

/**
 * A value computed from raw measurements.
 *
 * [algorithmId] is mandatory: an estimate without a named algorithm would be
 * indistinguishable from a measurement, which the project forbids.
 */
data class DerivedMeasurement(
    val measuredAt: Instant,
    val algorithmId: String,
    val bodyFatPercent: Double?,
    val leanMassKg: Double?,
    val bodyWaterPercent: Double?,
    val visceralFatIndex: Double?,
    val basalMetabolicRateKcal: Double?
)
