/*
 * Copyright 2022 The Android Open Source Project
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
// 本项目自有的组件代码（移植自上游的 Menu.kt + AndroidMenu.android.kt 后自行维护），
// 不再跟随上游生成，可直接改。上游是 expect/actual 结构（公共面在 commonMain、实现在 androidMain），
// 本项目只跑 Android，故按惯例把两半合并到本文件：签名与**默认参数**取 common 那份，函数体取 android 那份。
// 只保留正式入口 DropdownMenu / DropdownMenuItem；两个只为二进制兼容的 HIDDEN 重载都没搬。

package com.lhzkml.jasmine.core.widgets.menu

import com.lhzkml.jasmine.core.widgets.motion.LocalMotionScheme

import android.graphics.Rect as ViewRect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsContentColor
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.AppTypography
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.text.ProvideTextStyle
import com.lhzkml.jasmine.core.widgets.ripple.ripple
import com.lhzkml.jasmine.core.widgets.surface.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lhzkml.jasmine.core.widgets.menu.DropdownMenuPositionProvider
import com.lhzkml.jasmine.core.widgets.tokens.ElevationTokens
import com.lhzkml.jasmine.core.widgets.tokens.ListTokens
import com.lhzkml.jasmine.core.widgets.tokens.MenuTokens
import com.lhzkml.jasmine.core.widgets.tokens.value
import kotlin.math.max
import kotlin.math.min

/**
 *
 * Menus display a list of choices on a temporary surface. They appear when users interact with a
 * button, action, or other control.
 *
 *
 * A [DropdownMenu] behaves similarly to a [Popup], and will use the position of the parent layout
 * to position itself on screen. Commonly a [DropdownMenu] will be placed in a [Box] with a sibling
 * that will be used as the 'anchor'. Note that a [DropdownMenu] by itself will not take up any
 * space in a layout, as the menu is displayed in a separate window, on top of other content.
 *
 * The [content] of a [DropdownMenu] will typically be [DropdownMenuItem]s, as well as custom
 * content. Using [DropdownMenuItem]s will result in a menu that matches the Material specification
 * for menus. Also note that the [content] is placed inside a scrollable [Column], so using a
 * [LazyColumn] as the root layout inside [content] is unsupported.
 *
 * [onDismissRequest] will be called when the menu should close - for example when there is a tap
 * outside the menu, or when the back key is pressed.
 *
 * [DropdownMenu] changes its positioning depending on the available space, always trying to be
 * fully visible. Depending on layout direction, first it will try to align its start to the start
 * of its parent, then its end to the end of its parent, and then to the edge of the window.
 * Vertically, it will try to align its top to the bottom of its parent, then its bottom to top of
 * its parent, and then to the edge of the window.
 *
 * An [offset] can be provided to adjust the positioning of the menu for cases when the layout
 * bounds of its parent do not coincide with its visual bounds.
 *
 * Example usage:
 *
 *
 * Example usage with a [ScrollState] to control the menu items scroll position:
 *
 * @param expanded whether the menu is expanded or not
 * @param onDismissRequest called when the user requests to dismiss the menu, such as by tapping
 *   outside the menu's bounds
 * @param modifier [Modifier] to be applied to the menu's content
 * @param offset [DpOffset] from the original position of the menu. The offset respects the
 *   [LayoutDirection], so the offset's x position will be added in LTR and subtracted in RTL.
 * @param scrollState a [ScrollState] to used by the menu's content for items vertical scrolling
 * @param properties [PopupProperties] for further customization of this popup's behavior
 * @param shape the shape of the menu
 * @param containerColor the container color of the menu
 * @param tonalElevation when [containerColor] is `CssVariables.card`, a translucent primary color
 *   overlay is applied on top of the container. A higher tonal elevation value will result in a
 *   darker color in light theme and lighter color in dark theme. See also: [Surface].
 * @param shadowElevation the elevation for the shadow below the menu
 * @param border the border to draw around the container of the menu. Pass `null` for no border.
 * @param content the content of this dropdown menu, typically a [DropdownMenuItem]
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = DefaultMenuProperties,
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    if (expandedState.currentState || expandedState.targetState) {
        val transformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
        val density = LocalDensity.current
        val popupPositionProvider =
            remember(offset, density) {
                DropdownMenuPositionProvider(offset, density) { parentBounds, menuBounds ->
                    transformOriginState.value = calculateTransformOrigin(parentBounds, menuBounds)
                }
            }

        Popup(
            onDismissRequest = onDismissRequest,
            popupPositionProvider = popupPositionProvider,
            properties = properties,
        ) {
            DropdownMenuContent(
                expandedState = expandedState,
                transformOriginState = transformOriginState,
                scrollState = scrollState,
                shape = shape,
                containerColor = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                border = border,
                modifier = modifier,
                content = content,
            )
        }
    }
}

/**
 *
 * Menus display a list of choices on a temporary surface. They appear when users interact with a
 * button, action, or other control.
 *
 *
 * Example usage:
 *
 * @param text text of the menu item
 * @param onClick called when this menu item is clicked
 * @param modifier the [Modifier] to be applied to this menu item
 * @param leadingIcon optional leading icon to be displayed at the beginning of the item's text
 * @param trailingIcon optional trailing icon to be displayed at the end of the item's text. This
 *   trailing icon slot can also accept [Text] to indicate a keyboard shortcut.
 * @param enabled controls the enabled state of this menu item. When `false`, this component will
 *   not respond to user input, and it will appear visually disabled and disabled to accessibility
 *   services.
 * @param colors [MenuItemColors] that will be used to resolve the colors used for this menu item in
 *   different states. See [MenuDefaults.itemColors].
 * @param contentPadding the padding applied to the content of this menu item
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting [Interaction]s for this menu item. You can use this to change the menu item's
 *   appearance or preview the menu item in different states. Note that if `null` is provided,
 *   interactions will still happen internally.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItemContent(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}

/** Contains default values used for [DropdownMenu] and [DropdownMenuItem]. */
object MenuDefaults {
    /** The default tonal elevation for a menu. */
    val TonalElevation = ElevationTokens.Level0

