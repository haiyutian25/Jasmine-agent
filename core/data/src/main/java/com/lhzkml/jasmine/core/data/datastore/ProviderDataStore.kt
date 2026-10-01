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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** 读路径解码失败的通知口（G1），见 [readFailures]。 */
    private val failures = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Provider stream; a missing entry reads as the factory seed (see the class doc). */
    val providers: Flow<List<ProviderConfig>> = store.data.map { prefs ->
        val read = readStoredProviders(prefs[KEY_PROVIDERS], json, builtInProviders::list)
        // 解码失败**要上报**（G1）：以前这里是静默换成出厂种子，而"存了但读不出来"与"还没存过"
        // 在调用方看来完全一样 —— 界面显示出厂列表、用户一改就被 update 拒掉，他自己那份
        // （含 API key）被"藏起来"，而且没有任何人知道。
        read.failure?.let { failures.tryEmit(it) }
        read.providers
    }

    /**
     * 读路径解码失败的通知（G1）；每次失败发一条（原因文本，供日志）。
     *
     * `extraBufferCapacity = 1`：这是提示不是数据，投不进去也不算错。
     */
    val readFailures: Flow<String> = failures.asSharedFlow()

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

/** [readStoredProviders] 的结果：这次读出来的列表 + 读失败的原因（成功时为 null）。 */
internal data class ProviderRead(
    val providers: List<ProviderConfig>,
    val failure: String?,
)

/**
 * 存下来的那段 JSON → 供应商列表（G6 为可测提出来的纯函数：读路径的真身要 `Context` 与真
 * DataStore，而这条判定不依赖它们）。
 *
 * 三种输入要分得清清楚楚：
 * - **没存过**（null / 空白）→ 出厂种子，**不上报** —— 这是"第一次启动"，不是坏数据；
 * - **存过且能解码** → 用用户自己那份；
 * - **存过但解不出来** → 给出厂种子让界面有东西显示，**同时把原因交出去**（G1）：用户那份
 *   （含 API key）还在磁盘上，不说一声他会以为配置丢了、照着出厂清单重填一遍。
 *
 * [builtIn] 是个 lambda：出厂种子来自 Rust 那边（`built_in_providers`），只在真需要时才问它。
 */
internal fun readStoredProviders(
    raw: String?,
    json: Json,
    builtIn: () -> List<ProviderConfig>,
): ProviderRead {
    if (raw.isNullOrBlank()) return ProviderRead(builtIn(), failure = null)
    return runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }
        .fold(
            onSuccess = { ProviderRead(it, failure = null) },
            onFailure = { ProviderRead(builtIn(), failure = it.message ?: it.toString()) },
        )
}

internal const val PROVIDER_PREFS_FILE_NAME = "model_providers"
