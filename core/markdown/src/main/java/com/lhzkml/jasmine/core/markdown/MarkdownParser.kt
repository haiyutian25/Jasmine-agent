package com.lhzkml.jasmine.core.markdown

import com.lhzkml.jasmine.core.markdown.model.MarkdownUpdate
import java.io.Closeable

/**
 * 流式 Markdown 解析端口。
 *
 * 消费方依赖本接口，而不是 [IncrementalMarkdownParser] 这个 JNI 类：[NativeBridge]
 * 在 `init` 里就 `System.loadLibrary`，纯 JVM 单测必然 `UnsatisfiedLinkError`，
 * 绑定到接口后测试可以换成纯 Kotlin 实现，而被测逻辑本身一行不用改。
 *
 * 一个实例 = 一条流的一次解析会话。native 句柄**不是线程安全的**，持有者必须保证
 * 同一时刻只有一个线程使用它（生产侧由 `ChatViewModel` 的单条解析协程保证）。
 */
interface MarkdownParser : Closeable {

    /**
     * 追加一段增量文本，返回本次增量结果。
     *
     * 与 [IncrementalMarkdownParser] 相同：文本以 UTF-8 传给 native。
     */
    fun append(chunk: String): MarkdownUpdate

    /** 清空解析状态但保留会话（整篇重解析时先 reset 再 append）。 */
    fun reset()

    /** 结束流式输入，返回收尾增量（未闭合块在此闭合）。 */
    fun finalizeStream(): MarkdownUpdate
}

/** 创建一条解析会话；每段流式回复各持有一个。 */
fun interface MarkdownParserFactory {
    fun create(): MarkdownParser
}

/**
 * 生产实现：走 JNI 的 [IncrementalMarkdownParser]。
 *
 * DI 侧绑它（见 `feature/main/impl` 的 `MarkdownModule`），测试侧绑纯 Kotlin 实现。
 */
val DefaultMarkdownParserFactory: MarkdownParserFactory =
    MarkdownParserFactory { IncrementalMarkdownParser() }
