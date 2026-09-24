package com.lhzkml.jasmine.core.agent

import com.lhzkml.jasmine.core.data.model.ProviderConfig

/**
 * Outcome of a provider connectivity probe.
 *
 * [Failure.detail] carries the raw technical reason (HTTP status, transport
 * error, provider error message) so the caller can surface it verbatim; it is
 * deliberately not a string resource because this layer has no UI.
 */
sealed interface ProbeResult {
    /** The provider answered; [reply] is its (trimmed) text content. */
    data class Success(val reply: String) : ProbeResult

    /** The round trip failed; [detail] explains why. */
    data class Failure(val detail: String) : ProbeResult
}

/**
 * Narrow, UI-agnostic entry point of the agent layer.
 *
 * The only capability the app needs today is "can this provider actually
 * answer with this model" — so this facade exposes exactly that, and keeps the
 * ADK types (and the `Model` adapter) an implementation detail of this module.
 * A chat/runner API should be added here when a screen actually consumes it,
 * rather than speculatively now.
 */
interface ProviderProbe {
    /**
     * Sends one minimal request to [provider] using [modelId] and reports whether
     * a usable answer came back.
     *
     * Never throws for transport/provider errors — those come back as
     * [ProbeResult.Failure]; only coroutine cancellation propagates.
     */
    suspend fun probe(provider: ProviderConfig, modelId: String): ProbeResult
}
