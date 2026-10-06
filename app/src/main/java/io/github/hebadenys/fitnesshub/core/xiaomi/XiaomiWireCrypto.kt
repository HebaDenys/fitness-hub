package io.github.hebadenys.fitnesshub.core.xiaomi

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/**
 * Xiaomi wire compatibility only. Adapted from SmartScaleConnect auth.go (MIT),
 * revision a9e5c04. MD5/SHA-1/RC4 are vendor requirements, NOT local storage crypto.
 * All requests still require authenticated HTTPS. Sessions use AES-GCM separately.
 */
internal object XiaomiWireCrypto {
    fun nonce(clock: Clock, random: SecureRandom): ByteArray {
        val minutes = clock.instant().epochSecond / 60
        if (minutes !in 0..0xffffffffL) accessFailure(XiaomiAccessFailure.INVALID_INPUT)
        return ByteBuffer.allocate(12).put(ByteArray(8).also(random::nextBytes)).putInt(minutes.toInt()).array()
    }

    fun signedNonce(security: ByteArray, nonce: ByteArray): ByteArray {
        if (security.size !in 16..64 || nonce.size != 12) accessFailure(XiaomiAccessFailure.INVALID_INPUT)
        return MessageDigest.getInstance("SHA-256").digest(security + nonce)
    }

    fun signature(path: String, data: String, encryptedHash: String?, key: ByteArray): String {
        val input = "POST&$path&data=$data" + (encryptedHash?.let { "&rc4_hash__=$it" } ?: "") + "&" + b64(key)
        return b64(MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8)))
    }

    /** Portable RC4-drop1024 implementation, avoiding availability of Android RC4 providers. */
    fun crypt(key: ByteArray, input: ByteArray): ByteArray {
        if (key.size !in 1..256 || input.size > 2 * 1024 * 1024) accessFailure(XiaomiAccessFailure.INVALID_INPUT)
        val state = IntArray(256) { it }
        var j = 0
        for (i in state.indices) {
            j = (j + state[i] + (key[i % key.size].toInt() and 255)) and 255
            val swap = state[i]; state[i] = state[j]; state[j] = swap
        }
        var i = 0
        j = 0
        fun next(): Int {
            i = (i + 1) and 255
            j = (j + state[i]) and 255
            val swap = state[i]; state[i] = state[j]; state[j] = swap
            return state[(state[i] + state[j]) and 255]
        }
        return try {
            repeat(1024) { next() }
            ByteArray(input.size) { (input[it].toInt() xor next()).toByte() }
        } finally { state.fill(0) }
    }

    fun passwordHash(password: CharArray): String {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return try {
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02X".format(it.toInt() and 255) }
        } finally {
            bytes.fill(0)
            if (buffer.hasArray()) buffer.array().fill(0)
        }
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unbase64(value: String, limit: Int = 2 * 1024 * 1024): ByteArray {
        if (value.length > limit || !value.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) {
            accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        }
        return try { Base64.getDecoder().decode(value) } catch (_: Exception) {
            accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        }
    }

    fun form(fields: Map<String, String>): String = fields.toSortedMap().entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
}
