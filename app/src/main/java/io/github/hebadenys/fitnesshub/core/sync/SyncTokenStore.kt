package io.github.hebadenys.fitnesshub.core.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** A persisted Health Connect Changes token plus the backfill window it was created for. */
data class SyncToken(val value: String, val rangeDays: Int)

interface SyncTokenStore {
    suspend fun read(): SyncToken?
    suspend fun write(token: SyncToken)
}

private val Context.syncTokenDataStore by preferencesDataStore(name = "sync_token")

class DataStoreSyncTokenStore(private val context: Context) : SyncTokenStore {

    private val tokenKey = stringPreferencesKey("changes_token")
    private val rangeKey = intPreferencesKey("changes_token_range_days")

    override suspend fun read(): SyncToken? {
        val prefs = context.syncTokenDataStore.data.first()
        val value = prefs[tokenKey] ?: return null
        return SyncToken(value, prefs[rangeKey] ?: -1)
    }

    override suspend fun write(token: SyncToken) {
        context.syncTokenDataStore.edit { prefs ->
            prefs[tokenKey] = token.value
            prefs[rangeKey] = token.rangeDays
        }
    }
}
