package com.lhzkml.jasmine.core.agent

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [EventSink] 的不变量：**顺序不变、文本只合并、终态绝不丢**。
 *
 * 这些用例刻意用**小容量**通道把"满了"这一刻逼出来 —— 生产里那一瞬间要靠界面忙起来才会出现，
 * 在单测里没法靠等。
 */
class EventSinkTest {

    @Test
    fun `text deltas keep their order and merge only while the channel is full`() {
        val channel = Channel<ChatEvent>(capacity = 2)
        val sink = EventSink(channel)

        sink.offer(ChatEvent.Text("a"))
        sink.offer(ChatEvent.Text("b"))
        sink.offer(ChatEvent.Text("c")) // 满：进缓冲
        sink.offer(ChatEvent.Text("d")) // 继续并进缓冲
        assertEquals(ChatEvent.Text("a"), runBlocking { channel.receive() })
        sink.offer(ChatEvent.Text("e")) // 又有空位：cde 并成一条发出去

        assertEquals(
            listOf(ChatEvent.Text("b"), ChatEvent.Text("cde")),
            channel.drainAll(),
        )
    }

    @Test
    fun `a non-text event flushes the buffered text before itself`() {
        val channel = Channel<ChatEvent>(capacity = 3)
        val sink = EventSink(channel)

        sink.offer(ChatEvent.Text("a"))
        sink.offer(ChatEvent.Text("b"))
        sink.offer(ChatEvent.Text("c"))
        sink.offer(ChatEvent.Text("d")) // 满：进缓冲
        assertEquals(ChatEvent.Text("a"), runBlocking { channel.receive() })
        assertEquals(ChatEvent.Text("b"), runBlocking { channel.receive() })

        sink.offer(ChatEvent.ToolCall("run", "{}"))

        assertEquals(
            listOf(
                ChatEvent.Text("c"),
                ChatEvent.Text("d"), // 工具调用之前的增量必须先出去
                ChatEvent.ToolCall("run", "{}"),
            ),
            channel.drainAll(),
        )
    }

    @Test
    fun `buffered reasoning is flushed ahead of buffered text`() {
        val channel = Channel<ChatEvent>(capacity = 3)
        val sink = EventSink(channel)

        sink.offer(ChatEvent.Text("a"))
        sink.offer(ChatEvent.Text("b"))
        sink.offer(ChatEvent.Text("c"))
        sink.offer(ChatEvent.Reasoning("r"))
        sink.offer(ChatEvent.Text("d"))
        assertEquals(ChatEvent.Text("a"), runBlocking { channel.receive() })
        assertEquals(ChatEvent.Text("b"), runBlocking { channel.receive() })

        sink.finish()

        assertEquals(
            listOf(ChatEvent.Text("c"), ChatEvent.Reasoning("r"), ChatEvent.Text("d")),
            channel.drainAll(),
        )
    }

    @Test
    fun `the terminal event is enqueued before the channel closes, and late events are ignored`() {
        val channel = Channel<ChatEvent>(capacity = 4)
        val sink = EventSink(channel)

        sink.offer(ChatEvent.Text("hi"))
        sink.offer(ChatEvent.Completed)

        assertEquals(listOf(ChatEvent.Text("hi"), ChatEvent.Completed), channel.drainAll())
        assertNull("终态之后通道应收口", channel.tryReceive().getOrNull())

        // 收口之后才到的回调不该抛：核心可能还有一条在路上。
        sink.offer(ChatEvent.Text("late"))
        sink.offer(ChatEvent.ToolCall("t", "{}"))
        sink.finish()
        assertNull(channel.tryReceive().getOrNull())
    }
}

/** 把通道里此刻积压的事件全部取出（通道还开着、且已排空时为真）。 */
private fun Channel<ChatEvent>.drainAll(): List<ChatEvent> {
    val drained = mutableListOf<ChatEvent>()
    while (true) {
        drained += tryReceive().getOrNull() ?: break
    }
    return drained
}
