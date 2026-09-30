package com.lhzkml.jasmine.feature.main.impl

import com.lhzkml.jasmine.core.agent.AppUsage
import com.lhzkml.jasmine.core.agent.ConversationStore
import com.lhzkml.jasmine.core.data.model.Conversation
import com.lhzkml.jasmine.core.data.model.TranscriptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用量页的 UDF 行为（修复方案 §3 第 11 条）：状态只由 action 改，失败要可观察。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageStatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var store: FakeUsageStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = FakeUsageStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the first load lands through the action channel`() = runTest(testDispatcher) {
        val viewModel = UsageStatsViewModel(store)
        advanceUntilIdle()

        // 首屏那次扫描走的是 RefreshClicked → Loaded 这条回流路径。
        assertEquals(1, store.usageStatsCalls)
        assertFalse(viewModel.stateFlow.value.isLoading)
    }

    @Test
    fun `a failed load clears the loading flag and reports`() = runTest(testDispatcher) {
        store.failure = IllegalStateException("no")
        val viewModel = UsageStatsViewModel(store)
        val events = mutableListOf<UsageStatsEvent>()
        // 收集是长命的：必须挂 backgroundScope，否则 runTest 会等它（UncompletedCoroutinesError）。
        backgroundScope.launch { viewModel.eventFlow.collect { events += it } }
        advanceUntilIdle()

        // 失败也要可观察（以前这里什么都不发生，页面永远停在"统计中"）。
        assertFalse(viewModel.stateFlow.value.isLoading)
        assertTrue(events.single() is UsageStatsEvent.ShowError)
    }
}

private class FakeUsageStore : ConversationStore {
    override val conversationsStateFlow = MutableStateFlow<List<Conversation>>(emptyList())

    /** 让用例模拟"扫不出来"。 */
    var failure: Exception? = null

    var usageStatsCalls = 0

    override suspend fun usageStats(): AppUsage {
        usageStatsCalls++
        failure?.let { throw it }
        return AppUsage.Empty
    }

    override suspend fun refresh() = Unit
    override suspend fun latestConversation(): Conversation? = null
    override suspend fun interruptedTurn(conversationId: String): String? = null
    override suspend fun messagesOf(conversationId: String): List<TranscriptMessage> = emptyList()
    override suspend fun createConversation(
        providerId: String,
        modelId: String,
        title: String,
    ): Conversation = Conversation(
        id = "conv",
        title = title,
        providerId = providerId,
        modelId = modelId,
        createdAt = 0,
        updatedAt = 0,
    )

    override suspend fun deleteConversation(id: String) = Unit
}
