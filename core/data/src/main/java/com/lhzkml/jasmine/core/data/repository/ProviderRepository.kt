package com.lhzkml.jasmine.core.data.repository

import com.lhzkml.jasmine.core.data.datastore.ProviderDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.BuiltInProviders
import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ModelList
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Data-layer entry point for the model-provider list (the core's built-in
 * presets + user-added OpenAI-protocol providers).
 */
interface ProviderRepository {
    /** Hot stream of the persisted providers, started eagerly at injection time. */
    val providersStateFlow: StateFlow<List<ProviderConfig>>

    /**
     * 读存储失败的通知（G1）：存的那份**解不出来**时每次失败发一条（原因文本，供日志）。
     *
     * 它让"存了但读不出来"不再与"还没存过"长得一样 —— 两者以前都会让界面显示出厂清单，
     * 而前者意味着用户自己那份（含 API key）还在磁盘上、只是读不出来。
     */
    val readFailures: Flow<String>

    /** Inserts [provider] or replaces the stored entry with the same id. */
    suspend fun upsertProvider(provider: ProviderConfig)

    /** Removes the provider with [id]; built-in presets are never removed. */
    suspend fun deleteProvider(id: String)

    /**
     * Fetches the model catalog advertised by [provider]'s endpoint
     * (`GET /v1/models`, Bearer auth). Throws on transport/parse failure —
     * the result is transient UI data, never persisted by the repository.
     */
    suspend fun fetchModels(provider: ProviderConfig): List<String>

    /**
     * 核心目录里认得的模型（名字、上下文容量），界面拿它把模型表单自动填好。只有出厂那几家
     * 供应商有目录（按 [providerId] 认），认不出就是空的。纯本地查询，同样不落盘。
     */
    suspend fun catalog(providerId: String): List<CatalogModel>
}

/**
 * DataStore-backed implementation. Reads expose a [StateFlow]; each mutation
 * atomically edits the stored JSON list.
 */
class ProviderRepositoryImpl(
    private val providerDataStore: ProviderDataStore,
    private val modelList: ModelList,
    private val builtInProviders: BuiltInProviders,
    dispatcherManager: DispatcherManager,
) : ProviderRepository {

    // Long-lived repository scope on a deterministic dispatcher. A SupervisorJob
    // keeps a failed collection from killing the scope (and the StateFlow with it).
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatcherManager.default)

    init {
        // 出厂清单是会变的（这次就多了一家 OpenRouter），而存下来的那份是一次快照 —— 缺哪家就补哪家。
        // 已经存在的那几家按用户改过的原样留着：这里不覆盖，也不动它们的模型。
        repositoryScope.launch {
            providerDataStore.update { current -> current + missingBuiltIns(current) }
        }
    }

    /** [BuiltInProviders] 里、存的那份还没有的那些（按出厂顺序追加在列表后面）。 */
    private fun missingBuiltIns(stored: List<ProviderConfig>): List<ProviderConfig> =
        builtInProviders.list().filter { seed -> stored.none { it.id == seed.id } }

    override val providersStateFlow: StateFlow<List<ProviderConfig>> =
        providerDataStore
            .providers
            .stateIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                initialValue = builtInProviders.list(),
            )

    override val readFailures: Flow<String> = providerDataStore.readFailures

    override suspend fun upsertProvider(provider: ProviderConfig) =
        providerDataStore.update { current ->
            val index = current.indexOfFirst { it.id == provider.id }
            if (index >= 0) current.toMutableList().also { it[index] = provider }
            else current + provider
        }

    override suspend fun deleteProvider(id: String) =
        providerDataStore.update { current ->
            current.filterNot { it.id == id && !it.isBuiltIn }
        }

    override suspend fun fetchModels(provider: ProviderConfig): List<String> =
        modelList.fetch(provider)

    override suspend fun catalog(providerId: String): List<CatalogModel> =
        modelList.catalog(providerId)
}
