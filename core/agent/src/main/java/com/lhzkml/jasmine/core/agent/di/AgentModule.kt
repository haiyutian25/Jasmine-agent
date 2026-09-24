package com.lhzkml.jasmine.core.agent.di

import android.content.Context
import com.google.adk.kt.models.Model
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.lhzkml.jasmine.core.agent.AdkAgentChat
import com.lhzkml.jasmine.core.agent.AdkProviderProbe
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.agent.openAiModelFor
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
 * factory and the session store, so [AdkProviderProbe] and [AdkAgentChat] stay
 * free of transport/storage wiring (and testable with fakes).
 *
 * Both other dependencies come from sibling core modules: the shared
 * [OkHttpClient] (`core:network`) and the injectable [DispatcherManager]
 * (`core:data`) — the agent layer therefore never touches `Dispatchers`
 * directly, matching the project-wide threading convention.
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
     * Deliberately **not** `@Singleton`: an [AgentChat] owns a live conversation
     * (runner plus the attached session), so each conversation owner gets its own
     * instance and cannot inherit another one's history.
     */
    @Provides
    fun provideAgentChat(
        sessionService: SessionService,
        okHttpClient: OkHttpClient,
        dispatcherManager: DispatcherManager,
    ): AgentChat = AdkAgentChat(
        sessionService = sessionService,
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
