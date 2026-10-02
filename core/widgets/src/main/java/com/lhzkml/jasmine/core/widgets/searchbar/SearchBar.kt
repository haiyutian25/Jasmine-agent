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
// 本项目自有的组件代码（移植自 AndroidX Material3 的 SearchBar.kt 后自行维护），不再跟随上游生成，可直接改。
// 只保留了新版「state 版」的搜索框入口：SearchBar(state) / TopSearchBar / ExpandedFullScreenSearchBar /
// ExpandedDockedSearchBar + SearchBarState 家族；上游那套「一体版」（SearchBar/DockedSearchBar 的
// inputField + expanded 重载）与已废弃的 query 版重载、以及只为二进制兼容的 HIDDEN 重载都没搬。

package com.lhzkml.jasmine.core.widgets.searchbar

import androidx.annotation.FloatRange
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.lhzkml.jasmine.core.widgets.bottomsheet.BackEventCompat
import com.lhzkml.jasmine.core.widgets.bottomsheet.BackEventProgress
import com.lhzkml.jasmine.core.widgets.bottomsheet.BackHandler
import com.lhzkml.jasmine.core.widgets.bottomsheet.BasicEdgeToEdgeDialog
import com.lhzkml.jasmine.core.widgets.bottomsheet.MutableWindowInsets
import com.lhzkml.jasmine.core.widgets.bottomsheet.PredictiveBack
import com.lhzkml.jasmine.core.widgets.bottomsheet.PredictiveBackState
import com.lhzkml.jasmine.core.widgets.textfield.Strings
import com.lhzkml.jasmine.core.widgets.bottomsheet.SwipeEdge
import com.lhzkml.jasmine.core.widgets.textfield.getString
import com.lhzkml.jasmine.core.widgets.textfield.textFieldBackground
import com.lhzkml.jasmine.core.widgets.tokens.FilledTextFieldTokens
import com.lhzkml.jasmine.core.widgets.tokens.MotionSchemeKeyTokens
import com.lhzkml.jasmine.core.widgets.tokens.MotionTokens
import com.lhzkml.jasmine.core.widgets.tokens.SearchBarTokens
import com.lhzkml.jasmine.core.widgets.tokens.SearchViewTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.lhzkml.jasmine.core.ui.theme.contentColorFor
import com.lhzkml.jasmine.core.widgets.divider.HorizontalDivider
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsTextStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lhzkml.jasmine.core.widgets.motion.value
import com.lhzkml.jasmine.core.widgets.surface.Surface
import com.lhzkml.jasmine.core.widgets.tokens.ElevationTokens
import com.lhzkml.jasmine.core.widgets.textfield.TextFieldColors
import com.lhzkml.jasmine.core.widgets.textfield.TextFieldDefaults
import com.lhzkml.jasmine.core.widgets.tokens.value
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables


