package com.lhzkml.jasmine.core.ui.base.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lhzkml.jasmine.core.ui.base.BaseViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Convenience method for observing event flow from [BaseViewModel].
 *
 * Events are only consumed while the associated screen is resumed, so a screen
 * that is stopped or backgrounded cannot fire a duplicate navigation call or
 * toast; the pending event is delivered once the screen resumes.
 */
@Composable
fun <E> EventsEffect(
    viewModel: BaseViewModel<*, E, *>,
    lifecycleOwner: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    handler: (E) -> Unit,
) {
    LaunchedEffect(key1 = Unit) {
        viewModel
            .eventFlow
            .filter { lifecycleOwner.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            .onEach(handler)
            .launchIn(this)
    }
}
