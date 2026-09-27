package com.lhzkml.jasmine.core.agent.di

import android.content.Context
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.appsearch.AppSearchMemoryService
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.agent.RustAgentChat
import com.lhzkml.jasmine.core.agent.RustConversationStore
import com.lhzkml.jasmine.core.agent.RustProviderProbe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the agent layer into the graph — the composition root for the session
 * store and the agent's entry points, so the implementations stay free of
 * storage wiring (and testable with fakes).
 *
 * The engine behind both entry points is the Rust core, and so is the store: the
 * transcript lives in the core's own session files, and this file only hands over the
 * directory they live under.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    /** Stateless, so a single instance is shared. */
    @Provides
    @Singleton
    fun provideProviderProbe(): ProviderProbe = RustProviderProbe

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
     * The conversation history the UI reads, backed by the core's own session files — so a
     * conversation exists once, not once per layer.
     *
     * The core writes the transcript as each turn runs; this only reads it back. The handle it
     * builds is never attached, so a singleton is safe.
     */
    @Provides
    @Singleton
    fun provideConversationStore(@ApplicationContext context: Context): ConversationStore =
        RustConversationStore(sessionsDir(context))

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
     * Deliberately **not** `@Singleton`: an [AgentChat] owns a live conversation, so each
     * conversation owner gets its own instance and cannot inherit another one's history.
     *
     * The engine is the Rust core: it runs the turn loop, both wire protocols and the built-in
     * tools. What it needs from this side is the conversation's existing context, which it reads
     * through [ConversationStore] — so the transcript the UI shows and the context the model
     * continues from are the same one.
     */
    @Provides
    fun provideAgentChat(@ApplicationContext context: Context): AgentChat =
        RustAgentChat(sessionsDir(context))

    /**
     * Where the core keeps its session files: the app's own files directory, under which the
     * core creates its `sessions` tree.
     */
    private fun sessionsDir(context: Context): String = context.filesDir.absolutePath
    }