/**
 * [Material Design search](https://m3.material.io/components/search/overview)
 *
 * A search bar represents a field that allows users to enter a keyword or phrase and get relevant
 * information. It can be used as a way to navigate through an app via search queries.
 *
 * ![Search bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/search-bar.png)
 *
 * The [SearchBar] component represents a search bar in the collapsed state. It should be used in
 * conjunction with an [ExpandedFullScreenSearchBar] or [ExpandedDockedSearchBar] to display search
 * results when expanded.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField]
 *   and the expanded search bar.
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this search bar when collapsed.
 * @param shape the shape of this search bar when collapsed.
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 */
@Suppress("ComposableLambdaParameterNaming", "ComposableLambdaParameterPosition")
@Composable
fun SearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
) {
    Surface(
        modifier = modifier.onGloballyPositioned { state.collapsedCoords = it },
        shape = shape,
        color = colors.containerColor,
        contentColor = LocalCssVariables.current.contentColorFor(colors.containerColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = inputField,
    )
}


/**
 * [ExpandedFullScreenSearchBar] represents a search bar that is currently expanding or in the
 * expanded state, showing search results. This component is displayed in a new full-screen dialog.
 * If this expansion behavior is undesirable, for example on medium or large screens such as
 * tablets, [ExpandedDockedSearchBar] can be used instead.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField]
 *   and the collapsed search bar.
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this expanded search bar.
 * @param collapsedShape the shape of the search bar when it is collapsed. When fully expanded, the
 *   shape will always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 * @param windowInsets the window insets that this search bar will respect when expanded.
 * @param properties the platform-specific properties to configure the dialog's behavior. Any
 *   properties which limit the dialog's size (e.g. [DialogProperties.usePlatformDefaultWidth]) are
 *   ignored.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@Composable
fun ExpandedFullScreenSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    collapsedShape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: @Composable () -> WindowInsets = { SearchBarDefaults.fullScreenWindowInsets },
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.isExpanded) return

    val coroutineScope = rememberCoroutineScope()

    BasicEdgeToEdgeDialog(
        onDismissRequest = { coroutineScope.launch { state.animateToCollapsed() } },
        properties = properties,
    ) { predictiveBackState ->
        val focusRequester = remember { FocusRequester() }
        FullScreenSearchBarLayout(
            state = state,
            predictiveBackState = predictiveBackState,
            inputField = {
                Box(
                    modifier = Modifier.focusRequester(focusRequester),
                    propagateMinConstraints = true,
                ) {
                    inputField()
                }
            },
            modifier = modifier,
            collapsedShape = collapsedShape,
            colors = colors,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            windowInsets = windowInsets(),
            content = content,
        )

        // Focus the input field on the first expansion,
        // but no need to re-focus if the focus gets cleared.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Manually dismiss keyboard when search bar is collapsed.
        // Otherwise, the search bar's window closes and the keyboard disappears suddenly.
        val softwareKeyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(state.targetValue) {
            if (state.targetValue == SearchBarValue.Collapsed) {
                softwareKeyboardController?.hide()
            }
        }
    }
}

/**
 * [ExpandedDockedSearchBar] represents a search bar that is currently expanding or in the expanded
 * state, showing search results. This component is displayed in a popup over the collapsed search
 * bar. It is recommended to use [ExpandedDockedSearchBar] on medium and large screens such as
 * tablets, and to instead use [ExpandedFullScreenSearchBar] on compact screen such as phones.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField]
 *   and the collapsed search bar.
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this expanded search bar.
 * @param shape the shape of this search bar.
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 * @param properties the platform-specific properties to configure the dialog's behavior. Any
 *   properties which limit the dialog's size (e.g. [DialogProperties.usePlatformDefaultWidth]) are
 *   ignored.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@Composable
fun ExpandedDockedSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.dockedShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    properties: PopupProperties = PopupProperties(focusable = true, clippingEnabled = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.isExpanded) return

    val positionProvider =
        remember(state) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = state.collapsedBounds.topLeft
            }
        }

    val scope = rememberCoroutineScope()

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { scope.launch { state.animateToCollapsed() } },
        properties = properties,
    ) {
        val focusRequester = remember { FocusRequester() }

        DockedSearchBarLayout(
            state = state,
            inputField = {
                Box(
                    modifier = Modifier.focusRequester(focusRequester),
                    propagateMinConstraints = true,
                ) {
                    inputField()
                }
            },
            modifier = modifier,
            shape = shape,
            colors = colors,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content,
        )

        // Focus the input field on the first expansion,
        // but no need to re-focus if the focus gets cleared.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Manually dismiss keyboard when search bar is collapsed.
        // Otherwise, the search bar's window closes and the keyboard disappears suddenly.
        val softwareKeyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(state.targetValue) {
            if (state.targetValue == SearchBarValue.Collapsed) {
                softwareKeyboardController?.hide()
            }
        }
    }
}


/** Possible values of [SearchBarState]. */
enum class SearchBarValue {
    /** The state of the search bar when it is collapsed. */
    Collapsed,

    /** The state of the search bar when it is expanded. */
    Expanded,
}

/** The state of a search bar. */
@Stable
class SearchBarState
private constructor(
    private val animatable: Animatable<Float, AnimationVector1D>,
    private val animationSpecForExpand: AnimationSpec<Float>,
    private val animationSpecForCollapse: AnimationSpec<Float>,
) {
    /**
     * Construct a [SearchBarState].
     *
     * @param initialValue the initial value of whether the search bar is collapsed or expanded.
     * @param animationSpecForExpand the animation spec used when the search bar expands.
     * @param animationSpecForCollapse the animation spec used when the search bar collapses.
     */
    constructor(
        initialValue: SearchBarValue,
        animationSpecForExpand: AnimationSpec<Float>,
        animationSpecForCollapse: AnimationSpec<Float>,
    ) : this(
        animatable =
            Animatable(if (initialValue == SearchBarValue.Expanded) Expanded else Collapsed),
        animationSpecForExpand = animationSpecForExpand,
        animationSpecForCollapse = animationSpecForCollapse,
    )

    /**
     * The layout coordinates, if available, of the search bar when it is collapsed. Used to
     * coordinate the expansion animation.
     */
    var collapsedCoords: LayoutCoordinates? by mutableStateOf(null)

    /**
     * The animation progress of the search bar, where 0 represents [SearchBarValue.Collapsed] and 1
     * represents [SearchBarValue.Expanded].
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    val progress: Float
        get() = animatable.value.coerceIn(0f, 1f)

    /** Whether the state is currently animating */
    val isAnimating: Boolean
        get() = animatable.isRunning

    /** Whether the search bar is going to be expanded or collapsed. */
    val targetValue: SearchBarValue
        get() =
            if (animatable.targetValue == Expanded) {
                SearchBarValue.Expanded
            } else {
                SearchBarValue.Collapsed
            }

    /**
     * Whether the search bar is currently expanded or collapsed. If the search bar is currently
     * animating to/from the expanded state, [currentValue] is [SearchBarValue.Expanded] until the
     * animation completes.
     */
    val currentValue: SearchBarValue by derivedStateOf {
        if (animatable.value == Collapsed) {
            SearchBarValue.Collapsed
        } else {
            SearchBarValue.Expanded
        }
    }

    /** Animate the search bar to its expanded state. */
    suspend fun animateToExpanded() {
        animatable.animateTo(targetValue = 1f, animationSpec = animationSpecForExpand)
    }

    /** Animate the search bar to its collapsed state. */
    suspend fun animateToCollapsed() {
        animatable.animateTo(targetValue = 0f, animationSpec = animationSpecForCollapse)
    }

    /**
     * Snap the search bar progress to the given [fraction], where 0 represents
     * [SearchBarValue.Collapsed] and 1 represents [SearchBarValue.Expanded].
     */
    suspend fun snapTo(fraction: Float) {
        animatable.snapTo(fraction)
    }

    companion object {
        private const val Collapsed = 0f
        private const val Expanded = 1f

        /** The default [Saver] implementation for [SearchBarState]. */
        fun Saver(
            animationSpecForExpand: AnimationSpec<Float>,
            animationSpecForCollapse: AnimationSpec<Float>,
        ): Saver<SearchBarState, *> =
            listSaver(
                save = { listOf(it.progress) },
                restore = {
                    SearchBarState(
                        animatable = Animatable(it[0], Float.VectorConverter),
                        animationSpecForExpand = animationSpecForExpand,
                        animationSpecForCollapse = animationSpecForCollapse,
                    )
                },
            )
    }
}

/**
 * Create and remember a [SearchBarState].
 *
 * @param initialValue the initial value of whether the search bar is collapsed or expanded.
 * @param animationSpecForExpand the animation spec used when the search bar expands.
 * @param animationSpecForCollapse the animation spec used when the search bar collapses.
 */
@Composable
fun rememberSearchBarState(
    initialValue: SearchBarValue = SearchBarValue.Collapsed,
    animationSpecForExpand: AnimationSpec<Float> = MotionSchemeKeyTokens.SlowSpatial.value(),
    animationSpecForCollapse: AnimationSpec<Float> = MotionSchemeKeyTokens.DefaultSpatial.value(),
): SearchBarState {
    return rememberSaveable(
        initialValue,
        animationSpecForExpand,
        animationSpecForCollapse,
        saver =
            SearchBarState.Saver(
                animationSpecForExpand = animationSpecForExpand,
                animationSpecForCollapse = animationSpecForCollapse,
            ),
    ) {
        SearchBarState(
            initialValue = initialValue,
            animationSpecForExpand = animationSpecForExpand,
            animationSpecForCollapse = animationSpecForCollapse,
        )
    }
}

private val SearchBarState.isExpanded
    get() = this.currentValue == SearchBarValue.Expanded

/** Defaults used in [SearchBar] and [DockedSearchBar]. */
object SearchBarDefaults {
    /** Default tonal elevation for a search bar. */
    val TonalElevation: Dp = ElevationTokens.Level0

    /** Default shadow elevation for a search bar. */
    val ShadowElevation: Dp = ElevationTokens.Level0

    /** Default height for a search bar's input field, or a search bar in the unexpanded state. */
    val InputFieldHeight: Dp = SearchBarTokens.ContainerHeight

    /** Default shape for a search bar's input field, or a search bar in the unexpanded state. */
    val inputFieldShape: Shape
        @Composable get() = SearchBarTokens.ContainerShape.value

    /** Default shape for a [SearchBar] in the expanded state. */
    val fullScreenShape: Shape
        @Composable get() = SearchViewTokens.FullScreenContainerShape.value

    /** Default shape for a [DockedSearchBar]. */
    val dockedShape: Shape
        @Composable get() = SearchViewTokens.DockedContainerShape.value

    /** Default window insets used and consumed by [ExpandedFullScreenSearchBar]. */
    val fullScreenWindowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing

    /**
     * Creates a [SearchBarColors] that represents the different colors used in parts of the search
     * bar in different states.
     *
     * @param containerColor the container color of the search bar
     * @param dividerColor the color of the divider between the input field and the search results
     * @param inputFieldColors the colors of the input field. This can be accessed using
     *   [SearchBarColors.inputFieldColors] and should be passed to the `inputField` slot of the
     *   search bar.
     */
    @Composable
    fun colors(
        containerColor: Color = SearchBarTokens.ContainerColor.value,
        dividerColor: Color = SearchViewTokens.DividerColor.value,
        inputFieldColors: TextFieldColors =
            inputFieldColors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = containerColor,
            ),
    ): SearchBarColors =
        SearchBarColors(
            containerColor = containerColor,
            dividerColor = dividerColor,
            inputFieldColors = inputFieldColors,
        )

    /**
     * Creates a [TextFieldColors] that represents the different colors used in the search bar input
     * field in different states.
     *
     * Only a subset of the full list of [TextFieldColors] parameters are used in the input field.
     * All other parameters have no effect.
     *
     * @param focusedTextColor the color used for the input text of this input field when focused
     * @param unfocusedTextColor the color used for the input text of this input field when not
     *   focused
     * @param disabledTextColor the color used for the input text of this input field when disabled
     * @param cursorColor the cursor color for this input field
     * @param selectionColors the colors used when the input text of this input field is selected
     * @param focusedLeadingIconColor the leading icon color for this input field when focused
     * @param unfocusedLeadingIconColor the leading icon color for this input field when not focused
     * @param disabledLeadingIconColor the leading icon color for this input field when disabled
     * @param focusedTrailingIconColor the trailing icon color for this input field when focused
     * @param unfocusedTrailingIconColor the trailing icon color for this input field when not
     *   focused
     * @param disabledTrailingIconColor the trailing icon color for this input field when disabled
     * @param focusedPlaceholderColor the placeholder color for this input field when focused
     * @param unfocusedPlaceholderColor the placeholder color for this input field when not focused
     * @param disabledPlaceholderColor the placeholder color for this input field when disabled
     * @param focusedPrefixColor the prefix color for this input field when focused
     * @param unfocusedPrefixColor the prefix color for this input field when not focused
     * @param disabledPrefixColor the prefix color for this input field when disabled
     * @param focusedSuffixColor the suffix color for this input field when focused
     * @param unfocusedSuffixColor the suffix color for this input field when not focused
     * @param disabledSuffixColor the suffix color for this input field when disabled
     * @param focusedContainerColor the container color for this input field when focused
     * @param unfocusedContainerColor the container color for this input field when not focused
     * @param disabledContainerColor the container color for this input field when disabled
     */
    @Composable
    fun inputFieldColors(
        focusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        unfocusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color =
            FilledTextFieldTokens.DisabledLeadingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity
            ),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color =
            FilledTextFieldTokens.DisabledTrailingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity
            ),
        focusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        unfocusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedPrefixColor: Color = FilledTextFieldTokens.InputPrefixColor.value,
        unfocusedPrefixColor: Color = FilledTextFieldTokens.InputPrefixColor.value,
        disabledPrefixColor: Color =
            FilledTextFieldTokens.InputPrefixColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedSuffixColor: Color = FilledTextFieldTokens.InputSuffixColor.value,
        unfocusedSuffixColor: Color = FilledTextFieldTokens.InputSuffixColor.value,
        disabledSuffixColor: Color =
            FilledTextFieldTokens.InputSuffixColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedContainerColor: Color = SearchBarTokens.ContainerColor.value,
        unfocusedContainerColor: Color = SearchBarTokens.ContainerColor.value,
        disabledContainerColor: Color = SearchBarTokens.ContainerColor.value,
    ): TextFieldColors =
        TextFieldDefaults.colors(
            focusedTextColor = focusedTextColor,
            unfocusedTextColor = unfocusedTextColor,
            disabledTextColor = disabledTextColor,
            cursorColor = cursorColor,
            selectionColors = selectionColors,
            focusedLeadingIconColor = focusedLeadingIconColor,
            unfocusedLeadingIconColor = unfocusedLeadingIconColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            focusedTrailingIconColor = focusedTrailingIconColor,
            unfocusedTrailingIconColor = unfocusedTrailingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            focusedPlaceholderColor = focusedPlaceholderColor,
            unfocusedPlaceholderColor = unfocusedPlaceholderColor,
            disabledPlaceholderColor = disabledPlaceholderColor,
            focusedPrefixColor = focusedPrefixColor,
            unfocusedPrefixColor = unfocusedPrefixColor,
            disabledPrefixColor = disabledPrefixColor,
            focusedSuffixColor = focusedSuffixColor,
            unfocusedSuffixColor = unfocusedSuffixColor,
            disabledSuffixColor = disabledSuffixColor,
            focusedContainerColor = focusedContainerColor,
            unfocusedContainerColor = unfocusedContainerColor,
            disabledContainerColor = disabledContainerColor,
        )

    /**
     * A text field to input a query in a search bar.
     *
     * This overload of [InputField] uses [TextFieldState] to keep track of the text content and
     * position of the cursor or selection, and [SearchBarState] to keep track of the state of the
     * search bar. It should be used with the search bar APIs which also accept a [SearchBarState].
     *
     * @param textFieldState [TextFieldState] that holds the internal editing state of the input
     *   field.
     * @param searchBarState the state of the search bar as a whole.
     * @param onSearch the callback to be invoked when the input service triggers the
     *   [ImeAction.Search] action. The current query in the [textFieldState] comes as a parameter
     *   of the callback.
     * @param modifier the [Modifier] to be applied to this input field.
     * @param enabled the enabled state of this input field. When `false`, this component will not
     *   respond to user input, and it will appear visually disabled and disabled to accessibility
     *   services.
     * @param readOnly controls the editable state of the input field. When `true`, the field cannot
     *   be modified. However, a user can focus it and copy text from it.
     * @param textStyle the style to be applied to the input text. Defaults to [LocalWidgetsTextStyle].
     * @param placeholder the placeholder to be displayed when the input text is empty.
     * @param leadingIcon the leading icon to be displayed at the start of the input field.
     * @param trailingIcon the trailing icon to be displayed at the end of the input field.
     * @param prefix the optional prefix to be displayed before the input text.
     * @param suffix the optional suffix to be displayed after the input text.
     * @param inputTransformation optional [InputTransformation] that will be used to transform
     *   changes to the [TextFieldState] made by the user. The transformation will be applied to
     *   changes made by hardware and software keyboard events, pasting or dropping text,
     *   accessibility services, and tests. The transformation will _not_ be applied when changing
     *   the [textFieldState] programmatically, or when the transformation is changed. If the
     *   transformation is changed on an existing text field, it will be applied to the next user
     *   edit. The transformation will not immediately affect the current [textFieldState].
     * @param outputTransformation optional [OutputTransformation] that transforms how the contents
     *   of the text field are presented.
     * @param scrollState scroll state that manages the horizontal scroll of the input field.
     * @param shape the shape of the input field.
     * @param colors [TextFieldColors] that will be used to resolve the colors used for this input
     *   field in different states. See [SearchBarDefaults.inputFieldColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this input field. You can use this to change the search bar's
     *   appearance or preview the search bar in different states. Note that if `null` is provided,
     *   interactions will still happen internally.
     */
    @Composable
    fun InputField(
        textFieldState: TextFieldState,
        searchBarState: SearchBarState,
        onSearch: (String) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        textStyle: TextStyle = LocalWidgetsTextStyle.current,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        inputTransformation: InputTransformation? = null,
        outputTransformation: OutputTransformation? = null,
        scrollState: ScrollState = rememberScrollState(),
        shape: Shape = inputFieldShape,
        colors: TextFieldColors = inputFieldColors(),
        interactionSource: MutableInteractionSource? = null,
    ) {
        @Suppress("NAME_SHADOWING")
        val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

        /*
        Relationship between focus and expansion state:
            * In touch mode, the two are coupled:
                * Text field gains focus => search bar expands
                * Search bar collapses => text field loses focus
            * In non-touch/keyboard mode, they are independent. Instead, expansion triggers when:
                * the user starts typing
                * the user presses the down direction key
         */
        val focused by interactionSource.collectIsFocusedAsState()
        val focusManager = LocalFocusManager.current
        val isInTouchMode = LocalInputModeManager.current.inputMode == InputMode.Touch

        val searchSemantics = getString(Strings.SearchBarSearch)
        val suggestionsAvailableSemantics = getString(Strings.SuggestionsAvailable)

        val textColor =
            textStyle.color.takeOrElse {
                colors.textColor(enabled, isError = false, focused = focused)
            }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

        val coroutineScope = rememberCoroutineScope()

        BasicTextField(
            state = textFieldState,
            modifier =
                modifier
                    .onPreviewKeyEvent {
                        val expandOnDownKey = !isInTouchMode && !searchBarState.isExpanded
                        if (expandOnDownKey && it.key == Key.DirectionDown) {
                            coroutineScope.launch { searchBarState.animateToExpanded() }
                            return@onPreviewKeyEvent true
                        }
                        // Make sure arrow key down moves to list of suggestions.
                        if (searchBarState.isExpanded && it.key == Key.DirectionDown) {
                            focusManager.moveFocus(FocusDirection.Down)
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .sizeIn(
                        minWidth = SearchBarMinWidth,
                        maxWidth = SearchBarMaxWidth,
                        minHeight = SearchBarDefaults.InputFieldHeight,
                    )
                    .onFocusChanged {
                        if (it.isFocused && isInTouchMode) {
                            coroutineScope.launch { searchBarState.animateToExpanded() }
                        }
                    }
                    .semantics {
                        contentDescription = searchSemantics
                        if (searchBarState.isExpanded) {
                            stateDescription = suggestionsAvailableSemantics
                        }
                    },
            enabled = enabled,
            readOnly = readOnly,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError = false)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch(textFieldState.text.toString()) },
            interactionSource = interactionSource,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            scrollState = scrollState,
            decorator =
                TextFieldDefaults.decorator(
                    state = textFieldState,
                    enabled = enabled,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = outputTransformation,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    leadingIcon =
                        leadingIcon?.let { leading ->
                            { Box(Modifier.offset(x = SearchBarIconOffsetX)) { leading() } }
                        },
                    trailingIcon =
                        trailingIcon?.let { trailing ->
                            { Box(Modifier.offset(x = -SearchBarIconOffsetX)) { trailing() } }
                        },
                    prefix = prefix,
                    suffix = suffix,
                    colors = colors,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                    container = {
                        val containerColor =
                            animateColorAsState(
                                targetValue =
                                    colors.containerColor(
                                        enabled = enabled,
                                        isError = false,
                                        focused = focused,
                                    ),
                                animationSpec = MotionSchemeKeyTokens.FastEffects.value(),
                            )
                        Box(Modifier.textFieldBackground(containerColor::value, shape))
                    },
                ),
        )

        // Most expansions from touch happen via `onFocusChanged` above, but in a mixed
        // keyboard-touch flow, the user can focus via keyboard (with no expansion),
        // and subsequent touches won't change focus state. So this effect is needed as well.
        DetectClickFromInteractionSource(interactionSource) {
            if (!searchBarState.isExpanded) {
                coroutineScope.launch { searchBarState.animateToExpanded() }
            }
        }

        // Expand search bar if the user starts typing
        LaunchedEffect(searchBarState, textFieldState) {
            if (!searchBarState.isExpanded) {
                var prevLength = textFieldState.text.length
                snapshotFlow { textFieldState.text }
                    .onEach {
                        val currLength = it.length
                        if (currLength > prevLength && focused && !searchBarState.isExpanded) {
                            // Don't use LaunchedEffect's coroutine because
                            // cancelling the animation shouldn't cancel the Flow
                            coroutineScope.launch { searchBarState.animateToExpanded() }
                        }
                        prevLength = currLength
                    }
                    .collect {}
            }
        }

        val shouldClearFocusOnCollapse = !searchBarState.isExpanded && focused && isInTouchMode
        LaunchedEffect(searchBarState.isExpanded) {
            if (shouldClearFocusOnCollapse) {
                focusManager.clearFocus()
            }
        }
    }


}

