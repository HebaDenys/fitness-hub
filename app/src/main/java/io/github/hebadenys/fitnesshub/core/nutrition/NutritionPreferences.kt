package io.github.hebadenys.fitnesshub.core.nutrition

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nutritionDataStore by preferencesDataStore(name = "nutrition_settings")

/**
 * User preferences for the nutrition feature.
 *
 * Network lookup is opt-in and defaults to off, so a fresh install never
 * contacts a remote service without an explicit choice.
 */
class NutritionPreferences(private val context: Context) {

    val catalogEnabled: Flow<Boolean> =
        context.nutritionDataStore.data.map { it[CATALOG_ENABLED] ?: false }

    /** One-shot read of the opt-in flag; a missing value means the user never opted in. */
    suspend fun isCatalogEnabled(): Boolean = catalogEnabled.first()

    suspend fun setCatalogEnabled(enabled: Boolean) {
        context.nutritionDataStore.edit { it[CATALOG_ENABLED] = enabled }
    }

    private companion object {
        val CATALOG_ENABLED = booleanPreferencesKey("open_food_facts_enabled")
    }
}
