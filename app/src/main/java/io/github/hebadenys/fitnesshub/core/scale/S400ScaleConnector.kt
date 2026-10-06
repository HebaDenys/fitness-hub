package io.github.hebadenys.fitnesshub.core.scale

import android.util.Log
import io.github.hebadenys.fitnesshub.core.connector.DerivedMeasurement
import io.github.hebadenys.fitnesshub.core.connector.HealthSourceConnector
import io.github.hebadenys.fitnesshub.core.connector.RawMeasurement
import java.time.Instant

/**
 * Passive Xiaomi scale connector.
 *
 * Receives MiBeacon advertisements, decrypts them with the locally stored
 * bindkey, and persists the raw weigh-in. It never opens a GATT connection, so
 * the scale keeps working with Xiaomi Home at the same time.
 *
 * Decrypted frames are handed in by [ingestAdvertisement] rather than scanned
 * here, which keeps the protocol logic free of Android Bluetooth APIs and
 * directly testable.
 */
class S400ScaleConnector(
    private val dao: ScaleDao,
    private val bindkeyStore: BindkeyStore,
    private val clock: () -> Instant = { Instant.now() }
) : HealthSourceConnector {

    override val sourceId: String = "xiaomi_scale_ble"

    private var decryptor: MiBeaconDecryptor? = bindkeyStore.load()?.let { MiBeaconDecryptor(it) }

    override suspend fun isReady(): Boolean {
        if (decryptor == null) decryptor = bindkeyStore.load()?.let { MiBeaconDecryptor(it) }
        return decryptor != null
    }

    fun isConfigured(): Boolean = bindkeyStore.isConfigured()

    /** Stores a user-supplied bindkey and activates the decryptor. */
    fun configureBindkey(hexBindkey: String): Boolean {
        val parsed = MiBeaconDecryptor.parseBindkey(hexBindkey) ?: return false
        bindkeyStore.save(parsed)
        decryptor = MiBeaconDecryptor(parsed)
        return true
    }

    fun clearBindkey() {
        bindkeyStore.clear()
        decryptor = null
    }

    /**
     * Processes one advertisement.
     *
     * @return the stored measurement, or null when the frame is unencrypted,
     * not a scale, cannot be authenticated, or reports no settled weight.
     */
    suspend fun ingestAdvertisement(data: ByteArray?, measuredAt: Instant = clock()): RawMeasurement? {
        val cipher = decryptor ?: run {
            Log.i(TAG, "Advertisement ignored: no bindkey configured")
            return null
        }
        if (data == null) return null
        val frame = MiBeaconFrame.parse(data) ?: return null
        if (frame.productId !in MiBeaconFrame.SUPPORTED_PRODUCTS) return null

        // The payload is always fed to the cipher regardless of how the frame
        // control bits are laid out on a given firmware. The authentication tag
        // is the real gate: an unencrypted or tampered frame fails to verify and
        // yields null, so an uncertain flag bit can never admit bogus data.
        val plaintext = cipher.decrypt(frame) ?: return null
        val payload = ScalePayloadParser.parse(plaintext)
        if (!payload.isUsable) return null

        val impedance = payload.impedanceOhms?.takeIf { BodyCompositionEstimator.isPlausibleImpedance(it) }
        val stored = dao.insertMeasurement(
            ScaleMeasurementEntity(
                deviceAddress = frame.deviceAddress,
                measuredAtMillis = measuredAt.toEpochMilli(),
                weightKg = payload.weightKg,
                impedanceOhms = impedance,
                heartRateBpm = payload.heartRateBpm,
                profileSlot = payload.profileSlot,
                provenance = ScaleMeasurementEntity.PROVENANCE_MEASURED
            )
        )
        if (stored == -1L) {
            Log.i(TAG, "Duplicate advertisement ignored for ${frame.deviceAddress}")
            return null
        }

        estimateComposition(measuredAt)
        return RawMeasurement(
            measuredAt = measuredAt,
            sourceId = sourceId,
            deviceAddress = frame.deviceAddress,
            weightKg = payload.weightKg,
            impedanceOhms = impedance,
            heartRateBpm = payload.heartRateBpm,
            profileSlot = payload.profileSlot
        )
    }

    /**
     * Recomputes the derived estimate for [measuredAt] when enough of the user
     * profile is known. Missing profile data yields no estimate rather than a
     * fabricated one.
     */
    suspend fun estimateComposition(measuredAt: Instant): DerivedMeasurement? {
        val entity = dao.profile() ?: return null
        val height = entity.heightCm ?: return null
        val age = entity.ageYears ?: return null
        val sex = entity.sex?.let { stored ->
            runCatching { Sex.valueOf(stored) }.getOrNull()
        } ?: return null
        val weight = dao.measurementsSince(measuredAt.toEpochMilli())
            .firstOrNull { it.measuredAtMillis == measuredAt.toEpochMilli() }
            ?.weightKg ?: return null

        val composition = BodyCompositionEstimator.estimate(weight, UserProfile(height, age, sex))
            ?: return null

        dao.upsertEstimate(
            BodyCompositionEstimateEntity(
                measuredAtMillis = measuredAt.toEpochMilli(),
                bodyFatPercent = composition.bodyFatPercent,
                leanMassKg = composition.leanMassKg,
                bodyWaterPercent = composition.bodyWaterPercent,
                basalMetabolicRateKcal = composition.basalMetabolicRateKcal,
                visceralFatIndex = composition.visceralFatIndex,
                provenance = ScaleMeasurementEntity.PROVENANCE_ESTIMATE,
                algorithm = composition.algorithmId
            )
        )
        return DerivedMeasurement(
            measuredAt = measuredAt,
            algorithmId = composition.algorithmId,
            bodyFatPercent = composition.bodyFatPercent,
            leanMassKg = composition.leanMassKg,
            bodyWaterPercent = composition.bodyWaterPercent,
            visceralFatIndex = composition.visceralFatIndex,
            basalMetabolicRateKcal = composition.basalMetabolicRateKcal
        )
    }

    suspend fun saveProfile(heightCm: Double?, ageYears: Int?, sex: Sex?) {
        dao.upsertProfile(
            UserProfileEntity(
                heightCm = heightCm,
                ageYears = ageYears,
                sex = sex?.name
            )
        )
    }

    override suspend fun recentMeasurements(since: Instant?): List<RawMeasurement> {
        val sinceMillis = since?.toEpochMilli() ?: 0L
        return dao.measurementsSince(sinceMillis).mapNotNull { entity ->
            RawMeasurement(
                measuredAt = Instant.ofEpochMilli(entity.measuredAtMillis),
                sourceId = sourceId,
                deviceAddress = entity.deviceAddress,
                weightKg = entity.weightKg,
                impedanceOhms = entity.impedanceOhms,
                heartRateBpm = entity.heartRateBpm,
                profileSlot = entity.profileSlot
            )
        }
    }

    private companion object {
        const val TAG = "FitnessHubScale"
    }
}
