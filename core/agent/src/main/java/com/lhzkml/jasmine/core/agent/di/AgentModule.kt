package com.lhzkml.jasmine.core.agent.di

import android.content.Context
import com.lhzkml.jasmine.core.agent.AgentChat
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.agent.DeviceClock
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
import uniffi.jasmine_ffi.AgentHandle

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
     * 进程内**唯一**的核心句柄（修复方案 D5）。
     *
     * 核心按会话 id 分槽（`SessionSlot`），本来就是为"多会话并存"设计的，所以共享一个句柄不会
     * 让会话互相污染 —— 反而修掉两个真问题：
     * 1. 以前 store 与每个 chat 各持一个 `AgentChatService`，实例间唯一共享介质是文件系统：
     *    store 删掉一条正在跑的会话时，chat 那侧的槽变成孤儿（文件没了，回合还在往里写）。
     * 2. 存储变更推送要挂在**写文件的**那个实例上才有意义 —— 两个实例就收不全。
     */
    @Provides
    @Singleton
    fun provideAgentHandle(@ApplicationContext context: Context): AgentHandle =
        AgentHandle(sessionsDir(context), DeviceClock)

    /**
     * The conversation history the UI reads, backed by the core's own session files — so a
     * conversation exists once, not once per layer.
     *
     * The core writes the transcript as each turn runs; this reads it back, and is woken by the
     * core's own store-changed signal instead of polling (D5).
     */
    @Provides
    @Singleton
    fun provideConversationStore(handle: AgentHandle): ConversationStore =
        RustConversationStore(handle)

    /**
     * Deliberately **not** `@Singleton`: each conversation owner gets its own thin wrapper. It is
     * only a facade over the shared [AgentHandle], and conversation isolation comes from the core's
     * per-session slots rather than from having separate services.
     */
    @Provides
    fun provideAgentChat(handle: AgentHandle): AgentChat = RustAgentChat(handle)

    /**
     * Where the core keeps its session files: the app's own files directory, under which the
     * core creates its `sessions` tree.
     */
    private fun sessionsDir(context: Context): String = context.filesDir.absolutePath
}
