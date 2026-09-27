package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.jasmine_ffi.probe as runProbe
import uniffi.jasmine_protocol.ProbeResult as CoreProbeResult

/**
 * [ProviderProbe] backed by the Rust core: one minimal request, drained, and the settled reply
 * read from it.
 *
 * Stateless, so one instance serves the whole app. The core's call is synchronous, hence
 * [Dispatchers.IO]; failures come back as [ProbeResult.Failure] the same way the ADK-backed
 * implementation reports them.
 */
object RustProviderProbe : ProviderProbe {

    override suspend fun probe(provider: ProviderConfig, modelId: String): ProbeResult =
        withContext(Dispatchers.IO) {
            when (val result = runProbe(provider.toProviderInput(), modelId)) {
                is CoreProbeResult.Success -> ProbeResult.Success(result.reply)
                is CoreProbeResult.Failure -> ProbeResult.Failure(result.detail)
            }
        }
}
