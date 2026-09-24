package com.lhzkml.jasmine.core.agent.di

import android.content.Context
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.appsearch.AppSearchMemoryService
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.tools.BaseTool
import com.lhzkml.jasmine.core.agent.AdkAgentChat
import com.lhzkml.jasmine.core.agent.AdkConversationStore
import com.lhzkml.jasmine.core.agent.AdkProviderProbe
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.agent.openAiModelFor
import com.lhzkml.jasmine.core.agent.tools.JasmineTools
import com.lhzkml.jasmine.core.agent.tools.generatedTools
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Wires the agent layer into the graph — the composition root for the model
 * factory, the session store and the tool set, so [AdkProviderProbe] and
 * [AdkAgentChat] stay free of transport/storage wiring (and testable with fakes).
 *
 * Other dependencies come from sibling core modules: the shared [OkHttpClient]
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
     * ADK's own Room-backed session store (it ships in ADK core's Android
     * variant). This is what makes a conversation survive process death: the
     * session the runner reads its context from is no longer thrown away with
     * the process, so a resumed conversation does not have to replay its whole
     * transcript into a fresh in-memory session.
     *
     * Singleton because it owns one SQLite database; `fromContext` applies the
     * application context internally, so holding it cannot leak an Activity.
     */
    @Provides
    @Singleton
    fun provideSessionService(@ApplicationContext context: Context): SessionService =
        RoomSessionService.fromContext(context)

    /**
     * The conversation history the UI reads, backed by the same session store the agent
     * runs on — so a conversation exists once, not once per layer.
     */
    @Provides
    @Singleton
    fun provideConversationStore(sessionService: SessionService): ConversationStore =
        AdkConversationStore(sessionService)

    /**
     * ADK's AppSearch-backed long-term memory (it ships in ADK core's Android
     * variant, along with the `appsearch-local-storage` backend it needs).
     *
     * Persistent, unlike the `InMemoryMemoryService` ADK would otherwise default to
     * — which matters because memory exists to outlive the process. Singleton: it
     * owns one AppSearch database, and `fromContext` applies the application context
     * internally.
     */
    @Provides
    @Singleton
    fun provideMemoryService(@ApplicationContext context: Context): MemoryService =
        AppSearchMemoryService.fromContext(context)

    /**
     * Deliberately **not** `@Singleton`: an [AgentChat] owns a live conversation
     * (runner plus the attached session), so each conversation owner gets its own
     * instance and cannot inherit another one's history.
     *
     * The app's own tools arrive as one [JasmineTools] instance whose `generatedTools()`
     * accessor ADK's KSP processor emitted; ADK's own tools are contributed to the
     * [BaseTool] set with `@Provides @IntoSet` (see [AgentToolsModule]).
     */
    @Provides
    fun provideAgentChat(
        sessionService: SessionService,
        memoryService: MemoryService,
        okHttpClient: OkHttpClient,
        dispatcherManager: DispatcherManager,
        jasmineTools: JasmineTools,
        adkTools: Set<@JvmSuppressWildcards BaseTool>,
    ): AgentChat = AdkAgentChat(
        sessionService = sessionService,
        modelFactory = modelFactory(okHttpClient, dispatcherManager),
        tools = jasmineTools.generatedTools() + adkTools,
        memoryService = memoryService,
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
