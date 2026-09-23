package com.lhzkml.jasmine.core.data.repository

import com.lhzkml.jasmine.core.data.datastore.UserPreferencesDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.UserPreferences
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
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatcherManager.default)

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
}
