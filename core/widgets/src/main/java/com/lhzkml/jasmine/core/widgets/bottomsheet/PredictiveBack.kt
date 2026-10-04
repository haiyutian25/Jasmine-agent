/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// 本项目自有的组件代码（移植自上游对应源码后自行维护），不再跟随上游生成，可直接改。

package com.lhzkml.jasmine.core.widgets.bottomsheet

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

internal sealed interface PredictiveBackState {
    val value: BackEventProgress
}

/**
 * The state describing a one-shot back state, with use in a [PredictiveBackStateHandler].
 *
 * Because the back handler can only be used once, there are three states that [BackEventProgress]
 * can be in:
 * - [NotRunning]
 * - [InProgress], which can happen on API 34 and above if a predictive back is in progress.
 * - [Completed]
 */
internal sealed interface BackEventProgress {
    /** There is no predictive back ongoing, and the back has not been completed. */
    object NotRunning : BackEventProgress

    /** There is an ongoing predictive back animation, with the given [progress]. */
    data class InProgress(
        val touchX: Float,
        val touchY: Float,
        val progress: Float,
        val swipeEdge: SwipeEdge,
    ) : BackEventProgress

    /** The back has completed. */
    object Completed : BackEventProgress
}

internal enum class SwipeEdge {
    Left,
    Right,
    None,
}

@Composable
internal fun rememberPredictiveBackState(): PredictiveBackState = remember {
    PredictiveBackStateImpl()
}

private class PredictiveBackStateImpl : PredictiveBackState {
    override var value: BackEventProgress by mutableStateOf(BackEventProgress.NotRunning)
}

@Composable
internal fun PredictiveBackStateHandler(
    state: PredictiveBackState,
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    // Safely update the current `onBack` lambda when a new one is provided
    val currentOnBack by rememberUpdatedState(onBack)

    key(state) {
        state as PredictiveBackStateImpl
        PredictiveBackHandler(enabled = enabled && state.value !is BackEventProgress.Completed) {
            progress ->
            try {
                progress.collect { backEvent ->
                    state.value =
                        BackEventProgress.InProgress(
                            backEvent.touchX,
                            backEvent.touchY,
                            backEvent.progress,
                            when (backEvent.swipeEdge) {
                                BackEventCompat.EDGE_LEFT -> SwipeEdge.Left
                                BackEventCompat.EDGE_RIGHT -> SwipeEdge.Right
                                else -> SwipeEdge.None
                            },
                        )
                }
                state.value = BackEventProgress.Completed
                currentOnBack()
            } catch (e: CancellationException) {
                state.value = BackEventProgress.NotRunning
                throw e
            }
        }
    }
}

private val PredictiveBackEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal object PredictiveBack {
    internal fun transform(progress: Float) = PredictiveBackEasing.transform(progress)
}
