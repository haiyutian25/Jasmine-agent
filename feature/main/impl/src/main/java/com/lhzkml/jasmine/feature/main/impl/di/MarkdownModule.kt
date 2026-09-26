package com.lhzkml.jasmine.feature.main.impl.di

import com.lhzkml.jasmine.core.markdown.DefaultMarkdownParserFactory
import com.lhzkml.jasmine.core.markdown.MarkdownParserFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 流式 Markdown 解析器的绑定。
 *
 * 生产侧只有 JNI 一种实现（[DefaultMarkdownParserFactory]），所以这里是 `@Provides`
 * 而不是 `@Binds`：`@Binds` 需要一个实现类，而工厂本身就是实现。
 *
 * 绑在消费方（聊天界面）而不是 `core:markdown`：那边是纯渲染库，没有 Hilt，
 * 也不该为了一个绑定把 DI 拖进渲染层。
 */
@Module
@InstallIn(SingletonComponent::class)
object MarkdownModule {

    @Provides
    fun provideMarkdownParserFactory(): MarkdownParserFactory = DefaultMarkdownParserFactory
}
