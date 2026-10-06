package io.github.hebadenys.fitnesshub.core.scale

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MiBeaconFrameTest {

    companion object {
        init {
            // The JVM's default provider has no AES/CCM; Android supplies it via
            // Conscrypt. Registering BouncyCastle here exercises the same nonce,
            // AAD and tag handling the production cipher relies on.
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    private fun frameBytes(
        frameControl: Int = 0x02,
        productId: Int = MiBeaconFrame.PRODUCT_MI_SCALE,
        frameCounter: Int = 1,
        payload: ByteArray = byteArrayOf(1, 2, 3, 4),
        mic: ByteArray = ByteArray(MiBeaconFrame.MIC_SIZE) { it.toByte() }
    ): ByteArray {
        val header = byteArrayOf(
            frameControl.toByte(),
            (productId and 0xFF).toByte(),
            ((productId shr 8) and 0xFF).toByte(),
            (frameCounter and 0xFF).toByte(),
            ((frameCounter shr 8) and 0xFF).toByte()
        ) + byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        return header + payload + mic
    }

    @Test
    @DisplayName("reads header fields from a well-formed advertisement")
    fun parsesHeader() {
        val frame = MiBeaconFrame.parse(frameBytes(frameCounter = 0x0102))

        assertNotNull(frame)
        assertEquals(MiBeaconFrame.PRODUCT_MI_SCALE, frame!!.productId)
        assertEquals(0x0102, frame.frameCounter)
        assertEquals("11:22:33:44:55:66", frame.deviceAddress)
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), frame.encryptedPayload.toList())
    }

    @Test
    @DisplayName("an unencrypted frame is reported as such")
    fun detectsEncryptionFlag() {
        assertTrue(MiBeaconFrame.parse(frameBytes(frameControl = 0x01))!!.isEncrypted)
        assertFalse(MiBeaconFrame.parse(frameBytes(frameControl = 0x00))!!.isEncrypted)
    }

    @Test
    @DisplayName("a truncated advertisement yields null instead of a partial frame")
    fun tooShort_yieldsNull() {
        assertNull(MiBeaconFrame.parse(ByteArray(4)))
        assertNull(MiBeaconFrame.parse(ByteArray(MiBeaconFrame.HEADER_SIZE)))
        assertNull(MiBeaconFrame.parse(ByteArray(MiBeaconFrame.HEADER_SIZE + MiBeaconFrame.MIC_SIZE)))
    }

    @Test
    @DisplayName("the authenticated data covers the header exactly")
    fun authenticatedData_isHeader() {
        val frame = MiBeaconFrame.parse(frameBytes(frameCounter = 0x0304))!!
        val aad = frame.additionalAuthenticatedData()

        assertEquals(MiBeaconFrame.HEADER_SIZE, aad.size)
        assertEquals(0x02, aad[0].toInt() and 0xFF)
        assertEquals(MiBeaconFrame.PRODUCT_MI_SCALE and 0xFF, aad[1].toInt() and 0xFF)
        // Frame counter is little-endian, so 0x0304 stores as 0x04 then 0x03.
        assertEquals(0x04, aad[3].toInt() and 0xFF)
        assertEquals(0x03, aad[4].toInt() and 0xFF)
    }

    @Test
    @DisplayName("frame equality is by content, so replays are comparable")
    fun equality_isByContent() {
        val first = MiBeaconFrame.parse(frameBytes())!!
        val second = MiBeaconFrame.parse(frameBytes())!!

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    @DisplayName("a changed frame counter breaks equality")
    fun counterChange_breaksEquality() {
        val first = MiBeaconFrame.parse(frameBytes(frameCounter = 1))!!
        val second = MiBeaconFrame.parse(frameBytes(frameCounter = 2))!!

        assertFalse(first == second)
    }

    @Test
    @DisplayName("bindkey parsing accepts valid hex and rejects malformed input")
    fun bindkeyParsing() {
        val key = MiBeaconDecryptor.parseBindkey("00112233445566778899aabbccddeeff")

        assertNotNull(key)
        assertEquals(16, key!!.size)
        assertEquals(0x00.toByte(), key[0])
        assertEquals(0xFF.toByte(), key[15])
        assertNull(MiBeaconDecryptor.parseBindkey("too short"))
        assertNull(MiBeaconDecryptor.parseBindkey("zz112233445566778899aabbccddeeff"))
        assertNotNull(MiBeaconDecryptor.parseBindkey("00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"))
    }

    @Test
    @DisplayName("a payload encrypted with the matching bindkey decrypts back to the original bytes")
    fun decryptsRoundTrip() {
        val key = ByteArray(16) { it.toByte() }
        val plaintext = bytes(0x10, 0x03, 0x02, 0xC8, 0x00, 0x10, 0x04, 0x02, 0x2A, 0x00)
        val header = bytes(0x02, 0xD1, 0x02, 0x07, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val nonce = buildNonce(header)
        val aad = header

        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
        cipher.updateAAD(aad)
        val bodyAndTag = cipher.doFinal(plaintext)

        val advertisement = header + bodyAndTag.copyOfRange(0, bodyAndTag.size - MiBeaconFrame.MIC_SIZE) +
            bodyAndTag.copyOfRange(bodyAndTag.size - MiBeaconFrame.MIC_SIZE, bodyAndTag.size)
        val frame = MiBeaconFrame.parse(advertisement)!!

        val decrypted = MiBeaconDecryptor(key).decrypt(frame)

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    @DisplayName("a frame that cannot be authenticated is rejected rather than returned as noise")
    fun unauthenticatedFrame_isRejected() {
        val key = ByteArray(16) { it.toByte() }
        val otherKey = ByteArray(16) { (it + 1).toByte() }
        val frame = MiBeaconFrame.parse(frameBytes())!!

        assertNull(MiBeaconDecryptor(otherKey).decrypt(frame))
    }

    @Test
    @DisplayName("a tampered header invalidates the frame, because the header is authenticated")
    fun tamperedHeader_isRejected() {
        val key = ByteArray(16) { it.toByte() }
        val header = bytes(0x02, 0xD1, 0x02, 0x07, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val nonce = buildNonce(header)
        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
        cipher.updateAAD(header)
        val bodyAndTag = cipher.doFinal(bytes(1, 2, 3, 4))

        val tamperedHeader = header.copyOf()
        tamperedHeader[4] = 0x08.toByte()
        val tampered = MiBeaconFrame.parse(
            tamperedHeader + bodyAndTag.copyOfRange(0, bodyAndTag.size - MiBeaconFrame.MIC_SIZE) +
                bodyAndTag.copyOfRange(bodyAndTag.size - MiBeaconFrame.MIC_SIZE, bodyAndTag.size)
        )!!

        assertNull(MiBeaconDecryptor(key).decrypt(tampered))
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    private fun buildNonce(header: ByteArray): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = 0x01
        for (index in 0 until 6) nonce[1 + index] = header[5 + index]
        nonce[7] = header[3]
        nonce[8] = header[4]
        return nonce
    }
}