/**
 * Represents the colors used by a search bar in different states.
 *
 * See [SearchBarDefaults.colors] for the default implementation that follows Material
 * specifications.
 */
@Immutable
class SearchBarColors(
    val containerColor: Color,
    val dividerColor: Color,
    val inputFieldColors: TextFieldColors,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchBarColors) return false

        if (containerColor != other.containerColor) return false
        if (dividerColor != other.dividerColor) return false
        if (inputFieldColors != other.inputFieldColors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + dividerColor.hashCode()
        result = 31 * result + inputFieldColors.hashCode()
        return result
    }
}

@Composable
private fun DockedSearchBarLayout(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    colors: SearchBarColors,
    tonalElevation: Dp,
    shadowElevation: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.isExpanded) { scope.launch { state.animateToCollapsed() } }

    Surface(
        shape = shape,
        color = colors.containerColor,
        contentColor = LocalCssVariables.current.contentColorFor(colors.containerColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier.imePadding(),
    ) {
        val windowContainerHeight = getWindowContainerHeight()
        val maxHeight = windowContainerHeight * DockedExpandedTableMaxHeightScreenRatio
        val minHeight = DockedExpandedTableMinHeight.coerceAtMost(maxHeight)

        Layout(
            contents =
                listOf(
                    inputField,
                    {
                        Column {
                            HorizontalDivider(color = colors.dividerColor)
                            content()
                        }
                    },
                )
        ) { measurables, baseConstraints ->
            val (inputFieldMeasurables, contentMeasurables) = measurables
            val constraintMaxHeight =
                lerp(state.collapsedBounds.height, maxHeight.roundToPx(), state.progress)
            val constraints =
                baseConstraints.constrain(
                    Constraints(
                        minHeight = minHeight.roundToPx().coerceAtMost(constraintMaxHeight),
                        maxHeight = constraintMaxHeight,
                    )
                )
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            val inputFieldPlaceables =
                inputFieldMeasurables.fastMap { it.measure(looseConstraints) }
            val inputFieldWidth = inputFieldPlaceables.fastMaxOfOrNull { it.width } ?: 0
            val inputFieldHeight = inputFieldPlaceables.fastMaxOfOrNull { it.height } ?: 0

            val contentConstraints =
                looseConstraints
                    .offset(vertical = -inputFieldHeight)
                    .copy(maxWidth = inputFieldWidth)
            val contentPlaceables = contentMeasurables.fastMap { it.measure(contentConstraints) }

            val height = inputFieldHeight + (contentPlaceables.fastMaxOfOrNull { it.height } ?: 0)
            val width = max(inputFieldWidth, contentPlaceables.fastMaxOfOrNull { it.width } ?: 0)

            layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
                inputFieldPlaceables.fastForEach { it.place(0, 0) }
                contentPlaceables.fastForEach { it.place(0, inputFieldHeight) }
            }
        }
    }
}

