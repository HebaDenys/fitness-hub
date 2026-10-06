package io.github.hebadenys.fitnesshub.core.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Backup containers must round-trip losslessly, reject a wrong passphrase, and
 * detect tampering rather than returning damaged data.
 */
class BackupCryptoTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val payload = "daily_health\n2026-03-10|8421|5.2".toByteArray()

    @Test
    @DisplayName("a backup round-trips to exactly the original bytes")
    fun roundTrip_isLossless() {
        val blob = BackupCrypto.encrypt(payload, passphrase)

        assertArrayEquals(payload, BackupCrypto.decrypt(blob, passphrase))
    }

    @Test
    @DisplayName("binary payloads survive the round trip unchanged")
    fun binaryPayload_roundTrips() {
        val binary = ByteArray(512) { (it * 7 % 251).toByte() }

        assertArrayEquals(binary, BackupCrypto.decrypt(BackupCrypto.encrypt(binary, passphrase), passphrase))
    }

    @Test
    @DisplayName("an empty payload round-trips")
    fun emptyPayload_roundTrips() {
        val empty = ByteArray(0)

        assertArrayEquals(empty, BackupCrypto.decrypt(BackupCrypto.encrypt(empty, passphrase), passphrase))
    }

    @Test
    @DisplayName("the ciphertext does not contain the plaintext")
    fun ciphertext_hidesPlaintext() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        val asText = String(blob, Charsets.ISO_8859_1)

        assertFalse(asText.contains("daily_health"))
        assertFalse(asText.contains("8421"))
    }

    @Test
    @DisplayName("two backups of the same data differ, because salt and IV are fresh each time")
    fun encryptions_areNotDeterministic() {
        val first = BackupCrypto.encrypt(payload, passphrase)
        val second = BackupCrypto.encrypt(payload, passphrase)

        assertFalse(first.contentEquals(second))
        assertArrayEquals(payload, BackupCrypto.decrypt(first, passphrase))
        assertArrayEquals(payload, BackupCrypto.decrypt(second, passphrase))
    }

    @Test
    @DisplayName("the wrong passphrase is rejected instead of returning garbage")
    fun wrongPassphrase_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob, "wrong passphrase".toCharArray())
        }
    }

    @Test
    @DisplayName("a near-miss passphrase is still rejected")
    fun nearMissPassphrase_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob, "correct horse battery stapl".toCharArray())
        }
    }

    @Test
    @DisplayName("a flipped ciphertext byte fails authentication")
    fun tamperedCiphertext_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob, passphrase)
        }
    }

    @Test
    @DisplayName("a flipped header byte fails authentication, because the IV is authenticated")
    fun tamperedHeader_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        val ivStart = BackupCrypto.IV_OFFSET
        blob[ivStart] = (blob[ivStart].toInt() xor 0x01).toByte()

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob, passphrase)
        }
    }

    @Test
    @DisplayName("a truncated archive is reported rather than partially restored")
    fun truncatedArchive_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob.copyOfRange(0, blob.size / 2), passphrase)
        }
    }

    @Test
    @DisplayName("a foreign file is rejected as not a backup")
    fun foreignFile_isRejected() {
        val notABackup = ByteArray(200) { 0x7A }

        assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(notABackup, passphrase)
        }
    }

    @Test
    @DisplayName("an unsupported version is refused rather than guessed at")
    fun unsupportedVersion_isRejected() {
        val blob = BackupCrypto.encrypt(payload, passphrase)
        blob[BackupCrypto.VERSION_OFFSET] = 99

        val error = assertThrows(BackupCrypto.InvalidBackupException::class.java) {
            BackupCrypto.decrypt(blob, passphrase)
        }
        assertTrue(error.message!!.contains("version"))
    }

    @Test
    @DisplayName("an empty passphrase is refused up front")
    fun emptyPassphrase_isRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(payload, CharArray(0))
        }
    }

    @Test
    @DisplayName("a passphrase with non-ASCII characters still round-trips")
    fun unicodePassphrase_roundTrips() {
        val unicode = "pàsswörd-Ω-日本語".toCharArray()

        val blob = BackupCrypto.encrypt(payload, unicode)

        assertArrayEquals(payload, BackupCrypto.decrypt(blob, unicode))
    }

    @Test
    @DisplayName("a very long passphrase is accepted")
    fun longPassphrase_works() {
        val long = "x".repeat(500).toCharArray()

        assertArrayEquals(payload, BackupCrypto.decrypt(BackupCrypto.encrypt(payload, long), long))
    }

    @Test
    @DisplayName("base64 transport preserves the archive exactly")
    fun base64_roundTrips() {
        val encoded = BackupCrypto.encryptToBase64(payload, passphrase)

        assertArrayEquals(payload, BackupCrypto.decryptFromBase64(encoded, passphrase))
    }

    @Test
    @DisplayName("different passphrases produce different ciphertext for the same input")
    fun differentPassphrases_differ() {
        val first = BackupCrypto.encrypt(payload, "passphrase one".toCharArray())
        val second = BackupCrypto.encrypt(payload, "passphrase two".toCharArray())

        assertNotEquals(String(first), String(second))
    }

    @Test
    @DisplayName("the caller's passphrase array is not modified by encrypting")
    fun callerPassphrase_isNotMutated() {
        val caller = "untouched".toCharArray()

        BackupCrypto.encrypt(payload, caller)

        assertArrayEquals("untouched".toCharArray(), caller)
    }

    @Test
    @DisplayName("the salt is not reused between archives")
    fun salt_isFreshPerArchive() {
        val first = BackupCrypto.encrypt(payload, passphrase)
        val second = BackupCrypto.encrypt(payload, passphrase)

        val firstSalt = first.copyOfRange(BackupCrypto.SALT_OFFSET, BackupCrypto.SALT_OFFSET + 16)
        val secondSalt = second.copyOfRange(BackupCrypto.SALT_OFFSET, BackupCrypto.SALT_OFFSET + 16)

        assertFalse(firstSalt.contentEquals(secondSalt))
        assertEquals(16, firstSalt.size)
    }
}
