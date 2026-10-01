package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.jasmine_ffi.AgentFailure
import uniffi.jasmine_ffi.probe as runProbe

/**
 * [ProviderProbe] backed by the Rust core: one minimal request, drained, and the settled reply
 * read from it.
 *
 * Stateless, so one instance serves the whole app. The core's call is synchronous, hence
 * [Dispatchers.IO].
 *
 * 核心跨边界报的是 [AgentFailure]（分型 + 一句可显示的原因，与 `listModels` 同一种形状）；
 * 这里把它翻成 [ProbeResult]，因为这条接口的契约是"传输 / 供应商错误不抛异常，只回一句原因"。
 */
object RustProviderProbe : ProviderProbe {

    override suspend fun probe(provider: ProviderConfig, modelId: String): ProbeResult =
        withContext(Dispatchers.IO) {
            try {
                ProbeResult.Success(runProbe(provider.toProviderInput(), modelId))
            } catch (failure: AgentFailure) {
                ProbeResult.Failure(failure.detail)
            }
        }
}
