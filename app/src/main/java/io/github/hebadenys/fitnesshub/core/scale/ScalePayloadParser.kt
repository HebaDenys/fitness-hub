package io.github.hebadenys.fitnesshub.core.scale

/**
 * Decrypted MiBeacon payload for a body composition scale.
 *
 * Event data is a tag/length/value stream: each field starts with a two-byte
 * identifier, a one-byte length, and that many bytes of little-endian value.
 * A field whose identifier is unknown, or whose length does not match its
 * declared type, is skipped rather than guessed.
 */
object ScalePayloadParser {

    private const val TAG_WEIGHT = 0x1003
    private const val TAG_IMPEDANCE = 0x1004
    private const val TAG_HEART_RATE = 0x1005
    private const val TAG_PROFILE = 0x1006
    private const val TAG_STABILIZED = 0x1007
    private const val TAG_REMOVED = 0x1013

    const val IMPEDANCE_UNAVAILABLE = 0xFFFF

    data class Payload(
        val weightKg: Double?,
        val impedanceOhms: Double?,
        val heartRateBpm: Long?,
        val profileSlot: Int?,
        val stabilized: Boolean,
        val measurementRemoved: Boolean
    ) {
        /** A weighing is only usable once the scale reports it as settled. */
        val isUsable: Boolean
            get() = weightKg != null && weightKg > 0.0 && stabilized && !measurementRemoved

        val hasImpedance: Boolean
            get() = impedanceOhms != null && impedanceOhms > 0.0
    }

    fun parse(payload: ByteArray): Payload {
        var weight: Double? = null
        var impedance: Double? = null
        var heartRate: Long? = null
        var profile: Int? = null
        var stabilized = false
        var removed = false

        var index = 0
        while (index + 3 <= payload.size) {
            val id = ((payload[index].toInt() and 0xFF)) or
                ((payload[index + 1].toInt() and 0xFF) shl 8)
            val length = payload[index + 2].toInt() and 0xFF
            val start = index + 3
            if (start + length > payload.size) break

            val value = payload.copyOfRange(start, start + length)
            when (id) {
                TAG_WEIGHT -> if (length == 2) weight = u16(value) / 200.0
                TAG_IMPEDANCE -> if (length == 2) {
                    val raw = u16(value)
                    impedance = if (raw == IMPEDANCE_UNAVAILABLE || raw == 0) null else raw.toDouble()
                }
                TAG_HEART_RATE -> if (length == 1) heartRate = (value[0].toInt() and 0xFF).toLong()
                TAG_PROFILE -> if (length == 1) profile = (value[0].toInt() and 0xFF)
                TAG_STABILIZED -> if (length == 1) stabilized = (value[0].toInt() and 0xFF) != 0
                TAG_REMOVED -> if (length == 1) removed = (value[0].toInt() and 0xFF) != 0
            }
            index = start + length
        }

        return Payload(
            weightKg = weight,
            impedanceOhms = impedance,
            heartRateBpm = heartRate,
            profileSlot = profile,
            stabilized = stabilized,
            measurementRemoved = removed
        )
    }

    /** MiBeacon encodes weights as 16-bit hundredths of a kilogram. */
    private fun u16(bytes: ByteArray): Int =
        (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
}
