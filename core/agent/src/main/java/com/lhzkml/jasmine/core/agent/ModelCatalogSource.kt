package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ModelCatalog
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.jasmine_ffi.listModels

/**
 * [ModelCatalog] 的 Rust 实现：这次请求由核心发出，各家的响应形状由核心的预设适配，这一层只把结果搬
 * 过边界。
 *
 * 核心那边的调用是同步的（自己开运行时），所以挂到 [Dispatchers.IO]。
 */
@Singleton
class RustModelCatalog @Inject constructor() : ModelCatalog {

    override suspend fun list(provider: ProviderConfig): List<String> =
        withContext(Dispatchers.IO) { listModels(provider.toProviderInput()) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelCatalogModule {

    @Binds
    abstract fun bindModelCatalog(impl: RustModelCatalog): ModelCatalog
}
