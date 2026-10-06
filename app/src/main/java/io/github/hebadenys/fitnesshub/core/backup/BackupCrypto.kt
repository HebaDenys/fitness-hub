package io.github.hebadenys.fitnesshub.core.backup

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, self-verifying backup container.
 *
 * The payload is sealed with AES-256-GCM under a key derived from the user's
 * passphrase with PBKDF2-HMAC-SHA256, and wrapped with a SHA-256 digest of the
 * plaintext so a restored archive can be proven complete rather than merely
 * decrypted.
 *
 * The passphrase never leaves this class: it is taken as a `CharArray`, used to
 * derive the key, and wiped in a `finally` block.
 */
object BackupCrypto {

    private const val MAGIC = "FHUBBAK"
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000
    private const val DIGEST_BYTES = 32

    const val VERSION_OFFSET = MAGIC.length
    const val SALT_OFFSET = VERSION_OFFSET + 1
    const val IV_OFFSET = SALT_OFFSET + SALT_BYTES
    const val HEADER_BYTES = IV_OFFSET + IV_BYTES

    class InvalidBackupException(message: String) : Exception(message)

    /**
     * Seals [plaintext] under [passphrase], generating a fresh salt and IV.
     *
     * The result is self-contained: the header carries everything needed to
     * decrypt, so a backup restores on another device with only the passphrase.
     */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val keyChars = passphrase.copyOf()
        val key = try {
            deriveKey(keyChars, salt)
        } finally {
            keyChars.fill('\u0000')
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(plaintext)
        val envelope = ByteArray(digest.size + plaintext.size)
        digest.copyInto(envelope, 0)
        plaintext.copyInto(envelope, digest.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val sealed = cipher.doFinal(envelope)

        return ByteArray(HEADER_BYTES + sealed.size).also { blob ->
            MAGIC.toByteArray(Charsets.US_ASCII).copyInto(blob, 0)
            blob[VERSION_OFFSET] = VERSION
            salt.copyInto(blob, SALT_OFFSET)
            iv.copyInto(blob, IV_OFFSET)
            sealed.copyInto(blob, HEADER_BYTES)
        }
    }

    /**
     * Opens a backup and verifies it.
     *
     * @throws InvalidBackupException when the container is malformed, the
     * passphrase is wrong, or the payload does not match its digest. A wrong
     * passphrase and a corrupted file are reported the same way on purpose: the
     * GCM tag cannot distinguish them, and pretending otherwise would leak
     * information about the archive.
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        if (blob.size <= HEADER_BYTES) throw InvalidBackupException("Backup is truncated")
        val magic = String(blob, 0, MAGIC.length, Charsets.US_ASCII)
        if (magic != MAGIC) throw InvalidBackupException("Not a Fitness Hub backup")
        val version = blob[VERSION_OFFSET]
        if (version != VERSION) throw InvalidBackupException("Unsupported backup version")

        val salt = blob.copyOfRange(SALT_OFFSET, SALT_OFFSET + SALT_BYTES)
        val iv = blob.copyOfRange(IV_OFFSET, HEADER_BYTES)
        val sealed = blob.copyOfRange(HEADER_BYTES, blob.size)

        val keyChars = passphrase.copyOf()
        val key = try {
            deriveKey(keyChars, salt)
        } finally {
            keyChars.fill('\u0000')
        }

        val envelope = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(sealed)
        } catch (error: Exception) {
            throw InvalidBackupException("Backup could not be opened with this passphrase")
        }

        if (envelope.size < DIGEST_BYTES) throw InvalidBackupException("Backup payload is malformed")
        val expected = envelope.copyOfRange(0, DIGEST_BYTES)
        val payload = envelope.copyOfRange(DIGEST_BYTES, envelope.size)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        if (!MessageDigest.isEqual(expected, actual)) {
            throw InvalidBackupException("Backup failed its integrity check")
        }
        return payload
    }

    fun encryptToBase64(plaintext: ByteArray, passphrase: CharArray): String =
        java.util.Base64.getEncoder().encodeToString(encrypt(plaintext, passphrase))

    fun decryptFromBase64(encoded: String, passphrase: CharArray): ByteArray =
        decrypt(java.util.Base64.getDecoder().decode(encoded), passphrase)

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