    /** The default shadow elevation for a menu. */
    val ShadowElevation = MenuTokens.ContainerElevation

    /** The default shape for a menu. */
    val shape
        @Composable get() = MenuTokens.ContainerShape.value

    /** The default container color for a menu. */
    val containerColor
        @Composable get() = MenuTokens.ContainerColor(LocalCssVariables.current)

    /**
     * Creates a [MenuItemColors] that represents the default text and icon colors used in a
     * [DropdownMenuItemContent].
     */
    @Composable fun itemColors() = LocalCssVariables.current.defaultMenuItemColors

    /**
     * Creates a [MenuItemColors] that represents the default text and icon colors used in a
     * [DropdownMenuItemContent].
     *
     * @param textColor the text color of this [DropdownMenuItemContent] when enabled
     * @param leadingIconColor the leading icon color of this [DropdownMenuItemContent] when enabled
     * @param trailingIconColor the trailing icon color of this [DropdownMenuItemContent] when
     *   enabled
     * @param disabledTextColor the text color of this [DropdownMenuItemContent] when not enabled
     * @param disabledLeadingIconColor the leading icon color of this [DropdownMenuItemContent] when
     *   not enabled
     * @param disabledTrailingIconColor the trailing icon color of this [DropdownMenuItemContent]
     *   when not enabled
     */
    @Composable
    fun itemColors(
        textColor: Color = Color.Unspecified,
        leadingIconColor: Color = Color.Unspecified,
        trailingIconColor: Color = Color.Unspecified,
        disabledTextColor: Color = Color.Unspecified,
        disabledLeadingIconColor: Color = Color.Unspecified,
        disabledTrailingIconColor: Color = Color.Unspecified,
    ): MenuItemColors =
        LocalCssVariables.current.defaultMenuItemColors.copy(
            textColor = textColor,
            leadingIconColor = leadingIconColor,
            trailingIconColor = trailingIconColor,
            disabledTextColor = disabledTextColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
        )

    internal val CssVariables.defaultMenuItemColors: MenuItemColors
        @Composable
        get() =
            remember(this) {
                MenuItemColors(
                    textColor = ListTokens.ListItemLabelTextColor(this),
                    leadingIconColor = ListTokens.ListItemLeadingIconColor(this),
                    trailingIconColor = ListTokens.ListItemTrailingIconColor(this),
                    disabledTextColor =
                        ListTokens.ListItemDisabledLabelTextColor(this)
                            .copy(alpha = ListTokens.ListItemDisabledLabelTextOpacity),
                    disabledLeadingIconColor =
                        ListTokens.ListItemDisabledLeadingIconColor(this)
                            .copy(alpha = ListTokens.ListItemDisabledLeadingIconOpacity),
                    disabledTrailingIconColor =
                        ListTokens.ListItemDisabledTrailingIconColor(this)
                            .copy(alpha = ListTokens.ListItemDisabledTrailingIconOpacity),
                )
            }

    /** Default padding used for [DropdownMenuItem]. */
    val DropdownMenuItemContentPadding =
        PaddingValues(horizontal = DropdownMenuItemHorizontalPadding, vertical = 0.dp)
}

