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

package com.lhzkml.jasmine.core.widgets.surface

import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsContentColor
import com.lhzkml.jasmine.core.ui.theme.contentColorFor
import com.lhzkml.jasmine.core.widgets.interactive.minimumInteractiveComponentSize
import com.lhzkml.jasmine.core.widgets.internal.childSemantics
import com.lhzkml.jasmine.core.widgets.ripple.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isContainer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.widgets.tokens.applyTonalElevation

// TODO: Add url to spec on Material.io.
/**
 * Material surface is the central metaphor in material design. Each surface exists at a given
 * elevation, which influences how that piece of surface visually relates to other surfaces and how
 * that surface is modified by tonal variance.
 *
 * See the other overloads for clickable, selectable, and toggleable surfaces.
 *
 * The Surface is responsible for:
 * 1) Clipping: Surface clips its children to the shape specified by [shape]
 * 2) Borders: If [shape] has a border, then it will also be drawn.
 * 3) Background: Surface fills the shape specified by [shape] with the [color]. If [color] is
 *    `CssVariables.card` a color overlay will be applied. The color of the overlay depends on the
 *    [tonalElevation] of this Surface, and the [LocalAbsoluteTonalElevation] set by any parent
 *    surfaces. This ensures that a Surface never appears to have a lower elevation overlay than its
 *    ancestors, by summing the elevation of all previous Surfaces.
 * 4) Content color: Surface uses [contentColor] to specify a preferred color for the content of
 *    this surface - this is used by the [Text] and [Icon] components as a default color.
 *
 * If no [contentColor] is set, this surface will try and match its background color to a color
 * defined in the theme `CssVariables`, and return the corresponding content color. For example, if
 * the [color] of this surface is `CssVariables.card`, [contentColor] will be set to
 * `CssVariables.cardForeground`. If [color] is not part of the theme palette, [contentColor] will keep
 * the same value set above this Surface.
 *
 * To manually retrieve the content color inside a surface, use [LocalWidgetsContentColor].
 * 5) Blocking touch propagation behind the surface.
 *
 * @param modifier Modifier to be applied to the layout corresponding to the surface
 * @param shape Defines the surface's shape as well its shadow.
 * @param color The background color. Use [Color.Transparent] to have no color.
 * @param contentColor The preferred content color provided by this Surface to its children.
 *   Defaults to either the matching content color for [color], or if [color] is not a color from
 *   the theme, this will keep the same value set above this Surface.
 * @param tonalElevation When [color] is `CssVariables.card`, a higher the elevation will result
 *   in a darker color in light theme and lighter color in dark theme.
 * @param shadowElevation The size of the shadow below the surface. To prevent shadow creep, only
 *   apply shadow elevation when absolutely necessary, such as when the surface requires visual
 *   separation from a patterned background. Note that It will not affect z index of the Surface. If
 *   you want to change the drawing order you can use `Modifier.zIndex`.
 * @param border Optional border to draw on top of the surface
 * @param content The content to be displayed on this Surface
 */
