package com.lhzkml.jasmine.feature.provider.impl

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.agent.ProbeResult
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.model.catalogKey
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Draft of a model being added to / edited under the open provider editor.
 * [id] is null while adding; [modelId] may be prefilled from the fetched
 * catalog or left empty for a custom id. Length fields are raw input text so
 * the field can be cleared mid-edit; blank/invalid parses to 0 ("not set").
 *
 * [contextLengthTouched] 记"用户自己动过这一栏没有"：没动过时改 [modelId] 会照核心目录重填，
 * 动过之后就完全交给他，不再被覆盖。
 */
data class ModelEditorState(
    val id: String?,
    val modelId: String,
    val contextLength: String,
    val maxOutputLength: String,
    val contextLengthTouched: Boolean = false,
    /** 这个模型的默认档（新建会话的起点）；空串 = 未设置。只有目录外的模型才编辑它。 */
    val reasoningEffort: String = "",
    /**
     * 模型 ID 这一栏能不能改。**从「获取模型列表」里挑来的模型 = `true`** —— 那个 id 是端点报的，
     * 不该在这张表单上被改掉；要自己写 id 就走「自定义模型 ID」那条路（它的 id 本来就该由用户定）。
     */
    val modelIdLocked: Boolean = false,
    /**
     * 这个 id 是不是核心目录里的模型。是的话档位由目录直接给（那套档位会自动出现在聊天页的档位
     * 面板里），这一页就不显示档位那一行 —— 免得读成第二处设置。
     */
    val isCatalogModel: Boolean = false,
)

/** 选择列表里的一项：线上 id + 核心目录给的名字（目录不认得它时为 null，界面就显示 id）。 */
data class ModelSheetItem(val modelId: String, val name: String?)

/** Content of the model-picker bottom sheet. */
sealed interface ModelSheetState {
    /** Catalog request in flight. */
    data object Fetching : ModelSheetState

    /** 端点报出来的模型；[items] 可能是空的（端点一个都没报）。 */
    data class ModelList(val items: List<ModelSheetItem>) : ModelSheetState

    /** Catalog request failed; the sheet stays open with a retry/custom path. */
    data object Error : ModelSheetState
}

/**
 * Draft of the provider being added or edited. [id] is null while adding a
 * new provider; a non-null id means "edit the stored entry with this id".
 * Models live on the draft and are persisted together with the provider on
 * save. [modelSheet] / [modelEditor] drive the two bottom sheets and are
 * only meaningful while the editor is open. [isProbing] marks an in-flight
 * connectivity check against the (unsaved) draft credentials.
 */
data class ProviderEditorState(
    val id: String?,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val apiType: ProviderApiType,
    val isBuiltIn: Boolean,
    val models: List<ModelConfig>,
    val modelSheet: ModelSheetState?,
    val modelEditor: ModelEditorState?,
    val isProbing: Boolean = false,
)

/**
 * Single immutable UI state for the provider feature (UDF).
 *
 * [editor] == null renders the provider list; a non-null editor renders the
 * add/edit form (with optional model sheet / model editor on top).
 */
data class ProviderState(
    /** 首帧先空着：内置种子由核心给出，仓库那条流立刻会送上第一份。 */
    val providers: List<ProviderConfig> = emptyList(),
    val editor: ProviderEditorState? = null,
)

/**
 * One-time events emitted by [ProviderViewModel]; consumed exactly once by the UI.
 */
sealed interface ProviderEvent {
    /** Show a transient toast carrying a string resource, formatted with [formatArgs] when present. */
    data class ShowToast(
        @StringRes val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : ProviderEvent
}

/**
 * Actions sent from the UI to [ProviderViewModel] via [BaseViewModel.trySendAction].
 */
sealed interface ProviderAction {

    // Provider list
    data object AddClicked : ProviderAction
    data class EditClicked(val id: String) : ProviderAction
    data class DeleteClicked(val id: String) : ProviderAction

    // Provider editor fields
    data class NameChanged(val value: String) : ProviderAction
    data class BaseUrlChanged(val value: String) : ProviderAction
    data class ApiKeyChanged(val value: String) : ProviderAction
    data class ApiTypeSelected(val type: ProviderApiType) : ProviderAction
    data object SaveClicked : ProviderAction
    data object CancelClicked : ProviderAction