@Composable
private fun FullScreenSearchBarLayout(
    state: SearchBarState,
    predictiveBackState: PredictiveBackState,
    inputField: @Composable () -> Unit,
    modifier: Modifier,
    collapsedShape: Shape,
    colors: SearchBarColors,
    tonalElevation: Dp,
    shadowElevation: Dp,
    windowInsets: WindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backEvent by remember { derivedStateOf { predictiveBackState.value } }
    val firstInProgressValue =
        remember { mutableStateOf<BackEventProgress.InProgress?>(null) }
            .apply {
                when (val event = backEvent) {
                    is BackEventProgress.InProgress -> if (value == null) value = event
                    BackEventProgress.NotRunning -> value = null
                    BackEventProgress.Completed -> Unit
                }
            }
    val lastInProgressValue =
        remember { mutableStateOf<BackEventProgress.InProgress?>(null) }
            .apply {
                when (val event = backEvent) {
                    is BackEventProgress.InProgress -> value = event
                    BackEventProgress.NotRunning -> value = null
                    BackEventProgress.Completed -> Unit
                }
            }

    val density = LocalDensity.current
    val fullScreenShape = SearchBarDefaults.fullScreenShape
    val animatedShape =
        remember(density, fullScreenShape) {
            GenericShape { size, layoutDirection ->
                if (collapsedShape === CircleShape && fullScreenShape === RectangleShape) {
                    // The shape can only be animated if it's the default spec value
                    val radius =
                        with(density) {
                            val fraction =
                                max(1 - state.progress, lastInProgressValue.value.transform())
                            (SearchBarCornerRadius * fraction).toPx()
                        }
                    if (radius < 1e-3) {
                        addRect(size.toRect())
                    } else {
                        addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
                    }
                } else {
                    val shape = if (state.progress < 0.5f) collapsedShape else fullScreenShape
                    addOutline(shape.createOutline(size, layoutDirection, density))
                }
            }
        }

    // Top window insets need to be animated, but `Modifier.windowInsetsPadding` does not support
    // animation. The top insets are separated out so the animation calculations can be done
    // manually in the Layout's MeasureScope.
    val unconsumedInsets = remember { MutableWindowInsets() }
    val nonTopInsets =
        unconsumedInsets.insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Layout(
        modifier =
            modifier
                .onConsumedWindowInsetsChanged { consumedInsets ->
                    unconsumedInsets.insets = windowInsets.exclude(consumedInsets)
                }
                .consumeWindowInsets(windowInsets),
        content = {
            Box(
                modifier =
                    Modifier.layoutId(LayoutIdInputField)
                        .padding(nonTopInsets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
                propagateMinConstraints = true,
            ) {
                inputField()
            }

            Surface(
                modifier = Modifier.layoutId(LayoutIdSurface),
                shape = animatedShape,
                color = colors.containerColor,
                contentColor = LocalCssVariables.current.contentColorFor(colors.containerColor),
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                content = {},
            )

            Column(
                Modifier.layoutId(LayoutIdSearchContent).padding(nonTopInsets.asPaddingValues())
            ) {
                HorizontalDivider(color = colors.dividerColor)
                content()
            }
        },
    ) { measurables, constraints ->
        val predictiveBackProgress = lastInProgressValue.value.transform()
        val collapsedWidth =
            state.collapsedBounds.width.takeIf { it != 0 } ?: SearchBarMinWidth.roundToPx()
        val collapsedHeight =
            state.collapsedBounds.height.takeIf { it != 0 } ?: SearchBarDefaults.InputFieldHeight.roundToPx()

        val predictiveBackEndWidth =
            (constraints.maxWidth * SearchBarPredictiveBackMinScale)
                .roundToInt()
                .coerceAtLeast(collapsedWidth)
        val predictiveBackEndHeight =
            (constraints.maxHeight * SearchBarPredictiveBackMinScale)
                .roundToInt()
                .coerceAtLeast(collapsedHeight)
        val endWidth = lerp(constraints.maxWidth, predictiveBackEndWidth, predictiveBackProgress)
        val endHeight = lerp(constraints.maxHeight, predictiveBackEndHeight, predictiveBackProgress)
        val width = constraints.constrainWidth(lerp(collapsedWidth, endWidth, state.progress))
        val height = constraints.constrainHeight(lerp(collapsedHeight, endHeight, state.progress))

        val surfaceMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSurface }
        val surfacePlaceable = surfaceMeasurable.measure(Constraints.fixed(width, height))

        val inputFieldMeasurable = measurables.fastFirst { it.layoutId == LayoutIdInputField }
        val inputFieldPlaceable =
            inputFieldMeasurable.measure(Constraints.fixed(width, collapsedHeight))

        val topPadding = unconsumedInsets.getTop(this@Layout) + SearchBarVerticalPadding.roundToPx()
        val bottomPadding = SearchBarVerticalPadding.roundToPx()
        val animatedTopPadding =
            lerp(0, topPadding, min(state.progress, 1 - predictiveBackProgress))
        val animatedBottomPadding = lerp(0, bottomPadding, state.progress)

        val paddedInputFieldHeight =
            inputFieldPlaceable.height + animatedTopPadding + animatedBottomPadding
        val contentMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSearchContent }
        val contentPlaceable =
            contentMeasurable.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = (height - paddedInputFieldHeight).coerceAtLeast(0),
                )
            )

        layout(constraints.maxWidth, constraints.maxHeight) {
            fun BackEventProgress.InProgress.endOffsetX(): Int =
                (if (swipeEdge == SwipeEdge.Left) {
                        constraints.maxWidth -
                            SearchBarPredictiveBackMinMargin.roundToPx() -
                            predictiveBackEndWidth
                    } else {
                        SearchBarPredictiveBackMinMargin.roundToPx()
                    })
                    .coerceAtLeast(state.collapsedBounds.right - predictiveBackEndWidth)
                    .coerceAtMost(state.collapsedBounds.left)

            fun BackEventProgress.InProgress.endOffsetY(): Int {
                val absoluteDeltaY = this.touchY - (firstInProgressValue.value?.touchY ?: return 0)
                val relativeDeltaY = abs(absoluteDeltaY) / constraints.maxHeight

                val availableVerticalSpace =
                    ((constraints.maxHeight - predictiveBackEndHeight) / 2 -
                            SearchBarPredictiveBackMinMargin.roundToPx())
                        .coerceAtLeast(0)
                val totalOffsetY =
                    min(availableVerticalSpace, SearchBarPredictiveBackMaxOffsetY.roundToPx())
                val interpolatedOffsetY = lerp(0, totalOffsetY, relativeDeltaY)
                return (interpolatedOffsetY * sign(absoluteDeltaY).toInt() + topPadding)
                    .coerceAtMost(state.collapsedBounds.top)
            }

            val endOffsetX =
                lerp(0, lastInProgressValue.value?.endOffsetX() ?: 0, predictiveBackProgress)
            val endOffsetY =
                lerp(0, lastInProgressValue.value?.endOffsetY() ?: 0, predictiveBackProgress)
            val offsetX = lerp(state.collapsedBounds.left, endOffsetX, state.progress)
            val offsetY = lerp(state.collapsedBounds.top, endOffsetY, state.progress)

            surfacePlaceable.place(x = offsetX, y = offsetY)
            inputFieldPlaceable.place(x = offsetX, y = offsetY + animatedTopPadding)
            contentPlaceable.placeWithLayer(
                x = offsetX,
                y =
                    offsetY +
                        animatedTopPadding +
                        inputFieldPlaceable.height +
                        animatedBottomPadding,
                layerBlock = { alpha = state.progress },
            )
        }
    }
}

