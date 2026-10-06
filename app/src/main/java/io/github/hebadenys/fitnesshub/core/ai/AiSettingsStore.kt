package io.github.hebadenys.fitnesshub.core.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ai_settings")

/**
 * Persists whether AI features are on and which provider was chosen.
 *
 * The API key is deliberately not stored here: it lives in [AiKeyStore] under
 * Keystore encryption, so this DataStore holds only non-secret preferences.
 */
class AiSettingsStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("ai_enabled")
    private val providerKey = stringPreferencesKey("ai_provider")
    private val modelKey = stringPreferencesKey("ai_model")

    val settings: Flow<AiSettings> = context.aiSettingsDataStore.data.map { prefs ->
        AiSettings(
            enabled = prefs[enabledKey] ?: false,
            provider = prefs[providerKey]?.let { id -> AiProvider.entries.firstOrNull { it.id == id } },
            model = prefs[modelKey]
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.aiSettingsDataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setProvider(provider: AiProvider?) {
        context.aiSettingsDataStore.edit { prefs ->
            if (provider == null) prefs.remove(providerKey) else prefs[providerKey] = provider.id
        }
    }

    suspend fun setModel(model: String?) {
        context.aiSettingsDataStore.edit { prefs ->
            val trimmed = model?.trim().orEmpty()
            if (trimmed.isEmpty()) prefs.remove(modelKey) else prefs[modelKey] = trimmed
        }
    }

    /**
     * Turns the feature off. Called when the stored key is removed, so a
     * half-configured setup can never be left enabled.
     */
    suspend fun disable() {
        context.aiSettingsDataStore.edit { it[enabledKey] = false }
    }
}
