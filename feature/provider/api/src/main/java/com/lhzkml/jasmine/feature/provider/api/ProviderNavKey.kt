package com.lhzkml.jasmine.feature.provider.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation contract of the model-provider feature.
 *
 * Other modules only ever depend on this sealed hierarchy (never on the
 * implementation module) when they need to navigate into the provider flow.
 */
@Serializable
sealed interface ProviderNavKey : NavKey {

    /**
     * Provider management screen: DeepSeek preset + user-added
     * OpenAI-protocol providers (add / edit / delete, Chat Completions
     * or Responses API). Editing happens in-screen, driven by the
     * provider ViewModel state — no separate destination.
     */
    @Serializable
    data object ProviderList : ProviderNavKey
}
