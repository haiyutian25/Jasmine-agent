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

// 本项目自有的组件代码（移植自 AndroidX Material3 对应源码后自行维护），不再跟随上游生成，可直接改。
// 上游把这两个扩展声明在 BottomSheetScaffold.kt 里（模态也要用）；我们删掉非模态后把它们挪到这个文件。

package com.lhzkml.jasmine.core.widgets.bottomsheet

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A [Modifier] that scales up the drawing layer on the Y axis in case the [SheetState]'s
 * anchoredDraggableState offset overflows below the min anchor coordinates. This modifier should be
 * applied to the container of a component that is being dragged up above the top.
 *
 * A [verticalScaleDown] should be applied to the content of the sheet to maintain the content
 * aspect ratio as the container scales up.
 *
 * @param state a [SheetState]
 * @see verticalScaleDown
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.verticalScaleUp(state: SheetState) = graphicsLayer {
    val offset = state.anchoredDraggableState.offset
    val anchor = state.anchoredDraggableState.anchors.minAnchor()
    val overflow = if (offset < anchor) anchor - offset else 0f
    scaleY = if (overflow > 0f) (size.height + overflow) / size.height else 1f
    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
}

/**
 * A [Modifier] that scales down the drawing layer on the Y axis in case the [SheetState]'s
 * anchoredDraggableState offset overflows below the min anchor coordinates. This modifier should be
 * applied to the content inside a component that was scaled up with a [verticalScaleUp] modifier.
 * It will ensure that the content maintains its aspect ratio as the container scales up.
 *
 * @param state a [SheetState]
 * @see verticalScaleUp
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.verticalScaleDown(state: SheetState) = graphicsLayer {
    val offset = state.anchoredDraggableState.offset
    val anchor = state.anchoredDraggableState.anchors.minAnchor()
    val overflow = if (offset < anchor) anchor - offset else 0f
    scaleY = if (overflow > 0f) 1 / ((size.height + overflow) / size.height) else 1f
    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
}