@Composable
@NonRestartableComposable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = LocalCssVariables.current.card,
    contentColor: Color = LocalCssVariables.current.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation
    CompositionLocalProvider(
        LocalWidgetsContentColor provides contentColor,
        LocalAbsoluteTonalElevation provides absoluteElevation,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = shape,
                        backgroundColor =
                            surfaceColorAtElevation(color = color, elevation = absoluteElevation),
                        border = border,
                        shadowElevation = with(LocalDensity.current) { shadowElevation.toPx() },
                    )
                    .semantics(mergeDescendants = false) {
                        // TODO: replace `isContainer` with `isTraversalGroup` with new
                        // pruning API.
                        @Suppress("DEPRECATION")
                        isContainer = true
                    }
                    .pointerInput(Unit) {},
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/**
 * Material surface is the central metaphor in material design. Each surface exists at a given
 * elevation, which influences how that piece of surface visually relates to other surfaces and how
 * that surface is modified by tonal variance.
 *
 * This version of Surface is responsible for a click handling as well as everything else that a
 * regular Surface does:
 *
 * This clickable Surface is responsible for:
 * 1) Clipping: Surface clips its children to the shape specified by [shape]
 * 2) Borders: If [shape] has a border, then it will also be drawn.
 * 3) Background: Surface fills the shape specified by [shape] with the [color]. If [color] is
 *    `CssVariables.card` a color overlay may be applied. The color of the overlay depends on the
 *    [tonalElevation] of this Surface, and the [LocalAbsoluteTonalElevation] set by any parent
 *    surfaces. This ensures that a Surface never appears to have a lower elevation overlay than its
 *    ancestors, by summing the elevation of all previous Surfaces.
 * 4) Content color: Surface uses [contentColor] to specify a preferred color for the content of
 *    this surface - this is used by the [Text] and [Icon] components as a default color. If no
 *    [contentColor] is set, this surface will try and match its background color to a color defined
 *    in the theme `CssVariables`, and return the corresponding content color. For example, if the
 *    [color] of this surface is `CssVariables.card`, [contentColor] will be set to
 *    `CssVariables.cardForeground`. If [color] is not part of the theme palette, [contentColor] will keep
 *    the same value set above this Surface.
 * 5) Click handling. This version of surface will react to the clicks, calling [onClick] lambda,
 *    updating the [interactionSource] when [PressInteraction] occurs, and showing ripple indication
 *    in response to press events. If you don't need click handling, consider using the Surface
 *    function that doesn't require [onClick] param. If you need to set a custom label for the
 *    [onClick], apply a `Modifier.semantics { onClick(label = "YOUR_LABEL", action = null) }` to
 *    the Surface.
 * 6) Semantics for clicks. Just like with [Modifier.clickable], clickable version of Surface will
 *    produce semantics to indicate that it is clicked. No semantic role is set by default, you may
 *    specify one by passing a desired [Role] with a [Modifier.semantics].
 *
 * To manually retrieve the content color inside a surface, use [LocalWidgetsContentColor].
 *
 * @param onClick callback to be called when the surface is clicked
 * @param modifier Modifier to be applied to the layout corresponding to the surface
 * @param enabled Controls the enabled state of the surface. When `false`, this surface will not be
 *   clickable
 * @param shape Defines the surface's shape as well its shadow. A shadow is only displayed if the
 *   [tonalElevation] is greater than zero.
 * @param color The background color. Use [Color.Transparent] to have no color.
 * @param contentColor The preferred content color provided by this Surface to its children.
 *   Defaults to either the matching content color for [color], or if [color] is not a color from
 *   the theme, this will keep the same value set above this Surface.
 * @param border Optional border to draw on top of the surface
 * @param tonalElevation When [color] is `CssVariables.card`, a higher the elevation will result
 *   in a darker color in light theme and lighter color in dark theme.
 * @param shadowElevation The size of the shadow below the surface. Note that It will not affect z
 *   index of the Surface. If you want to change the drawing order you can use `Modifier.zIndex`.
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting [Interaction]s for this surface. You can use this to change the surface's appearance
 *   or preview the surface in different states. Note that if `null` is provided, interactions will
 *   still happen internally.
 * @param content The content to be displayed on this Surface
 */