// TODO: Consider to move into public [MenuDefaults]
internal val DefaultMenuProperties = PopupProperties(focusable = true)

/**
 * Represents the text and icon colors used in a menu item at different states.
 *
 * @param textColor the text color of this [DropdownMenuItemContent] when enabled
 * @param leadingIconColor the leading icon color of this [DropdownMenuItemContent] when enabled
 * @param trailingIconColor the trailing icon color of this [DropdownMenuItemContent] when enabled
 * @param disabledTextColor the text color of this [DropdownMenuItemContent] when not enabled
 * @param disabledLeadingIconColor the leading icon color of this [DropdownMenuItemContent] when not
 *   enabled
 * @param disabledTrailingIconColor the trailing icon color of this [DropdownMenuItemContent] when
 *   not enabled
 * @constructor create an instance with arbitrary colors. See [MenuDefaults.itemColors] for the
 *   default colors used in a [DropdownMenuItemContent].
 */
@Immutable
class MenuItemColors(
    val textColor: Color,
    val leadingIconColor: Color,
    val trailingIconColor: Color,
    val disabledTextColor: Color,
    val disabledLeadingIconColor: Color,
    val disabledTrailingIconColor: Color,
) {

    /**
     * Returns a copy of this MenuItemColors, optionally overriding some of the values. This uses
     * the Color.Unspecified to mean “use the value from the source”
     */
    fun copy(
        textColor: Color = this.textColor,
        leadingIconColor: Color = this.leadingIconColor,
        trailingIconColor: Color = this.trailingIconColor,
        disabledTextColor: Color = this.disabledTextColor,
        disabledLeadingIconColor: Color = this.disabledLeadingIconColor,
        disabledTrailingIconColor: Color = this.disabledTrailingIconColor,
    ) =
        MenuItemColors(
            textColor.takeOrElse { this.textColor },
            leadingIconColor.takeOrElse { this.leadingIconColor },
            trailingIconColor.takeOrElse { this.trailingIconColor },
            disabledTextColor.takeOrElse { this.disabledTextColor },
            disabledLeadingIconColor.takeOrElse { this.disabledLeadingIconColor },
            disabledTrailingIconColor.takeOrElse { this.disabledTrailingIconColor },
        )

    /**
     * Represents the text color for a menu item, depending on its [enabled] state.
     *
     * @param enabled whether the menu item is enabled
     */
    @Stable
    internal fun textColor(enabled: Boolean): Color = if (enabled) textColor else disabledTextColor

    /**
     * Represents the leading icon color for a menu item, depending on its [enabled] state.
     *
     * @param enabled whether the menu item is enabled
     */
    @Stable
    internal fun leadingIconColor(enabled: Boolean): Color =
        if (enabled) leadingIconColor else disabledLeadingIconColor

    /**
     * Represents the trailing icon color for a menu item, depending on its [enabled] state.
     *
     * @param enabled whether the menu item is enabled
     */
    @Stable
    internal fun trailingIconColor(enabled: Boolean): Color =
        if (enabled) trailingIconColor else disabledTrailingIconColor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is MenuItemColors) return false

        if (textColor != other.textColor) return false
        if (leadingIconColor != other.leadingIconColor) return false
        if (trailingIconColor != other.trailingIconColor) return false
        if (disabledTextColor != other.disabledTextColor) return false
        if (disabledLeadingIconColor != other.disabledLeadingIconColor) return false
        if (disabledTrailingIconColor != other.disabledTrailingIconColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textColor.hashCode()
        result = 31 * result + leadingIconColor.hashCode()
        result = 31 * result + trailingIconColor.hashCode()
        result = 31 * result + disabledTextColor.hashCode()
        result = 31 * result + disabledLeadingIconColor.hashCode()
        result = 31 * result + disabledTrailingIconColor.hashCode()
        return result
    }
}