    /** Runs a real round trip through the draft credentials (see [ProviderProbe]). */
    data object TestConnectionClicked : ProviderAction

    // Model catalog sheet
    data object FetchModelsClicked : ProviderAction
    data object CustomModelClicked : ProviderAction
    data object ModelSheetDismissed : ProviderAction
    data class ModelSelected(val modelId: String) : ProviderAction

    // Model editor sheet
    data class ModelEditClicked(val id: String) : ProviderAction
    data class ModelDeleteClicked(val id: String) : ProviderAction
    data class ModelIdChanged(val value: String) : ProviderAction
    data class ModelContextLengthChanged(val value: String) : ProviderAction
    data class ModelMaxOutputChanged(val value: String) : ProviderAction

    /** 选了这个模型的默认档；空串代表「未设置」（请求里不发任何推理参数）。 */
    data class ModelReasoningEffortSelected(val value: String) : ProviderAction
    data object ModelSaveClicked : ProviderAction
    data object ModelCancelClicked : ProviderAction

    /**
     * Internal actions: results of asynchronous work posted back onto the action
     * channel so that all state mutations stay synchronous inside [handleAction].
     */
    sealed interface Internal : ProviderAction {
        data class ProvidersReceived(val providers: List<ProviderConfig>) : Internal
        data class ModelsFetched(val models: List<ModelSheetItem>) : Internal
        data object ModelsFetchFailed : Internal
        data class ProbeFinished(val result: ProbeResult) : Internal
    }
}

/**
 * ViewModel backing the model-provider feature (MVVM + unidirectional data flow).
 *
 * The UI renders [stateFlow] and sends every user intent as a [ProviderAction];
 * one-shot feedback (toasts) is delivered through [eventFlow]. State mutations
 * happen synchronously inside [handleAction]; persistence, the model-catalog
 * fetch and the connectivity probe post follow-up [ProviderAction.Internal]
 * actions.
 */
@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val providerProbe: ProviderProbe,
) : BaseViewModel<ProviderState, ProviderEvent, ProviderAction>(
    initialState = ProviderState(),
) {

    init {
        providerRepository
            .providersStateFlow
            .map { ProviderAction.Internal.ProvidersReceived(it) }
            .onEach(::sendAction)
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: ProviderAction) {
        when (action) {
            ProviderAction.AddClicked -> {
                // 新供应商还没有 id，目录认不出它 —— 把上一家的清掉，免得拿别人的值来填。
                catalog = emptyMap()
                updateState {
                    copy(
                        editor = ProviderEditorState(
                            id = null,
                            name = "",
                            baseUrl = "",
                            apiKey = "",
                            apiType = ProviderApiType.CHAT_COMPLETIONS,
                            isBuiltIn = false,
                            models = emptyList(),
                            modelSheet = null,
                            modelEditor = null,
                        ),
                    )
                }
            }

            is ProviderAction.EditClicked -> handleEditClicked(action)
            is ProviderAction.DeleteClicked -> handleDeleteClicked(action)

            is ProviderAction.NameChanged -> updateEditor { copy(name = action.value) }
            is ProviderAction.BaseUrlChanged -> updateEditor { copy(baseUrl = action.value) }
            is ProviderAction.ApiKeyChanged -> updateEditor { copy(apiKey = action.value) }
            is ProviderAction.ApiTypeSelected -> updateEditor { copy(apiType = action.type) }

            ProviderAction.SaveClicked -> handleSaveClicked()
            ProviderAction.CancelClicked -> updateState { copy(editor = null) }
            ProviderAction.TestConnectionClicked -> handleTestConnectionClicked()

            ProviderAction.FetchModelsClicked -> handleFetchModelsClicked()
            ProviderAction.CustomModelClicked -> updateEditor {
                copy(
                    modelSheet = null,
                    modelEditor = ModelEditorState(
                        id = null,
                        modelId = "",
                        contextLength = "",
                        maxOutputLength = "",
                        // 自己填的 id：先当成目录外，打了 id 之后按目录再判。
                        isCatalogModel = false,
                    ),
                )
            }
            ProviderAction.ModelSheetDismissed -> updateEditor { copy(modelSheet = null) }
            is ProviderAction.ModelSelected -> updateEditor {
                val known = knownModel(action.modelId)
                copy(
                    modelSheet = null,
                    modelEditor = ModelEditorState(
                        id = null,
                        modelId = action.modelId,
                        // 目录认得这个模型就顺手把上下文容量填上；不认得就空着 —— 不猜。
                        contextLength = known.catalogContextLength(),
                        maxOutputLength = "",
                        isCatalogModel = known != null,
                        // 这个 id 是端点报的、用户刚点的那个：锁住不给改。
                        modelIdLocked = true,
                    ),
                )
            }

            is ProviderAction.ModelEditClicked -> handleModelEditClicked(action)
            is ProviderAction.ModelDeleteClicked -> handleModelDeleteClicked(action)
            is ProviderAction.ModelIdChanged -> updateModelEditor {
                // 手打/粘贴自定义 id 走的是同一条规矩：目录认得就填；用户自己动过那一栏就不再覆盖。
                val known = knownModel(action.value.trim())
                val isCatalog = known != null
                copy(
                    modelId = action.value,
                    contextLength = if (contextLengthTouched) {
                        contextLength
                    } else {
                        known.catalogContextLength()
                    },
                    // 一旦变成目录里的模型，档位就交给目录：配置里那份默认档清掉，免得留个不再生效的值。
                    reasoningEffort = if (isCatalog) "" else reasoningEffort,
                    isCatalogModel = isCatalog,
                )
            }
            is ProviderAction.ModelReasoningEffortSelected ->
                updateModelEditor { copy(reasoningEffort = action.value) }
            is ProviderAction.ModelContextLengthChanged ->
                updateModelEditor {
                    copy(
                        contextLength = action.value.filter(Char::isDigit),
                        contextLengthTouched = true,
                    )
                }
            is ProviderAction.ModelMaxOutputChanged ->
                updateModelEditor { copy(maxOutputLength = action.value.filter(Char::isDigit)) }
            ProviderAction.ModelSaveClicked -> handleModelSaveClicked()
            ProviderAction.ModelCancelClicked -> updateEditor { copy(modelEditor = null) }

            is ProviderAction.Internal.ProvidersReceived -> {
                updateState { copy(providers = action.providers) }
            }
            is ProviderAction.Internal.ModelsFetched -> updateEditor {
                copy(modelSheet = ModelSheetState.ModelList(action.models))
            }
            ProviderAction.Internal.ModelsFetchFailed -> {
                updateEditor { copy(modelSheet = ModelSheetState.Error) }
                sendEvent(ProviderEvent.ShowToast(R.string.provider_fetch_failed_toast))
            }
            is ProviderAction.Internal.ProbeFinished -> handleProbeFinished(action)
        }
    }

    // region Provider action handlers

    private fun handleEditClicked(action: ProviderAction.EditClicked) {
        val provider = state.providers.firstOrNull { it.id == action.id } ?: return
        updateState {
            copy(
                editor = ProviderEditorState(
                    id = provider.id,
                    name = provider.name,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    apiType = provider.apiType,
                    isBuiltIn = provider.isBuiltIn,
                    models = provider.models,
                    modelSheet = null,
                    modelEditor = null,
                ),
            )
        }
        // 目录是核心里的一张本地表：先把这家供应商的取回来，后面"改模型 id"才有得填。
        viewModelScope.launch { catalog = loadCatalog(provider.id) }
    }

    private fun handleDeleteClicked(action: ProviderAction.DeleteClicked) {
        val provider = state.providers.firstOrNull { it.id == action.id } ?: return
        // Built-in presets (DeepSeek) can be edited but never deleted.
        if (provider.isBuiltIn) return
        // Optimistic removal; the repository StateFlow echo is a no-op afterwards.
        updateState { copy(providers = providers.filterNot { it.id == action.id }) }
        viewModelScope.launch { providerRepository.deleteProvider(action.id) }
        sendEvent(ProviderEvent.ShowToast(R.string.provider_deleted_toast))
    }

    private fun handleSaveClicked() {
        val editor = state.editor ?: return
        val name = editor.name.trim()
        val baseUrl = editor.baseUrl.trim()
        val apiKey = editor.apiKey.trim()
        if (name.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty()) {
            sendEvent(ProviderEvent.ShowToast(R.string.provider_incomplete_toast))
            return
        }
        val provider = ProviderConfig(
            id = editor.id ?: UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiType = editor.apiType,
            isBuiltIn = editor.isBuiltIn,
            models = editor.models,
        )
        // Optimistic write-through; the repository echo reconciles the list.
        updateState {
            val index = providers.indexOfFirst { it.id == provider.id }
            val next = if (index >= 0) {
                providers.toMutableList().also { it[index] = provider }
            } else {
                providers + provider
            }
            copy(providers = next, editor = null)
        }
        viewModelScope.launch { providerRepository.upsertProvider(provider) }
        sendEvent(ProviderEvent.ShowToast(R.string.provider_saved_toast))
    }

    /**
     * Probes the draft endpoint with the first configured model — the check is
     * deliberately run against unsaved credentials so the user can verify
     * before committing. Nothing is persisted here.
     */
    private fun handleTestConnectionClicked() {
        val editor = state.editor ?: return
        if (editor.isProbing) return
        val baseUrl = editor.baseUrl.trim()
        val apiKey = editor.apiKey.trim()
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            sendEvent(ProviderEvent.ShowToast(R.string.provider_fetch_needs_endpoint_toast))
            return
        }
        val modelId = editor.models.firstOrNull()?.modelId
        if (modelId == null) {
            sendEvent(ProviderEvent.ShowToast(R.string.provider_test_needs_model_toast))
            return
        }
        updateEditor { copy(isProbing = true) }
        val probe = ProviderConfig(
            id = editor.id ?: "",
            name = editor.name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiType = editor.apiType,
        )
        viewModelScope.launch {
            val result = providerProbe.probe(probe, modelId)
            sendAction(ProviderAction.Internal.ProbeFinished(result))
        }
    }

    private fun handleProbeFinished(action: ProviderAction.Internal.ProbeFinished) {
        updateEditor { copy(isProbing = false) }
        sendEvent(
            when (val result = action.result) {
                is ProbeResult.Success -> ProviderEvent.ShowToast(
                    R.string.provider_test_success_toast,
                    listOf(result.reply.abbreviated(ProbeReplyMaxLength)),
                )
                is ProbeResult.Failure -> ProviderEvent.ShowToast(
                    R.string.provider_test_failed_toast,
                    listOf(result.detail),
                )
            }
        )
    }

    // endregion

    // region Model action handlers

    private fun handleFetchModelsClicked() {
        val editor = state.editor ?: return
        val baseUrl = editor.baseUrl.trim()
        val apiKey = editor.apiKey.trim()
        // A catalog request needs the endpoint credentials first.
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            sendEvent(ProviderEvent.ShowToast(R.string.provider_fetch_needs_endpoint_toast))
            return
        }
        updateEditor { copy(modelSheet = ModelSheetState.Fetching) }
        val probe = ProviderConfig(
            id = editor.id ?: "",
            name = editor.name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiType = editor.apiType,
        )
        viewModelScope.launch {
            // 先取目录（本地表，跟端点无关）：列表拿它显示名字，选中/手打 id 时拿它填表单。
            // 放在 fetch 之前 —— 这样端点取列表失败、转去"自定义模型"时也照样有得填。
            catalog = loadCatalog(editor.id)
            val result = runCatching { providerRepository.fetchModels(probe) }
            sendAction(
                result.fold(
                    onSuccess = { modelIds ->
                        ProviderAction.Internal.ModelsFetched(
                            modelIds.map { ModelSheetItem(modelId = it, name = catalog[it]?.name) }
                        )
                    },
                    onFailure = { ProviderAction.Internal.ModelsFetchFailed },
                )
            )
        }
    }

    /**
     * 核心目录：`线上 id → 目录条目`。只用来填表单 / 显示名字 —— 不落盘，也不参与请求。
     * 认不出这家供应商（或读取失败）就是空的，界面按"没有目录"处理，绝不猜值。
     */
    private var catalog: Map<String, CatalogModel> = emptyMap()

    /**
     * 目录里认得这个 id 吗：先按原样找，再按 [catalogKey] 归一化后的键找一遍 —— 网关的 id 带
     * `厂商/` 前缀与 `:变体` 后缀（免费档才有那截后缀），目录里的键是裸 id，归一化后才对得上。
     */
    private fun knownModel(modelId: String): CatalogModel? =
        catalog[modelId] ?: catalog[catalogKey(modelId)]

    private suspend fun loadCatalog(providerId: String?): Map<String, CatalogModel> =
        runCatching { providerRepository.catalog(providerId.orEmpty()) }
            .getOrDefault(emptyList())
            .associateBy { it.modelId }

    private fun handleModelEditClicked(action: ProviderAction.ModelEditClicked) {
        val model = state.editor?.models?.firstOrNull { it.id == action.id } ?: return
        updateEditor {
            copy(
                modelEditor = ModelEditorState(
                    id = model.id,
                    modelId = model.modelId,
                    contextLength = model.contextLength.takeIf { it > 0 }?.toString() ?: "",
                    maxOutputLength = model.maxOutputLength.takeIf { it > 0 }?.toString() ?: "",
                    reasoningEffort = model.reasoningEffort,
                    isCatalogModel = knownModel(model.modelId.trim()) != null,
                ),
            )
        }
    }

    private fun handleModelDeleteClicked(action: ProviderAction.ModelDeleteClicked) {
        updateEditor { copy(models = models.filterNot { it.id == action.id }) }
    }

    private fun handleModelSaveClicked() {
        val editor = state.editor ?: return
        val modelEditor = editor.modelEditor ?: return
        val modelId = modelEditor.modelId.trim()
        if (modelId.isEmpty()) {
            sendEvent(ProviderEvent.ShowToast(R.string.provider_model_id_required_toast))
            return
        }
        val id = modelEditor.id ?: UUID.randomUUID().toString()
        val model = ModelConfig(
            id = id,
            modelId = modelId,
            contextLength = modelEditor.contextLength.toIntOrNull() ?: 0,
            maxOutputLength = modelEditor.maxOutputLength.toIntOrNull() ?: 0,
            // 名字这一页不改（界面上没有那一行），存着的值原样带回去，别把它抹了。
            name = editor.models.firstOrNull { it.id == id }?.name.orEmpty(),
            // 默认档只在目录外的模型上有意义；目录模型这一页不显示它，值也已经被清空。
            reasoningEffort = modelEditor.reasoningEffort,
        )
        updateEditor {
            val index = models.indexOfFirst { it.id == model.id }
            val next = if (index >= 0) {
                models.toMutableList().also { it[index] = model }
            } else {
                models + model
            }
            copy(models = next, modelEditor = null)
        }
    }

    // endregion

    /** Mutates the open editor draft; no-op when the list mode is showing. */
    private inline fun updateEditor(block: ProviderEditorState.() -> ProviderEditorState) {
        val editor = state.editor ?: return
        updateState { copy(editor = editor.block()) }
    }

    /** Mutates the open model editor; no-op when the sheet is not showing. */
    private inline fun updateModelEditor(block: ModelEditorState.() -> ModelEditorState) {
        val editor = state.editor ?: return
        val modelEditor = editor.modelEditor ?: return
        updateState { copy(editor = editor.copy(modelEditor = modelEditor.block())) }
    }

    /** Single mutation point of [mutableStateFlow] (mirrors MainViewModel's helper). */
    private inline fun updateState(block: ProviderState.() -> ProviderState) {
        mutableStateFlow.update(block)
    }
}

/** 目录给的上下文容量；目录里没有这个模型、或它没声明容量时都是空串（= 不填）。 */
private fun CatalogModel?.catalogContextLength(): String =
    this?.contextLength?.takeIf { it > 0 }?.toString().orEmpty()

/** Keeps a model reply short enough to read inside a toast. */
private const val ProbeReplyMaxLength = 60

/** Truncates with an ellipsis when [this] exceeds [max] characters. */
private fun String.abbreviated(max: Int): String =
    if (length <= max) this else take(max - 1) + "…"
