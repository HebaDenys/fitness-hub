package io.github.hebadenys.fitnesshub.core.xiaomi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface XiaomiSessionKeys {
    fun existing(): SecretKey?
    fun create(): SecretKey
    fun delete()
}

/** Separate alias: logout cannot remove AI/bindkey keys or the local health archive. */
internal class XiaomiAndroidSessionKeys : XiaomiSessionKeys {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun existing(): SecretKey? = store().getKey(ALIAS, null) as? SecretKey
    override fun create(): SecretKey = existing() ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    }.generateKey()
    override fun delete() { store().deleteEntry(ALIAS) }
    companion object { private const val ALIAS = "fitnesshub.xiaomi.session.v1" }
}

internal interface XiaomiSessionFile {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun delete()
}

internal class XiaomiAtomicSessionFile(context: Context) : XiaomiSessionFile {
    private val file = AtomicFile(File(context.noBackupFilesDir, "xiaomi-session-v1.bin"))
    override fun read(): ByteArray? = try {
        file.openRead().use { XiaomiHttpsTransport.readLimited(it, MAX_BYTES) }
    } catch (_: FileNotFoundException) { null }

    override fun write(bytes: ByteArray) {
        if (bytes.size > MAX_BYTES) accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    override fun delete() {
        file.delete()
        if (file.baseFile.exists()) accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
    }

    companion object { const val MAX_BYTES = 24 * 1024 }
}

/** AES-GCM framing/version is authenticated; a missing key is never regenerated on read. */
internal class XiaomiSessionCipher(private val keys: XiaomiSessionKeys) {
    fun seal(plaintext: ByteArray): ByteArray {
        if (plaintext.size > 16 * 1024) accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.create())
        cipher.updateAAD(AAD)
        val iv = cipher.iv
        if (iv.size != 12) accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
        return byteArrayOf(1) + iv + cipher.doFinal(plaintext)
    }

    fun open(sealed: ByteArray): ByteArray {
        if (sealed.size !in 30..XiaomiAtomicSessionFile.MAX_BYTES || sealed[0] != 1.toByte()) {
            accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
        }
        val key = keys.existing() ?: accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.copyOfRange(1, 13)))
        cipher.updateAAD(AAD)
        return cipher.doFinal(sealed, 13, sealed.size - 13)
    }

    fun forgetKey() = keys.delete()
    companion object { private val AAD = "fitnesshub.xiaomi.session.v1".toByteArray(Charsets.US_ASCII) }
}

/** Singleton per app: serialized file/keystore access, outside Room and outside backups. */
internal class XiaomiProtectedSessionStore(
    private val file: XiaomiSessionFile,
    private val cipher: XiaomiSessionCipher
) : XiaomiSessionStore {
    private val lock = Mutex()

    override suspend fun load(): XiaomiSession? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val sealed = file.read() ?: return@withLock null
                val plaintext = try { cipher.open(sealed) } finally { sealed.fill(0) }
                try { XiaomiSessionCodec.decode(plaintext) } finally { plaintext.fill(0) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                // Only session material is discarded. A failed recovery never clears health data.
                runCatching { file.delete() }
                runCatching { cipher.forgetKey() }
                accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            }
        }
    }

    override suspend fun save(session: XiaomiSession) = withContext(Dispatchers.IO) {
        lock.withLock {
            val plaintext = XiaomiSessionCodec.encode(session)
            try {
                val sealed = cipher.seal(plaintext)
                try { file.write(sealed) } finally { sealed.fill(0) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
            } finally { plaintext.fill(0) }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            // Attempt both: removing the key also invalidates a ciphertext file that cannot be deleted.
            val deleteFile = runCatching { file.delete() }
            val deleteKey = runCatching { cipher.forgetKey() }
            if (deleteFile.isFailure || deleteKey.isFailure) accessFailure(XiaomiAccessFailure.STORAGE_ERROR)
        }
    }
}