private fun BackEventProgress.InProgress?.transform(): Float =
    if (this == null) 0f else PredictiveBack.transform(this.progress)

private val SearchBarState.collapsedBounds: IntRect
    get() =
        collapsedCoords?.let { IntRect(offset = it.positionInWindow().round(), size = it.size) }
            ?: IntRect.Zero

@Composable
private fun DetectClickFromInteractionSource(
    interactionSource: InteractionSource,
    onClick: () -> Unit,
) {
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) onClick()
        }
    }
}





@Composable
internal fun getWindowContainerHeight(): Dp = LocalConfiguration.current.screenHeightDp.dp

private const val LayoutIdInputField = "InputField"
private const val LayoutIdSurface = "Surface"
private const val LayoutIdSearchContent = "Content"

// Measurement specs
private val SearchBarCornerRadius: Dp = SearchBarDefaults.InputFieldHeight / 2
internal val DockedExpandedTableMinHeight: Dp = 240.dp
private const val DockedExpandedTableMaxHeightScreenRatio: Float = 2f / 3f
internal val SearchBarMinWidth: Dp = 360.dp
private val SearchBarMaxWidth: Dp = 720.dp
internal val SearchBarVerticalPadding: Dp = 8.dp
// Search bar has 16dp padding between icons and start/end, while by default text field has 12dp.
private val SearchBarIconOffsetX: Dp = 4.dp
private const val SearchBarPredictiveBackMinScale: Float = 9f / 10f
private val SearchBarPredictiveBackMinMargin: Dp = 8.dp
private const val SearchBarPredictiveBackMaxOffsetXRatio: Float = 1f / 20f
private val SearchBarPredictiveBackMaxOffsetY: Dp = 24.dp

// Animation specs
private const val AnimationEnterDurationMillis: Int = MotionTokens.DurationLong4.toInt()
private const val AnimationExitDurationMillis: Int = MotionTokens.DurationMedium3.toInt()
private const val AnimationDelayMillis: Int = MotionTokens.DurationShort2.toInt()
