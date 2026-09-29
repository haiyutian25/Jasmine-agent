package com.lhzkml.jasmine.core.data.repository

import com.lhzkml.jasmine.core.data.datastore.ProviderDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.BuiltInProviders
import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ModelList
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Data-layer entry point for the model-provider list (the core's built-in
 * presets + user-added OpenAI-protocol providers).
 */
interface ProviderRepository {
    /** Hot stream of the persisted providers, started eagerly at injection time. */
    val providersStateFlow: StateFlow<List<ProviderConfig>>

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

    override val providersStateFlow: StateFlow<List<ProviderConfig>> =
        providerDataStore
            .providers
            .stateIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                initialValue = builtInProviders.list(),
            )

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
