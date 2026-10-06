package io.github.hebadenys.fitnesshub.core.scale

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the scale bindkey encrypted at rest with an Android Keystore key.
 *
 * The bindkey itself never leaves the device and is never written to disk in
 * clear: only AES-GCM ciphertext plus its IV are persisted. The Keystore key is
 * non-exportable, so the stored bytes are useless on any other device, which is
 * exactly the property wanted for a credential that unlocks health readings.
 */
class BindkeyStore(private val context: Context) {

    /** Stores the bindkey, replacing any previously stored one. */
    fun save(bindkey: ByteArray) {
        require(bindkey.size == MiBeaconDecryptor.KEY_SIZE_BYTES) { "bindkey must be 16 bytes" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(bindkey)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BINDKEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /** Returns the stored bindkey, or null when none is stored or it cannot be decrypted. */
    fun load(): ByteArray? {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(KEY_BINDKEY, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        } catch (error: Exception) {
            Log.w(TAG, "Stored bindkey could not be decrypted (${error::class.java.simpleName})")
            null
        }
    }

    fun isConfigured(): Boolean = load() != null

    fun clear() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fitnesshub.scale.bindkey"
        const val PREFERENCES = "fitnesshub_scale_bindkey"
        const val KEY_BINDKEY = "bindkey_ciphertext"
        const val KEY_IV = "bindkey_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val TAG = "FitnessHubScale"
    }
}
