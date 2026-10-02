/*
 * Copyright 2021 The Android Open Source Project
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
// 上游位置：androidx/compose/material3/InteractiveComponentSize.kt。本库不再调用 M3 的
// `Modifier.minimumInteractiveComponentSize()` / `LocalMinimumInteractiveComponentSize`，改由本文件提供，
// 行为与取值逐项照抄（最小触控目标 48.dp；未启用时不做扩张；对齐线缓存逻辑一致）。
// 差异：① 上游那个 `identityHashCode` 来自 material3.internal（跨模块取不到），这里直接用 JVM 的
// `System.identityHashCode`，语义相同；② 上游的 `LocalMinimumInteractiveComponentEnforcement` 已废弃
// （WARNING 级，官方让改用 `LocalMinimumInteractiveComponentSize`），按本库惯例不搬。

package com.lhzkml.jasmine.core.widgets.interactive

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reserves at least 48.dp in size to disambiguate touch interactions if the element would measure
 * smaller.
 *
 * This uses the Material recommended minimum size of 48.dp x 48.dp, which may not the same as the
 * system enforced minimum size. The minimum clickable / touch target size (48.dp by default) is
 * controlled by the system via `ViewConfiguration` and automatically expanded at the touch input
 * layer.
 *
 * This modifier is not needed for touch target expansion to happen. It only affects layout, to make
 * sure there is adequate space for touch target expansion.
 *
 * Because layout constraints are affected by modifier order, for this modifier to take effect, it
 * must come before any size modifiers on the element that might limit its constraints.
 *
 * @see LocalMinimumInteractiveComponentSize
 */
@Stable
fun Modifier.minimumInteractiveComponentSize(): Modifier = this then MinimumInteractiveModifier

internal object MinimumInteractiveModifier : ModifierNodeElement<MinimumInteractiveModifierNode>() {

    override fun create(): MinimumInteractiveModifierNode = MinimumInteractiveModifierNode()

    override fun update(node: MinimumInteractiveModifierNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "minimumInteractiveComponentSize"
        properties["README"] =
            "Reserves at least 48.dp in size to disambiguate touch " +
                "interactions if the element would measure smaller"
    }

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?) = (other === this)
}

@Suppress("PrimitiveInCollection")
internal class MinimumInteractiveModifierNode :
    Modifier.Node(), CompositionLocalConsumerModifierNode, LayoutModifierNode {

    private var alignmentLinesCache: MutableMap<AlignmentLine, Int>? = null

    @Suppress("PrimitiveInCollection")
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val size = currentValueOf(LocalMinimumInteractiveComponentSize).coerceAtLeast(0.dp)
        val placeable = measurable.measure(constraints)
        val enforcement = isAttached && (size.isSpecified && size > 0.dp)

        val sizePx = if (size.isSpecified) size.roundToPx() else 0
        // Be at least as big as the minimum dimension in both dimensions
        val width =
            if (enforcement) {
                maxOf(placeable.width, sizePx)
            } else {
                placeable.width
            }
        val height =
            if (enforcement) {
                maxOf(placeable.height, sizePx)
            } else {
                placeable.height
            }

        if (enforcement) {
            updateAlignmentLines(sizePx, placeable)
        }

        return layout(
            width = width,
            height = height,
            alignmentLines = alignmentLinesCache ?: emptyMap(),
        ) {
            val centerX = ((width - placeable.width) / 2f).roundToInt()
            val centerY = ((height - placeable.height) / 2f).roundToInt()
            placeable.place(centerX, centerY)
        }
    }

    /**
     * Updates the alignment lines cache based on the enforcement of minimum interactive size and
     * the measured size of the placeable.
     */
    private fun updateAlignmentLines(sizePx: Int, placeable: Placeable) {
        val cache = getAlignmentLinesCache()
        cache[MinimumInteractiveLeftAlignmentLine] =
            ((sizePx - placeable.width) / 2f).fastRoundToInt().coerceAtLeast(0)
        cache[MinimumInteractiveTopAlignmentLine] =
            ((sizePx - placeable.height) / 2f).fastRoundToInt().coerceAtLeast(0)
    }

    /**
     * Returns a [MutableMap] that will act as a cache of alignment lines.
     */
    private fun getAlignmentLinesCache(): MutableMap<AlignmentLine, Int> =
        alignmentLinesCache
            ?: LinkedHashMap<AlignmentLine, Int>(2).also { alignmentLinesCache = it }
}

internal val MinimumInteractiveTopAlignmentLine = HorizontalAlignmentLine(::min)
internal val MinimumInteractiveLeftAlignmentLine = VerticalAlignmentLine(::min)

/**
 * CompositionLocal that configures the minimum touch target size for Material components (such as
 * Button) to ensure they are accessible. If a component has a visual size that is lower than the
 * minimum touch target size, extra space outside the component will be included. If set to 0.dp,
 * there will be no extra space.
 */
val LocalMinimumInteractiveComponentSize: ProvidableCompositionLocal<Dp> =
    staticCompositionLocalOf {
        48.dp
    }
