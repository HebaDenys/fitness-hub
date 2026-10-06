package io.github.hebadenys.fitnesshub.core.scale

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-CCM decryption of MiBeacon payloads.
 *
 * Parameters follow the MiBeacon convention: a 13-byte nonce
 * (`0x01 || device address || frame counter || 0x00 * 4`) and the 11 header
 * bytes as additional authenticated data, with a 10-byte authentication tag.
 *
 * A wrong bindkey, a truncated packet or a corrupted tag all fail here rather
 * than yielding plausible-looking numbers, so a failed decryption is never
 * turned into a measurement.
 */
class MiBeaconDecryptor(private val bindkey: ByteArray) {

    init {
        require(bindkey.size == KEY_SIZE_BYTES) {
            "bindkey must be $KEY_SIZE_BYTES bytes, was ${bindkey.size}"
        }
    }

    /**
     * Returns the plaintext payload, or null when the frame cannot be
     * authenticated. Authentication failures are not retried or guessed.
     */
    fun decrypt(frame: MiBeaconFrame): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(bindkey, "AES"),
            IvParameterSpec(nonce(frame))
        )
        cipher.updateAAD(frame.additionalAuthenticatedData())
        cipher.doFinal(frame.encryptedPayload + frame.messageIntegrityCode)
    } catch (error: Exception) {
        Log.w(TAG, "MiBeacon frame rejected (${error::class.java.simpleName})")
        null
    }

    private fun nonce(frame: MiBeaconFrame): ByteArray {
        val address = frame.deviceAddress.split(':').map { it.toInt(16) }
        val nonce = ByteArray(13)
        nonce[0] = 0x01
        for (index in 0 until 6) {
            nonce[1 + index] = address[index].toByte()
        }
        nonce[7] = (frame.frameCounter and 0xFF).toByte()
        nonce[8] = ((frame.frameCounter shr 8) and 0xFF).toByte()
        return nonce
    }

    companion object {
        const val KEY_SIZE_BYTES = 16
        const val MAC_SIZE_BITS = 80
        const val TRANSFORMATION = "AES/CCM/NoPadding"
        private const val TAG = "FitnessHubScale"

        /** Parses a user-supplied hex bindkey, returning null for malformed input. */
        fun parseBindkey(hex: String): ByteArray? {
            val cleaned = hex.replace(Regex("[^0-9a-fA-F]"), "")
            if (cleaned.length != KEY_SIZE_BYTES * 2) return null
            return try {
                ByteArray(cleaned.length / 2) { index ->
                    cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            } catch (error: NumberFormatException) {
                null
            }
        }
    }
}
