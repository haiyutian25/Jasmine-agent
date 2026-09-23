package com.lhzkml.jasmine.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.providerStore: DataStore<Preferences> by preferencesDataStore(
    name = PROVIDER_PREFS_FILE_NAME
)

/**
 * Preferences DataStore backing the model-provider list.
 *
 * The whole list is stored as one JSON string under a single key: provider
 * configs are a small, always-read/written-as-a-whole collection, so a JSON
 * blob keeps reads atomic and avoids a Room table for trivial structured data.
 * Corrupt or missing JSON falls back to the factory seed ([ProviderConfig.DEFAULTS]).
 */
@Singleton
class ProviderDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> = context.providerStore

    private val json = Json { ignoreUnknownKeys = true }

    /** Provider stream; a missing/corrupt entry falls back to the factory seed. */
    val providers: Flow<List<ProviderConfig>> = store.data.map { prefs ->
        val raw = prefs[KEY_PROVIDERS]
        if (raw.isNullOrBlank()) {
            ProviderConfig.DEFAULTS
        } else {
            runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }
                .getOrDefault(ProviderConfig.DEFAULTS)
        }
    }

    /** Atomically updates the provider list; [transform] receives the current value. */
    suspend fun update(transform: (List<ProviderConfig>) -> List<ProviderConfig>) {
        store.edit { prefs ->
            val current = prefs[KEY_PROVIDERS]
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }.getOrNull()
                }
                ?: ProviderConfig.DEFAULTS
            prefs[KEY_PROVIDERS] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        val KEY_PROVIDERS = stringPreferencesKey("providers")
    }
}

internal const val PROVIDER_PREFS_FILE_NAME = "model_providers"