@Composable
@NonRestartableComposable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = LocalCssVariables.current.card,
    contentColor: Color = LocalCssVariables.current.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation
    CompositionLocalProvider(
        LocalWidgetsContentColor provides contentColor,
        LocalAbsoluteTonalElevation provides absoluteElevation,
    ) {
        Box(
            modifier =
                modifier
                    .minimumInteractiveComponentSize()
                    .surface(
                        shape = shape,
                        backgroundColor =
                            surfaceColorAtElevation(color = color, elevation = absoluteElevation),
                        border = border,
                        shadowElevation = with(LocalDensity.current) { shadowElevation.toPx() },
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .childSemantics(),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/**
 * Material surface is the central metaphor in material design. Each surface exists at a given
 * elevation, which influences how that piece of surface visually relates to other surfaces and how
 * that surface is modified by tonal variance.
 *
 * This version of Surface is responsible for selecting its state as well as everything else a
 * regular Surface does; it is what the navigation drawer items are built on.
 *
 * @param selected whether or not this Surface is selected
 * @param onClick callback to be called when the surface is clicked
 * @param modifier Modifier to be applied to the layout corresponding to the surface
 * @param enabled Controls the enabled state of the surface. When `false`, this surface will not be
 *   clickable
 * @param shape Defines the surface's shape as well its shadow. A shadow is only displayed if the
 *   [tonalElevation] is greater than zero.
 * @param color The background color. Use [Color.Transparent] to have no color.
 * @param contentColor The preferred content color provided by this Surface to its children.
 * @param tonalElevation When [color] is `CssVariables.card`, a higher the elevation will result
 *   in a darker color in light theme and lighter color in dark theme.
 * @param shadowElevation The size of the shadow below the surface.
 * @param border Optional border to draw on top of the surface
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting [Interaction]s for this surface.
 * @param content The content to be displayed on this Surface
 */
@Composable
@NonRestartableComposable
fun Surface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = LocalCssVariables.current.card,
    contentColor: Color = LocalCssVariables.current.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation
    CompositionLocalProvider(
        LocalWidgetsContentColor provides contentColor,
        LocalAbsoluteTonalElevation provides absoluteElevation,
    ) {
        Box(
            modifier =
                modifier
                    .minimumInteractiveComponentSize()
                    .surface(
                        shape = shape,
                        backgroundColor =
                            surfaceColorAtElevation(color = color, elevation = absoluteElevation),
                        border = border,
                        shadowElevation = with(LocalDensity.current) { shadowElevation.toPx() },
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .childSemantics(),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}



/**
 * 上游（M3 1.4.0）的 `Surface` 有四个重载：无色 / onClick / selected / checked。本库此前只搬了前三个，
 * 为照搬 IconButton 家族（`IconToggleButton` 用的是 checked 版）补上第四个，实现与 selected 版逐行一致。
 */
@Composable
@NonRestartableComposable
fun Surface(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = LocalCssVariables.current.card,
    contentColor: Color = LocalCssVariables.current.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation
    CompositionLocalProvider(
        LocalWidgetsContentColor provides contentColor,
        LocalAbsoluteTonalElevation provides absoluteElevation,
    ) {
        Box(
            modifier =
                modifier
                    .minimumInteractiveComponentSize()
                    .surface(
                        shape = shape,
                        backgroundColor =
                            surfaceColorAtElevation(color = color, elevation = absoluteElevation),
                        border = border,
                        shadowElevation = with(LocalDensity.current) { shadowElevation.toPx() },
                    )
                    .toggleable(
                        value = checked,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        onValueChange = onCheckedChange,
                    )
                    .childSemantics(),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Stable
private fun Modifier.surface(
    shape: Shape,
    backgroundColor: Color,
    border: BorderStroke?,
    shadowElevation: Float,
) =
    this.then(
            if (shadowElevation > 0f) {
                Modifier.graphicsLayer(
                    shadowElevation = shadowElevation,
                    shape = shape,
                    clip = false,
                )
            } else {
                Modifier
            }
        )
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .background(color = backgroundColor, shape = shape)
        .clip(shape)

@Composable
private fun surfaceColorAtElevation(color: Color, elevation: Dp): Color =
    LocalCssVariables.current.applyTonalElevation(color, elevation)

/**
 * CompositionLocal containing the current absolute elevation provided by [Surface] components. This
 * absolute elevation is a sum of all the previous elevations. Absolute elevation is only used for
 * calculating surface tonal colors, and is *not* used for drawing the shadow in a [Surface].
 */
val LocalAbsoluteTonalElevation = compositionLocalOf { 0.dp }
