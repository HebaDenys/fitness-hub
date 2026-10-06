package io.github.hebadenys.fitnesshub.core.scale

/**
 * MiBeacon v5 advertisement header, as broadcast by Xiaomi body composition
 * scales.
 *
 * Layout (all multi-byte integers little-endian):
 * ```
 * offset 0      frame control      bit 0 marks an encrypted payload
 * offset 1..2   product id         hardware identifier
 * offset 3..4   frame counter      rolling counter
 * offset 5..10  device address     6 bytes
 * offset 11..   encrypted payload
 * last 10       message integrity  authentication tag
 * ```
 *
 * The 11 header bytes double as the AES-CCM additional authenticated data, so
 * the header cannot be altered without invalidating the tag.
 */
data class MiBeaconFrame(
    val frameControl: Int,
    val productId: Int,
    val frameCounter: Int,
    val deviceAddress: String,
    val encryptedPayload: ByteArray,
    val messageIntegrityCode: ByteArray
) {
    val isEncrypted: Boolean get() = frameControl and 0x01 != 0

    /** Additional authenticated data covering the whole header. */
    fun additionalAuthenticatedData(): ByteArray = byteArrayOf(
        frameControl.toByte(),
        (productId and 0xFF).toByte(),
        ((productId shr 8) and 0xFF).toByte(),
        (frameCounter and 0xFF).toByte(),
        ((frameCounter shr 8) and 0xFF).toByte()
    ) + deviceAddressBytes()

    private fun deviceAddressBytes(): ByteArray =
        deviceAddress.split(':').map { it.toInt(16).toByte() }.toByteArray()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MiBeaconFrame) return false
        return frameControl == other.frameControl &&
            productId == other.productId &&
            frameCounter == other.frameCounter &&
            deviceAddress == other.deviceAddress &&
            encryptedPayload.contentEquals(other.encryptedPayload) &&
            messageIntegrityCode.contentEquals(other.messageIntegrityCode)
    }

    override fun hashCode(): Int {
        var result = frameControl
        result = 31 * result + productId
        result = 31 * result + frameCounter
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + messageIntegrityCode.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 11
        const val MIC_SIZE = 10

        /** Product identifiers reported by Xiaomi body composition scales. */
        const val PRODUCT_MI_SCALE = 0x02D1
        const val PRODUCT_MI_SCALE_V1 = 0x02D2
        const val PRODUCT_S400 = 0x0D02

        val SUPPORTED_PRODUCTS = setOf(PRODUCT_MI_SCALE, PRODUCT_MI_SCALE_V1, PRODUCT_S400)

        /** Returns null when the advertisement is too short or malformed to be a MiBeacon frame. */
        fun parse(bytes: ByteArray): MiBeaconFrame? {
            if (bytes.size < HEADER_SIZE + MIC_SIZE + 1) return null
            val frameControl = bytes[0].toInt() and 0xFF
            val productId = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
            val frameCounter = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
            val deviceAddress = (5..10).joinToString(":") { index ->
                "%02X".format(bytes[index].toInt() and 0xFF)
            }
            val payloadEnd = bytes.size - MIC_SIZE
            return MiBeaconFrame(
                frameControl = frameControl,
                productId = productId,
                frameCounter = frameCounter,
                deviceAddress = deviceAddress,
                encryptedPayload = bytes.copyOfRange(HEADER_SIZE, payloadEnd),
                messageIntegrityCode = bytes.copyOfRange(payloadEnd, bytes.size)
            )
        }
    }
}
