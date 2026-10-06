package io.github.hebadenys.fitnesshub.core.scale

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ScalePayloadParserTest {

    private fun field(id: Int, vararg value: Int): ByteArray =
        byteArrayOf((id and 0xFF).toByte(), ((id shr 8) and 0xFF).toByte(), value.size.toByte()) +
            value.map { it.toByte() }.toByteArray()

    private val weight74_2 = field(0x1003, 0xF8, 0x39)
    private val stabilized = field(0x1007, 1)
    private val impedance500 = field(0x1004, 0xF4, 0x01)
    private val heartRate62 = field(0x1005, 62)
    private val profile2 = field(0x1006, 2)

    @Test
    @DisplayName("reads weight as hundredths of a kilogram")
    fun readsWeight() {
        val payload = ScalePayloadParser.parse(weight74_2 + stabilized)

        assertEquals(74.2, payload.weightKg)
    }

    @Test
    @DisplayName("a settled weigh-in with weight is usable")
    fun settledWeight_isUsable() {
        val payload = ScalePayloadParser.parse(weight74_2 + stabilized)

        assertTrue(payload.isUsable)
        assertFalse(payload.measurementRemoved)
    }

    @Test
    @DisplayName("an unsettled reading is not usable, so a stepping-on value is never stored")
    fun unsettledWeight_isNotUsable() {
        val payload = ScalePayloadParser.parse(weight74_2)

        assertEquals(74.2, payload.weightKg)
        assertFalse(payload.stabilized)
        assertFalse(payload.isUsable)
    }

    @Test
    @DisplayName("an absent weight stays null instead of becoming zero")
    fun absentWeight_staysNull() {
        val payload = ScalePayloadParser.parse(stabilized + impedance500)

        assertNull(payload.weightKg)
        assertFalse(payload.isUsable)
    }

    @Test
    @DisplayName("impedance is read when present and dropped when the scale reports none")
    fun readsImpedance() {
        assertEquals(500.0, ScalePayloadParser.parse(impedance500).impedanceOhms)

        val missing = ScalePayloadParser.parse(
            field(0x1004, 0xFF, ScalePayloadParser.IMPEDANCE_UNAVAILABLE)
        )
        assertNull(missing.impedanceOhms)
        assertFalse(missing.hasImpedance)

        assertNull(ScalePayloadParser.parse(field(0x1004, 0x00, 0x00)).impedanceOhms)
    }

    @Test
    @DisplayName("heart rate and profile slot are read when broadcast")
    fun readsHeartRateAndProfile() {
        val payload = ScalePayloadParser.parse(heartRate62 + profile2)

        assertEquals(62L, payload.heartRateBpm)
        assertEquals(2, payload.profileSlot)
    }

    @Test
    @DisplayName("missing heart rate stays null, never zero bpm")
    fun absentHeartRate_staysNull() {
        assertNull(ScalePayloadParser.parse(weight74_2 + stabilized).heartRateBpm)
    }

    @Test
    @DisplayName("a removal flag marks the reading as unusable even when a weight is present")
    fun removedReading_isNotUsable() {
        val payload = ScalePayloadParser.parse(weight74_2 + stabilized + field(0x1013, 1))

        assertTrue(payload.measurementRemoved)
        assertFalse(payload.isUsable)
    }

    @Test
    @DisplayName("unknown field identifiers are skipped without losing the known ones")
    fun unknownFields_areSkipped() {
        val payload = ScalePayloadParser.parse(
            field(0x0999, 7) + weight74_2 + field(0x0888, 1, 2) + stabilized
        )

        assertEquals(74.2, payload.weightKg)
        assertTrue(payload.isUsable)
    }

    @Test
    @DisplayName("a field whose length contradicts its type is ignored rather than misread")
    fun mismatchedLength_isIgnored() {
        val payload = ScalePayloadParser.parse(byteArrayOf(0x03, 0x10, 0x03, 0x01, 0x02, 0x03) + stabilized)

        assertNull(payload.weightKg)
    }

    @Test
    @DisplayName("a truncated final field does not throw and does not corrupt earlier values")
    fun truncatedPayload_isSafe() {
        val truncated = weight74_2 + stabilized + byteArrayOf(0x05, 0x10, 0x04, 0x01)

        val payload = ScalePayloadParser.parse(truncated)

        assertEquals(74.2, payload.weightKg)
        assertTrue(payload.isUsable)
    }

    @Test
    @DisplayName("an empty payload yields no measurements rather than zeros")
    fun emptyPayload_yieldsNulls() {
        val payload = ScalePayloadParser.parse(ByteArray(0))

        assertNull(payload.weightKg)
        assertNull(payload.impedanceOhms)
        assertNull(payload.heartRateBpm)
        assertNull(payload.profileSlot)
        assertFalse(payload.isUsable)
    }

    @Test
    @DisplayName("an implausible weight is still surfaced raw, and left to the caller to reject")
    fun implausibleWeight_isSurfaced() {
        val payload = ScalePayloadParser.parse(field(0x1003, 0x01, 0x00) + stabilized)

        assertEquals(0.005, payload.weightKg)
    }
}
