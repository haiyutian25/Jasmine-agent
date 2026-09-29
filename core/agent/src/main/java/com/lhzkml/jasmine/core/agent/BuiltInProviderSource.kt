package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.BuiltInProviders
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import uniffi.jasmine_ffi.ProviderInput
import uniffi.jasmine_ffi.builtInProviders
import uniffi.jasmine_model_provider_info.WireApi

/**
 * [BuiltInProviders] 的 Rust 实现：清单来自核心的预设（`rust/model-provider-info/src/presets`），这一层
 * 只把边界上的 record 翻成界面用的 [ProviderConfig]。
 *
 * 核心那边的调用是同步的，返回的是一份很小的常量表，所以不必挂到 IO 上。
 */
@Singleton
class RustBuiltInProviders @Inject constructor() : BuiltInProviders {

    override fun list(): List<ProviderConfig> =
        builtInProviders().map { it.toBuiltInProviderConfig() }
}

/** 边界上的 record → 界面配置。预设按定义都是内置的，所以这里把它标成 [ProviderConfig.isBuiltIn]。 */
private fun ProviderInput.toBuiltInProviderConfig(): ProviderConfig =
    ProviderConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        apiType = when (wireApi) {
            WireApi.CHAT -> ProviderApiType.CHAT_COMPLETIONS
            WireApi.RESPONSES -> ProviderApiType.RESPONSES
        },
        isBuiltIn = true,
    )

@Module
@InstallIn(SingletonComponent::class)
abstract class BuiltInProviderModule {

    @Binds
    abstract fun bindBuiltInProviders(impl: RustBuiltInProviders): BuiltInProviders
}
