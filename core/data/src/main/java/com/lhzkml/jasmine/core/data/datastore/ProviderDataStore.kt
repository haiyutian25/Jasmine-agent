package com.lhzkml.jasmine.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhzkml.jasmine.core.data.model.BuiltInProviders
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
 *
 * A **missing** entry means "nothing saved yet" and reads as the built-in seed ([BuiltInProviders]).
 * An entry that is there but cannot be decoded is not that: it is what the user saved (API keys
 * included). The read still hands the seed out so the screen has something to show, but the write
 * refuses to touch unreadable text — see [update]. Nothing here silently replaces what it could not
 * parse: configuration that does not parse is reported by whoever reads it, not overwritten.
 */
@Singleton
class ProviderDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builtInProviders: BuiltInProviders,
) {
    private val store: DataStore<Preferences> = context.providerStore

    private val json = Json { ignoreUnknownKeys = true }

    /** Provider stream; a missing entry reads as the factory seed (see the class doc). */
    val providers: Flow<List<ProviderConfig>> = store.data.map { prefs ->
        val raw = prefs[KEY_PROVIDERS]
        if (raw.isNullOrBlank()) {
            builtInProviders.list()
        } else {
            runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }
                .getOrDefault(builtInProviders.list())
        }
    }

    /**
     * Atomically updates the provider list; [transform] receives the current value.
     *
     * When the stored text cannot be decoded the update **fails loudly** (throws) instead of
     * being applied to the seed: writing the seed back would replace what the user saved (API
     * keys included) with a factory list, silently and with no copy anywhere; silently skipping
     * would leave the UI's optimistic state standing with no echo and no error (P0 修复方案 D4
     * 堵的就是这个"既无回灌又无事件"的洞)。抛出让失败可观察：EffectRunner 兜底成 Rejected，
     * 界面回滚并提示，而磁盘上的原文保持不动、仍可人工恢复。
     */
    suspend fun update(transform: (List<ProviderConfig>) -> List<ProviderConfig>) {
        store.edit { prefs ->
            val raw = prefs[KEY_PROVIDERS]
            val current = if (raw.isNullOrBlank()) {
                builtInProviders.list()
            } else {
                runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }.getOrNull()
                    ?: throw IllegalStateException("stored provider list is undecodable; refusing to overwrite it")
            }
            // 没变就不写：省一次落盘，也不会因为这个动作把界面那条流再推一遍。
            val next = json.encodeToString(transform(current))
            if (next != prefs[KEY_PROVIDERS]) prefs[KEY_PROVIDERS] = next
        }
    }

    private companion object {
        val KEY_PROVIDERS = stringPreferencesKey("providers")
    }
}

internal const val PROVIDER_PREFS_FILE_NAME = "model_providers"