@Composable
internal fun DropdownMenuContent(
    modifier: Modifier,
    expandedState: MutableTransitionState<Boolean>,
    transformOriginState: MutableState<TransformOrigin>,
    scrollState: ScrollState,
    shape: Shape,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    border: BorderStroke?,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Menu open/close animation.
    @Suppress("DEPRECATION") val transition = updateTransition(expandedState, "DropDownMenu")
    // TODO Load the motionScheme tokens from the component tokens file
    val scaleAnimationSpec = LocalMotionScheme.current.fastSpatialSpec<Float>()
    val alphaAnimationSpec = LocalMotionScheme.current.fastEffectsSpec<Float>()
    val scale by
        transition.animateFloat(transitionSpec = { scaleAnimationSpec }) { expanded ->
            if (expanded) ExpandedScaleTarget else ClosedScaleTarget
        }

    val alpha by
        transition.animateFloat(transitionSpec = { alphaAnimationSpec }) { expanded ->
            if (expanded) ExpandedAlphaTarget else ClosedAlphaTarget
        }

    val isInspecting = LocalInspectionMode.current
    Surface(
        modifier =
            Modifier.graphicsLayer {
                scaleX =
                    if (!isInspecting) scale
                    else if (expandedState.targetState) ExpandedScaleTarget else ClosedScaleTarget
                scaleY =
                    if (!isInspecting) scale
                    else if (expandedState.targetState) ExpandedScaleTarget else ClosedScaleTarget
                this.alpha =
                    if (!isInspecting) alpha
                    else if (expandedState.targetState) ExpandedAlphaTarget else ClosedAlphaTarget
                transformOrigin = transformOriginState.value
            },
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Column(
            modifier =
                modifier
                    .padding(vertical = DropdownMenuVerticalPadding)
                    .width(IntrinsicSize.Max)
                    .verticalScroll(scrollState),
            content = content,
        )
    }
}

@Composable
internal fun DropdownMenuItemContent(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    enabled: Boolean,
    colors: MenuItemColors,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
) {
    Row(
        modifier =
            modifier
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = ripple(true),
                )
                .fillMaxWidth()
                // Preferred min and max width used during the intrinsic measurement.
                .sizeIn(
                    minWidth = DropdownMenuItemDefaultMinWidth,
                    maxWidth = DropdownMenuItemDefaultMaxWidth,
                    minHeight = MenuListItemContainerHeight,
                )
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TODO(b/271818892): Align menu list item style with general list item style.
        ProvideTextStyle(AppTypography.labelLarge) {
            if (leadingIcon != null) {
                CompositionLocalProvider(
                    LocalWidgetsContentColor provides colors.leadingIconColor(enabled)
                ) {
                    Box(Modifier.defaultMinSize(minWidth = ListTokens.ListItemLeadingIconSize)) {
                        leadingIcon()
                    }
                }
            }
            CompositionLocalProvider(LocalWidgetsContentColor provides colors.textColor(enabled)) {
                Box(
                    Modifier.weight(1f)
                        .padding(
                            start =
                                if (leadingIcon != null) {
                                    DropdownMenuItemHorizontalPadding
                                } else {
                                    0.dp
                                },
                            end =
                                if (trailingIcon != null) {
                                    DropdownMenuItemHorizontalPadding
                                } else {
                                    0.dp
                                },
                        )
                ) {
                    text()
                }
            }
            if (trailingIcon != null) {
                CompositionLocalProvider(
                    LocalWidgetsContentColor provides colors.trailingIconColor(enabled)
                ) {
                    Box(Modifier.defaultMinSize(minWidth = ListTokens.ListItemTrailingIconSize)) {
                        trailingIcon()
                    }
                }
            }
        }
    }
}

internal fun calculateTransformOrigin(anchorBounds: IntRect, menuBounds: IntRect): TransformOrigin {
    val pivotX =
        when {
            menuBounds.left >= anchorBounds.right -> 0f
            menuBounds.right <= anchorBounds.left -> 1f
            menuBounds.width == 0 -> 0f
            else -> {
                val intersectionCenter =
                    (max(anchorBounds.left, menuBounds.left) +
                        min(anchorBounds.right, menuBounds.right)) / 2
                (intersectionCenter - menuBounds.left).toFloat() / menuBounds.width
            }
        }
    val pivotY =
        when {
            menuBounds.top >= anchorBounds.bottom -> 0f
            menuBounds.bottom <= anchorBounds.top -> 1f
            menuBounds.height == 0 -> 0f
            else -> {
                val intersectionCenter =
                    (max(anchorBounds.top, menuBounds.top) +
                        min(anchorBounds.bottom, menuBounds.bottom)) / 2
                (intersectionCenter - menuBounds.top).toFloat() / menuBounds.height
            }
        }
    return TransformOrigin(pivotX, pivotY)
}

// Size defaults.
internal val MenuVerticalMargin = 48.dp
private val MenuListItemContainerHeight = 48.dp
private val DropdownMenuItemHorizontalPadding = 12.dp
internal val DropdownMenuVerticalPadding = 8.dp
private val DropdownMenuItemDefaultMinWidth = 112.dp
private val DropdownMenuItemDefaultMaxWidth = 280.dp

// Menu open/close animation.
internal const val ExpandedScaleTarget = 1f
internal const val ClosedScaleTarget = 0.8f
internal const val ExpandedAlphaTarget = 1f
internal const val ClosedAlphaTarget = 0f
