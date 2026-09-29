package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ModelList
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
import uniffi.jasmine_ffi.providerCatalog

/**
 * [ModelList] 的 Rust 实现：这次请求由核心发出（各家响应形状的适配也在核心），这一层只把结果搬过边界。
 *
 * 核心那边的调用是同步的，所以挂到 [Dispatchers.IO]。
 */
@Singleton
class RustModelList @Inject constructor() : ModelList {

    override suspend fun fetch(provider: ProviderConfig): List<String> =
        withContext(Dispatchers.IO) { listModels(provider.toProviderInput()) }

    override suspend fun catalog(providerId: String): List<CatalogModel> =
        withContext(Dispatchers.IO) {
            providerCatalog(providerId).map { entry ->
                CatalogModel(
                    modelId = entry.modelId,
                    name = entry.name,
                    contextLength = entry.contextLength.toInt(),
                    levels = entry.levels,
                )
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelListModule {

    @Binds
    abstract fun bindModelList(impl: RustModelList): ModelList
}
