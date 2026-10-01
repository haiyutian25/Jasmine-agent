package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.jasmine_ffi.AgentFailure
import uniffi.jasmine_ffi.ConversationSummary

/**
 * 会话列表**读失败**时的行为（G6，D5 的不变量）。
 *
 * 这是用户可见的一条：目录一时读不出来（文件被占用、磁盘抖了一下、核心正好在写），
 * 界面**不能**把用户的全部历史变成一个空列表 —— 他会以为记录全没了。上一次成功的快照要留着，
 * 只把原因交出去（`readFailures`，界面据此提示）。
 *
 * 之所以测这个纯函数而不是 `RustConversationStore`：真实那条路要一个真的 `AgentHandle`
 * （JNI + `.so`），纯 JVM 单测起不来。判定逻辑本身不依赖句柄，所以提出来单独钉住。
 */
class RustConversationReadTest {

    /** 读成功：换上新快照，没有失败上报。 */
    @Test
    fun `a successful read replaces the snapshot and reports nothing`() {
        val read = applyConversationRead(
            previous = listOf(conversation("旧会话")),
            result = Result.success(listOf(summary("s-1", "新会话", updatedAt = 42L))),
        )

        assertEquals(
            listOf(
                Conversation(
                    id = "s-1",
                    title = "新会话",
                    providerId = "deepseek",
                    modelId = "deepseek-chat",
                    createdAt = 42L,
                    updatedAt = 42L,
                )
            ),
            read.conversations,
        )
        assertNull("读成功不应该上报失败", read.failure)
    }

    /** 读失败：**保留**上一次的快照，只把原因交出去。 */
    @Test
    fun `a failed read keeps the previous snapshot and reports the reason`() {
        val previous = listOf(conversation("用户的历史"), conversation("另一条"))

        val read = applyConversationRead(
            previous = previous,
            result = Result.failure(AgentFailure.Transcript("会话目录读不出来")),
        )

        assertEquals("读失败不许把历史清空", previous, read.conversations)
        assertEquals(
            "原因要交出去（界面据此提示），实际是 ${read.failure}",
            AgentFailure.Transcript("会话目录读不出来").message,
            read.failure,
        )
    }

    /** 从空开始、且第一次就读失败：还是空，但照样要上报（不能当成"没有历史"）。 */
    @Test
    fun `a failed first read reports instead of pretending there is no history`() {
        val read = applyConversationRead(
            previous = emptyList(),
            result = Result.failure(AgentFailure.Transport("核心没起来")),
        )

        assertEquals(emptyList<Conversation>(), read.conversations)
        assertEquals(AgentFailure.Transport("核心没起来").message, read.failure)
    }

    /**
     * 没有 message 的异常也要有一句能显示的原因。
     *
     * `Throwable.message` 可以是 null，直接用它会让 `failure` 变成 null —— 那就等于"读成功了"，
     * 界面一句提示都不会出。
     */
    @Test
    fun `a throwable without a message still reports something`() {
        val read = applyConversationRead(
            previous = listOf(conversation("历史")),
            result = Result.failure(RuntimeException()),
        )

        assertEquals("历史", read.conversations.single().title)
        assertEquals("抛异常本身要能当原因", RuntimeException().toString(), read.failure)
    }

    private fun conversation(title: String) = Conversation(
        id = title,
        title = title,
        providerId = "deepseek",
        modelId = "deepseek-chat",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun summary(sessionId: String, title: String, updatedAt: Long) = ConversationSummary(
        sessionId = sessionId,
        title = title,
        providerId = "deepseek",
        modelId = "deepseek-chat",
        updatedAt = updatedAt,
    )
}
