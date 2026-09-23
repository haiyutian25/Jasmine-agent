package com.lhzkml.jasmine.core.agent.di

import com.lhzkml.jasmine.core.agent.AdkAgentChat
import com.lhzkml.jasmine.core.agent.AdkProviderProbe
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.agent.openAiModelFor
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.google.adk.kt.models.Model
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Wires the agent layer into the graph — the composition root for the model
 * factory, so [AdkProviderProbe] and [AdkAgentChat] stay free of transport
 * wiring (and testable with a fake [Model]).
 *
 * Both dependencies come from other core modules: the shared [OkHttpClient]
 * (`core:network`) and the injectable [DispatcherManager] (`core:data`) — the
 * agent layer therefore never touches `Dispatchers` directly, matching the
 * project-wide threading convention.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    /** Stateless, so a single instance is shared. */
    @Provides
    @Singleton
    fun provideProviderProbe(
        okHttpClient: OkHttpClient,
        dispatcherManager: DispatcherManager,
    ): ProviderProbe = AdkProviderProbe(
        modelFactory = modelFactory(okHttpClient, dispatcherManager),
    )

    /**
     * Deliberately **not** `@Singleton`: an [AgentChat] owns a live conversation
     * (ADK session + runner), so each conversation owner gets its own instance
     * and cannot inherit another one's history.
     */
    @Provides
    fun provideAgentChat(
        okHttpClient: OkHttpClient,
        dispatcherManager: DispatcherManager,
    ): AgentChat = AdkAgentChat(
        modelFactory = modelFactory(okHttpClient, dispatcherManager),
    )

    private fun modelFactory(
        okHttpClient: OkHttpClient,
        dispatcherManager: DispatcherManager,
    ): (ProviderConfig, String) -> Model = { provider, modelId ->
        openAiModelFor(
            provider = provider,
            modelId = modelId,
            httpClient = okHttpClient,
            ioDispatcher = dispatcherManager.io,
        )
    }
}
