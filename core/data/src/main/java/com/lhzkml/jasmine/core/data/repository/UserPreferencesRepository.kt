package com.lhzkml.jasmine.core.data.repository

import android.util.Log
import com.lhzkml.jasmine.core.data.datastore.UserPreferencesDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.UserPreferences
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Data-layer entry point for reading and updating persisted UI preferences.
 */
interface UserPreferencesRepository {
    /** Hot stream of the persisted preferences, started eagerly at injection time. */
    val preferencesStateFlow: StateFlow<UserPreferences>
    suspend fun updateTheme(themeId: String)
    suspend fun updateTypography(typographyChoice: String)
    suspend fun updateColorMode(colorMode: String)
    suspend fun updateFontScale(fontScale: Float)
    suspend fun updateActiveCustomFont(fontId: String)

    /** Points the chat at [providerId] / [modelId] (both empty clears the selection). */
    suspend fun updateActiveModel(providerId: String, modelId: String)

    /** 模型回复语言（见 [com.lhzkml.jasmine.core.data.model.AgentOutputLanguage]）；全局一个值。 */
    suspend fun updateAgentOutputLanguage(value: String)
}

/**
 * Preferences DataStore-backed implementation. Reads expose a [StateFlow]; each
 * update atomically edits the stored preferences.
 */
class UserPreferencesRepositoryImpl(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    dispatcherManager: DispatcherManager,
) : UserPreferencesRepository {

    // Long-lived repository scope on a deterministic dispatcher. A SupervisorJob
    // keeps a failed collection from killing the scope (and the StateFlow with it).
    //
    // 但 SupervisorJob 只保证兄弟协程不受牵连，**不接住异常** —— 根协程里逃出来的异常仍会走到
    // 线程默认处理器并杀掉进程（DataStore 文件损坏、IO 失败时上游 `store.data` 会抛）。
    // 这里降级成一条日志，别让"偏好读不出来"变成"应用起不来"。
    private val repositoryScope = CoroutineScope(
        SupervisorJob() +
            dispatcherManager.default +
            CoroutineExceptionHandler { _, error ->
                Log.w(TAG, "user preferences background work failed", error)
            }
    )

    override val preferencesStateFlow: StateFlow<UserPreferences> =
        userPreferencesDataStore
            .preferences
            .stateIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                initialValue = UserPreferences.DEFAULT,
            )

    override suspend fun updateTheme(themeId: String) =
        userPreferencesDataStore.update { it.copy(themeId = themeId) }

    override suspend fun updateTypography(typographyChoice: String) =
        userPreferencesDataStore.update { it.copy(typographyChoice = typographyChoice) }

    override suspend fun updateColorMode(colorMode: String) =
        userPreferencesDataStore.update { it.copy(colorMode = colorMode) }

    override suspend fun updateFontScale(fontScale: Float) =
        userPreferencesDataStore.update { it.copy(fontScale = fontScale) }

    override suspend fun updateActiveCustomFont(fontId: String) =
        userPreferencesDataStore.update { it.copy(activeCustomFontId = fontId) }

    override suspend fun updateActiveModel(providerId: String, modelId: String) =
        userPreferencesDataStore.update {
            it.copy(activeProviderId = providerId, activeModelId = modelId)
        }

    override suspend fun updateAgentOutputLanguage(value: String) =
        userPreferencesDataStore.update { it.copy(agentOutputLanguage = value) }

    private companion object {
        /** 只用于后台失败的日志归类；不参与任何权限/行为判断。 */
        const val TAG = "UserPreferencesRepo"
    }
}
