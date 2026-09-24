package com.lhzkml.jasmine.core.agent.di

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GetUserChoiceTool
import com.google.adk.kt.tools.LoadMemoryTool
import com.google.adk.kt.tools.PreloadMemoryTool
import com.google.adk.kt.tools.RequestInputTool
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.agent.tools.JasmineTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * The tools the agent may call.
 *
 * Two sources:
 *
 * - [JasmineTools] — the app's own tools, declared as `@Tool`-annotated functions and
 *   turned into `FunctionTool`s by ADK's KSP processor. ADK derives the declarations,
 *   so nothing here describes one. [AgentModule] collects them via the generated
 *   `generatedTools()` accessor.
 * - [BaseTool] — ADK's own tools, contributed to the set directly because they *are*
 *   ADK types. These are the ones whose behaviour depends on ADK internals (see the
 *   long-running pair below).
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentToolsModule {

    /** One instance: it holds the conversation store the listing tool reads. */
    @Provides
    @Singleton
    fun provideJasmineTools(conversationStore: ConversationStore): JasmineTools =
        JasmineTools(conversationStore)

    /**
     * Lets the agent ask the user a question and wait for the answer. Long-running:
     * it returns without a result, the turn ends on the call, and the answer arrives
     * through `AgentChat.respondToPrompt`.
     */
    @Provides
    @IntoSet
    fun provideRequestInputTool(): BaseTool = RequestInputTool()

    /** Same pause/resume contract, but the answer is one of a fixed set of options. */
    @Provides
    @IntoSet
    fun provideGetUserChoiceTool(): BaseTool = GetUserChoiceTool()

    /**
     * Searches long-term memory on demand. The agent carries ADK's after-agent callback,
     * which ingests each finished session into the same store, so this can surface
     * something said in an earlier conversation.
     */
    @Provides
    @IntoSet
    fun provideLoadMemoryTool(): BaseTool = LoadMemoryTool()

    /**
     * The other half of the pair: instead of waiting to be asked, it searches memory
     * with the user's own message and appends what it finds to every request. It is
     * invisible to the model — `declaration()` returns null and it only rewrites the
     * request — so it never shows up as a callable tool.
     */
    @Provides
    @IntoSet
    fun providePreloadMemoryTool(): BaseTool = PreloadMemoryTool()
}
