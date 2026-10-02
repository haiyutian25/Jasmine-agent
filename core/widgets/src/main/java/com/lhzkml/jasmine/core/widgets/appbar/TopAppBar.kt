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

package com.lhzkml.jasmine.core.widgets.appbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import com.lhzkml.jasmine.core.widgets.textfield.FloatProducer
import com.lhzkml.jasmine.core.widgets.textfield.ProvideContentColorTextStyle
import com.lhzkml.jasmine.core.widgets.bottomsheet.systemBarsForVisualComponents
import com.lhzkml.jasmine.core.widgets.tokens.AppBarSmallTokens
import com.lhzkml.jasmine.core.widgets.tokens.AppBarTokens
import com.lhzkml.jasmine.core.widgets.tokens.MotionSchemeKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.tokens.fromToken
import com.lhzkml.jasmine.core.widgets.tokens.value
import com.lhzkml.jasmine.core.widgets.motion.LocalMotionScheme
import com.lhzkml.jasmine.core.widgets.motion.value
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [Material Design small top app bar](https://m3.material.io/components/top-app-bar/overview)
 *
 * Top app bars display information and actions at the top of a screen.
 *
 * This small TopAppBar has slots for a title, navigation icon, and actions.
 *
 * ![Small top app bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/small-top-app-bar.png)
 *
 * A simple top app bar looks like:
 *
 * @sample androidx.compose.material3.samples.SimpleTopAppBar A top app bar that uses a
 *   [scrollBehavior] to customize its nested scrolling behavior when working in conjunction with a
 *   scrolling content looks like:
 * @sample androidx.compose.material3.samples.PinnedTopAppBar
 * @param title the title to be displayed in the top app bar
 * @param modifier the [Modifier] to be applied to this top app bar
 * @param navigationIcon the navigation icon displayed at the start of the top app bar. This should
 *   typically be an [IconButton] or [IconToggleButton].
 * @param actions the actions displayed at the end of the top app bar. This should typically be
 *   [IconButton]s. The default layout here is a [Row], so icons inside will be placed horizontally.
 * @param expandedHeight this app bar's height. When a specified [scrollBehavior] causes the app bar
 *   to collapse or expand, this value will represent the maximum height that the bar will be
 *   allowed to expand. This value must be specified and finite, otherwise it will be ignored and
 *   replaced with [TopAppBarDefaults.TopAppBarExpandedHeight].
 * @param windowInsets a window insets that app bar will respect.
 * @param colors [TopAppBarColors] that will be used to resolve the colors used for this top app bar
 *   in different states. See [TopAppBarDefaults.topAppBarColors].
 * @param scrollBehavior a [TopAppBarScrollBehavior] which holds various offset values that will be
 *   applied by this top app bar to set up its height and colors. A scroll behavior is designed to
 *   work in conjunction with a scrolled content to change the top app bar appearance as the content
 *   scrolls. See [TopAppBarScrollBehavior.nestedScrollConnection].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) =
    SingleRowTopAppBar(
        modifier = modifier,
        title = title,
        titleTextStyle = AppBarSmallTokens.TitleFont.value,
        subtitle = null,
        subtitleTextStyle = TextStyle.Default,
        titleHorizontalAlignment = Alignment.Start,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight =
            if (expandedHeight == Dp.Unspecified || expandedHeight == Dp.Infinity) {
                TopAppBarDefaults.TopAppBarExpandedHeight
            } else {
                expandedHeight
            },
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )

/**
 * [Material Design center-aligned small top app
 * bar](https://m3.material.io/components/top-app-bar/overview)
 *
 * Top app bars display information and actions at the top of a screen.
 *
 * This small top app bar has a header title that is horizontally aligned to the center.
 *
 * ![Center-aligned top app bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/center-aligned-top-app-bar.png)
 *
 * This CenterAlignedTopAppBar has slots for a title, navigation icon, and actions.
 *
 * A center aligned top app bar that uses a [scrollBehavior] to customize its nested scrolling
 * behavior when working in conjunction with a scrolling content looks like:
 *
 * @sample androidx.compose.material3.samples.SimpleCenterAlignedTopAppBar
 * @param title the title to be displayed in the top app bar
 * @param modifier the [Modifier] to be applied to this top app bar
 * @param navigationIcon the navigation icon displayed at the start of the top app bar. This should
 *   typically be an [IconButton] or [IconToggleButton].
 * @param actions the actions displayed at the end of the top app bar. This should typically be
 *   [IconButton]s. The default layout here is a [Row], so icons inside will be placed horizontally.
 * @param expandedHeight this app bar's height. When a specified [scrollBehavior] causes the app bar
 *   to collapse or expand, this value will represent the maximum height that the bar will be
 *   allowed to expand. This value must be specified and finite, otherwise it will be ignored and
 *   replaced with [TopAppBarDefaults.TopAppBarExpandedHeight].
 * @param windowInsets a window insets that app bar will respect.
 * @param colors [TopAppBarColors] that will be used to resolve the colors used for this top app bar
 *   in different states. See [TopAppBarDefaults.topAppBarColors].
 * @param scrollBehavior a [TopAppBarScrollBehavior] which holds various offset values that will be
 *   applied by this top app bar to set up its height and colors. A scroll behavior is designed to
 *   work in conjunction with a scrolled content to change the top app bar appearance as the content
 *   scrolls. See [TopAppBarScrollBehavior.nestedScrollConnection].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) =
    SingleRowTopAppBar(
        modifier = modifier,
        title = title,
        titleTextStyle = AppBarSmallTokens.TitleFont.value,
        subtitle = null,
        subtitleTextStyle = TextStyle.Default,
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight =
            if (expandedHeight == Dp.Unspecified || expandedHeight == Dp.Infinity) {
                TopAppBarDefaults.TopAppBarExpandedHeight
            } else {
                expandedHeight
            },
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )

/**
 * A TopAppBarScrollBehavior defines how an app bar should behave when the content under it is
 * scrolled.
 *
 * @see [TopAppBarDefaults.pinnedScrollBehavior]
 * @see [TopAppBarDefaults.enterAlwaysScrollBehavior]
 * @see [TopAppBarDefaults.exitUntilCollapsedScrollBehavior]
 */
@ExperimentalMaterial3Api
@Stable
interface TopAppBarScrollBehavior {

    /**
     * A [TopAppBarState] that is attached to this behavior and is read and updated when scrolling
     * happens.
     */
    val state: TopAppBarState

    /**
     * Indicates whether the top app bar is pinned.
     *
     * A pinned app bar will stay fixed in place when content is scrolled and will not react to any
     * drag gestures.
     */
    val isPinned: Boolean

    /**
     * An optional [AnimationSpec] that defines how the top app bar snaps to either fully collapsed
     * or fully extended state when a fling or a drag scrolled it into an intermediate position.
     */
    val snapAnimationSpec: AnimationSpec<Float>?

    /**
     * An optional [DecayAnimationSpec] that defined how to fling the top app bar when the user
     * flings the app bar itself, or the content below it.
     */
    val flingAnimationSpec: DecayAnimationSpec<Float>?

    /**
     * A [NestedScrollConnection] that should be attached to a [Modifier.nestedScroll] in order to
     * keep track of the scroll events.
     */
    val nestedScrollConnection: NestedScrollConnection
}

/** Contains default values used for the top app bar implementations. */
object TopAppBarDefaults {

    /**
     * Creates a [TopAppBarColors] for small [TopAppBar]. The default implementation animates
     * between the provided colors according to the Material Design specification.
     */
    @Composable fun topAppBarColors() = LocalCssVariables.current.defaultTopAppBarColors

    /**
     * Creates a [TopAppBarColors] for small [TopAppBar]. The default implementation animates
     * between the provided colors according to the Material Design specification.
     *
     * @param containerColor the container color
     * @param scrolledContainerColor the container color when content is scrolled behind it
     * @param navigationIconContentColor the content color used for the navigation icon
     * @param titleContentColor the content color used for the title
     * @param actionIconContentColor the content color used for actions
     * @param subtitleContentColor the content color used for the subtitle
     * @return the resulting [TopAppBarColors] used for the top app bar
     */
    @Composable
    fun topAppBarColors(
        containerColor: Color = Color.Unspecified,
        scrolledContainerColor: Color = Color.Unspecified,
        navigationIconContentColor: Color = Color.Unspecified,
        titleContentColor: Color = Color.Unspecified,
        actionIconContentColor: Color = Color.Unspecified,
        subtitleContentColor: Color = Color.Unspecified,
    ): TopAppBarColors =
        LocalCssVariables.current.defaultTopAppBarColors.copy(
            containerColor,
            scrolledContainerColor,
            navigationIconContentColor,
            titleContentColor,
            actionIconContentColor,
            subtitleContentColor,
        )

    /**
     * Creates a [TopAppBarColors] for small [TopAppBar]. The default implementation animates
     * between the provided colors according to the Material Design specification.
     *
     * @param containerColor the container color
     * @param scrolledContainerColor the container color when content is scrolled behind it
     * @param navigationIconContentColor the content color used for the navigation icon
     * @param titleContentColor the content color used for the title
     * @param actionIconContentColor the content color used for actions
     * @return the resulting [TopAppBarColors] used for the top app bar
     */
    @Deprecated(
        "Maintained for binary compatibility in favor of topAppBarColors with subtitleContentColor",
        level = DeprecationLevel.HIDDEN,
    )
    @Composable
    fun topAppBarColors(
        containerColor: Color = Color.Unspecified,
        scrolledContainerColor: Color = Color.Unspecified,
        navigationIconContentColor: Color = Color.Unspecified,
        titleContentColor: Color = Color.Unspecified,
        actionIconContentColor: Color = Color.Unspecified,
    ): TopAppBarColors =
        topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor,
            navigationIconContentColor = navigationIconContentColor,
            titleContentColor = titleContentColor,
            actionIconContentColor = actionIconContentColor,
            subtitleContentColor = titleContentColor,
        )

    internal val CssVariables.defaultTopAppBarColors: TopAppBarColors
        @Composable
        get() =
            remember(this) {
                TopAppBarColors(
                    containerColor = fromToken(AppBarTokens.ContainerColor),
                    scrolledContainerColor = fromToken(AppBarTokens.OnScrollContainerColor),
                    navigationIconContentColor = fromToken(AppBarTokens.LeadingIconColor),
                    titleContentColor = fromToken(AppBarTokens.TitleColor),
                    actionIconContentColor = fromToken(AppBarTokens.TrailingIconColor),
                    subtitleContentColor = fromToken(AppBarTokens.SubtitleColor),
                )
            }

    /** Default insets to be used and consumed by the top app bars */
    val windowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.systemBarsForVisualComponents.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )

    /**
     * Creates a [TopAppBarColors] for [CenterAlignedTopAppBar]s. The default implementation
     * animates between the provided colors according to the Material Design specification.
     */
    @Deprecated(
        "Use topAppBarColors instead",
        replaceWith = ReplaceWith("topAppBarColors()"),
        DeprecationLevel.WARNING,
    )
    @Composable
    fun centerAlignedTopAppBarColors() = LocalCssVariables.current.defaultTopAppBarColors

    /**
     * Creates a [TopAppBarColors] for [CenterAlignedTopAppBar]s. The default implementation
     * animates between the provided colors according to the Material Design specification.
     *
     * @param containerColor the container color
     * @param scrolledContainerColor the container color when content is scrolled behind it
     * @param navigationIconContentColor the content color used for the navigation icon
     * @param titleContentColor the content color used for the title
     * @param actionIconContentColor the content color used for actions
     * @return the resulting [TopAppBarColors] used for the top app bar
     */
    @Deprecated(
        "Use topAppBarColors instead",
        replaceWith =
            ReplaceWith(
                "topAppBarColors(containerColor, scrolledContainerColor, " +
                    "navigationIconContentColor, titleContentColor, actionIconContentColor)"
            ),
        DeprecationLevel.WARNING,
    )
    @Composable
    fun centerAlignedTopAppBarColors(
        containerColor: Color = Color.Unspecified,
        scrolledContainerColor: Color = Color.Unspecified,
        navigationIconContentColor: Color = Color.Unspecified,
        titleContentColor: Color = Color.Unspecified,
        actionIconContentColor: Color = Color.Unspecified,
    ): TopAppBarColors =
        LocalCssVariables.current.defaultTopAppBarColors.copy(
            containerColor,
            scrolledContainerColor,
            navigationIconContentColor,
            titleContentColor,
            actionIconContentColor,
        )

    /**
     * Returns a pinned [TopAppBarScrollBehavior] that tracks nested-scroll callbacks and updates
     * its [TopAppBarState.contentOffset] accordingly.
     *
     * The returned [TopAppBarScrollBehavior] is remembered across compositions.
     *
     * @param state the state object to be used to control or observe the top app bar's scroll
     *   state. See [rememberTopAppBarState] for a state that is remembered across compositions.
     * @param canScroll a callback used to determine whether scroll events are to be handled by this
     *   pinned [TopAppBarScrollBehavior]
     */
    @ExperimentalMaterial3Api
    @Composable
    fun pinnedScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
    ): TopAppBarScrollBehavior =
        remember(state, canScroll) { PinnedScrollBehavior(state = state, canScroll = canScroll) }

    /**
     * Returns a [TopAppBarScrollBehavior]. A top app bar that is set up with this
     * [TopAppBarScrollBehavior] will immediately collapse when the content is pulled up, and will
     * immediately appear when the content is pulled down.
     *
     * The returned [TopAppBarScrollBehavior] is remembered across compositions.
     *
     * @param state the state object to be used to control or observe the top app bar's scroll
     *   state. See [rememberTopAppBarState] for a state that is remembered across compositions.
     * @param canScroll a callback used to determine whether scroll events are to be handled by this
     *   [EnterAlwaysScrollBehavior]
     * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps
     *   to either fully collapsed or fully extended state when a fling or a drag scrolled it into
     *   an intermediate position
     * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top
     *   app bar when the user flings the app bar itself, or the content below it
     */
    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    @ExperimentalMaterial3Api
    @Composable
    fun enterAlwaysScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
        // TODO Load the motionScheme tokens from the component tokens file
        snapAnimationSpec: AnimationSpec<Float>? = MotionSchemeKeyTokens.DefaultEffects.value(),
        flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
    ): TopAppBarScrollBehavior {
        return enterAlwaysScrollBehavior(
            state = state,
            canScroll = canScroll,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec,
            reverseLayout = false,
        )
    }

    /**
     * Returns a [TopAppBarScrollBehavior]. A top app bar that is set up with this
     * [TopAppBarScrollBehavior] will immediately collapse when the content is pulled up, and will
     * immediately appear when the content is pulled down.
     *
     * The returned [TopAppBarScrollBehavior] is remembered across compositions.
     *
     * @param state the state object to be used to control or observe the top app bar's scroll
     *   state. See [rememberTopAppBarState] for a state that is remembered across compositions.
     * @param canScroll a callback used to determine whether scroll events are to be handled by this
     *   [EnterAlwaysScrollBehavior]
     * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps
     *   to either fully collapsed or fully extended state when a fling or a drag scrolled it into
     *   an intermediate position
     * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top
     *   app bar when the user flings the app bar itself, or the content below it
     * @param reverseLayout indicates that this behavior is applied to a scrollable content that has
     *   a reversed direction of scrolling and layout
     */
    @ExperimentalMaterial3Api
    @Composable
    fun enterAlwaysScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
        // TODO Load the motionScheme tokens from the component tokens file
        snapAnimationSpec: AnimationSpec<Float>? = MotionSchemeKeyTokens.DefaultEffects.value(),
        flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
        reverseLayout: Boolean = false,
    ): TopAppBarScrollBehavior =
        remember(state, canScroll, snapAnimationSpec, flingAnimationSpec, reverseLayout) {
            EnterAlwaysScrollBehavior(
                state = state,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec,
                canScroll = canScroll,
                reverseLayout = reverseLayout,
            )
        }

    /**
     * Returns a [TopAppBarScrollBehavior] that adjusts its properties to affect the colors and
     * height of the top app bar.
     *
     * A top app bar that is set up with this [TopAppBarScrollBehavior] will immediately collapse
     * when the nested content is pulled up, and will expand back the collapsed area when the
     * content is pulled all the way down.
     *
     * The returned [TopAppBarScrollBehavior] is remembered across compositions.
     *
     * @param state the state object to be used to control or observe the top app bar's scroll
     *   state. See [rememberTopAppBarState] for a state that is remembered across compositions.
     * @param canScroll a callback used to determine whether scroll events are to be handled by this
     *   [ExitUntilCollapsedScrollBehavior]
     * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps
     *   to either fully collapsed or fully extended state when a fling or a drag scrolled it into
     *   an intermediate position
     * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top
     *   app bar when the user flings the app bar itself, or the content below it
     */
    @ExperimentalMaterial3Api
    @Composable
    fun exitUntilCollapsedScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
        // TODO Load the motionScheme tokens from the component tokens file
        snapAnimationSpec: AnimationSpec<Float>? = MotionSchemeKeyTokens.DefaultEffects.value(),
        flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
    ): TopAppBarScrollBehavior =
        remember(state, canScroll, snapAnimationSpec, flingAnimationSpec) {
            ExitUntilCollapsedScrollBehavior(
                state = state,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec,
                canScroll = canScroll,
            )
        }

    /** The default expanded height of a [TopAppBar] and the [CenterAlignedTopAppBar]. */
    val TopAppBarExpandedHeight: Dp = AppBarSmallTokens.ContainerHeight
}

/**
 * Creates a [TopAppBarState] that is remembered across compositions.
 *
 * @param initialHeightOffsetLimit the initial value for [TopAppBarState.heightOffsetLimit], which
 *   represents the pixel limit that a top app bar is allowed to collapse when the scrollable
 *   content is scrolled
 * @param initialHeightOffset the initial value for [TopAppBarState.heightOffset]. The initial
 *   offset height offset should be between zero and [initialHeightOffsetLimit].
 * @param initialContentOffset the initial value for [TopAppBarState.contentOffset]
 */
@Composable
fun rememberTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
): TopAppBarState {
    return rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(initialHeightOffsetLimit, initialHeightOffset, initialContentOffset)
    }
}

/**
 * A state object that can be hoisted to control and observe the top app bar state. The state is
 * read and updated by a [TopAppBarScrollBehavior] implementation.
 *
 * In most cases, this state will be created via [rememberTopAppBarState].
 *
 * @param initialHeightOffsetLimit the initial value for [TopAppBarState.heightOffsetLimit]
 * @param initialHeightOffset the initial value for [TopAppBarState.heightOffset]
 * @param initialContentOffset the initial value for [TopAppBarState.contentOffset]
 */
@Stable
class TopAppBarState(
    initialHeightOffsetLimit: Float,
    initialHeightOffset: Float,
    initialContentOffset: Float,
) {

    /**
     * The top app bar's height offset limit in pixels, which represents the limit that a top app
     * bar is allowed to collapse to.
     *
     * Use this limit to coerce the [heightOffset] value when it's updated.
     */
    var heightOffsetLimit = initialHeightOffsetLimit

    /**
     * The top app bar's current height offset in pixels. This height offset is applied to the fixed
     * height of the app bar to control the displayed height when content is being scrolled.
     *
     * Updates to the [heightOffset] value are coerced between zero and [heightOffsetLimit].
     */
    var heightOffset: Float
        get() = _heightOffset.floatValue
        set(newOffset) {
            _heightOffset.floatValue =
                newOffset.coerceIn(minimumValue = heightOffsetLimit, maximumValue = 0f)
        }

    /**
     * The total offset of the content scrolled under the top app bar.
     *
     * The content offset is used to compute the [overlappedFraction], which can later be read by an
     * implementation.
     *
     * This value is updated by a [TopAppBarScrollBehavior] whenever a nested scroll connection
     * consumes scroll events. A common implementation would update the value to be the sum of all
     * [NestedScrollConnection.onPostScroll] `consumed.y` values.
     */
    var contentOffset by mutableFloatStateOf(initialContentOffset)

    /**
     * A value that represents the collapsed height percentage of the app bar.
     *
     * A `0.0` represents a fully expanded bar, and `1.0` represents a fully collapsed bar (computed
     * as [heightOffset] / [heightOffsetLimit]).
     */
    val collapsedFraction: Float
        get() =
            if (heightOffsetLimit != 0f) {
                heightOffset / heightOffsetLimit
            } else {
                0f
            }

    /**
     * A value that represents the percentage of the app bar area that is overlapping with the
     * content scrolled behind it.
     *
     * A `0.0` indicates that the app bar does not overlap any content, while `1.0` indicates that
     * the entire visible app bar area overlaps the scrolled content.
     */
    val overlappedFraction: Float
        get() =
            if (heightOffsetLimit != 0f) {
                1 -
                    ((heightOffsetLimit - contentOffset).coerceIn(
                        minimumValue = heightOffsetLimit,
                        maximumValue = 0f,
                    ) / heightOffsetLimit)
            } else {
                0f
            }

    companion object {
        /** The default [Saver] implementation for [TopAppBarState]. */
        val Saver: Saver<TopAppBarState, *> =
            listSaver(
                save = { listOf(it.heightOffsetLimit, it.heightOffset, it.contentOffset) },
                restore = {
                    TopAppBarState(
                        initialHeightOffsetLimit = it[0],
                        initialHeightOffset = it[1],
                        initialContentOffset = it[2],
                    )
                },
            )
    }

    private var _heightOffset = mutableFloatStateOf(initialHeightOffset)
}

/**
 * Represents the colors used by a top app bar in different states. This implementation animates the
 * container color according to the top app bar scroll state. It does not animate the leading,
 * headline, or trailing colors.
 *
 * @param containerColor the color used for the background of this TopAppBar. Use
 *   [Color.Transparent] to have no color.
 * @param scrolledContainerColor the container color when content is scrolled behind it
 * @param navigationIconContentColor the content color used for the navigation icon
 * @param titleContentColor the content color used for the title
 * @param actionIconContentColor the content color used for actions
 * @param subtitleContentColor the content color used for a subtitle
 * @constructor create an instance with arbitrary colors, see [TopAppBarColors] for a factory method
 *   using the default material3 spec
 */
@Stable
class TopAppBarColors(
    val containerColor: Color,
    val scrolledContainerColor: Color,
    val navigationIconContentColor: Color,
    val titleContentColor: Color,
    val actionIconContentColor: Color,
    val subtitleContentColor: Color,
) {
    @Deprecated(
        "Use the TopAppBarColors constructor with subtitleContentColor",
        replaceWith =
            ReplaceWith(
                "TopAppBarColors(containerColor, scrolledContainerColor," +
                    "navigationIconContentColor, titleContentColor, actionIconContentColor, " +
                    "subtitleContentColor)"
            ),
        DeprecationLevel.WARNING,
    )
    constructor(
        containerColor: Color,
        scrolledContainerColor: Color,
        navigationIconContentColor: Color,
        titleContentColor: Color,
        actionIconContentColor: Color,
    ) : this(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        navigationIconContentColor = navigationIconContentColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        subtitleContentColor = titleContentColor,
    )

    /**
     * Returns a copy of this TopAppBarColors, optionally overriding some of the values. This uses
     * the Color.Unspecified to mean “use the value from the source”
     */
    fun copy(
        containerColor: Color = this.containerColor,
        scrolledContainerColor: Color = this.scrolledContainerColor,
        navigationIconContentColor: Color = this.navigationIconContentColor,
        titleContentColor: Color = this.titleContentColor,
        actionIconContentColor: Color = this.actionIconContentColor,
        subtitleContentColor: Color = this.subtitleContentColor,
    ) =
        TopAppBarColors(
            containerColor.takeOrElse { this.containerColor },
            scrolledContainerColor.takeOrElse { this.scrolledContainerColor },
            navigationIconContentColor.takeOrElse { this.navigationIconContentColor },
            titleContentColor.takeOrElse { this.titleContentColor },
            actionIconContentColor.takeOrElse { this.actionIconContentColor },
            subtitleContentColor.takeOrElse { this.subtitleContentColor },
        )

    /**
     * Represents the container color used for the top app bar.
     *
     * A [colorTransitionFraction] provides a percentage value that can be used to generate a color.
     * Usually, an app bar implementation will pass in a [colorTransitionFraction] read from the
     * [TopAppBarState.collapsedFraction] or the [TopAppBarState.overlappedFraction].
     *
     * @param colorTransitionFraction a `0.0` to `1.0` value that represents a color transition
     *   percentage
     */
    @Stable
    internal fun containerColor(colorTransitionFraction: Float): Color {
        return lerp(
            containerColor,
            scrolledContainerColor,
            FastOutLinearInEasing.transform(colorTransitionFraction),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is TopAppBarColors) return false

        if (containerColor != other.containerColor) return false
        if (scrolledContainerColor != other.scrolledContainerColor) return false
        if (navigationIconContentColor != other.navigationIconContentColor) return false
        if (titleContentColor != other.titleContentColor) return false
        if (actionIconContentColor != other.actionIconContentColor) return false
        if (subtitleContentColor != other.subtitleContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + scrolledContainerColor.hashCode()
        result = 31 * result + navigationIconContentColor.hashCode()
        result = 31 * result + titleContentColor.hashCode()
        result = 31 * result + actionIconContentColor.hashCode()
        result = 31 * result + subtitleContentColor.hashCode()

        return result
    }
}

/**
 * A single-row top app bar that is designed to be called by the small and center aligned top app
 * bar composables.
 *
 * This SingleRowTopAppBar has slots for a title, subtitle, navigation icon, and actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleRowTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    titleHorizontalAlignment: Alignment.Horizontal,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    expandedHeight: Dp,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    val scope =
        SingleRowTopAppBarOverrideScope(
            modifier = modifier,
            title = title,
            titleTextStyle = titleTextStyle,
            subtitle = subtitle,
            subtitleTextStyle = subtitleTextStyle,
            titleHorizontalAlignment = titleHorizontalAlignment,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = expandedHeight,
            windowInsets = windowInsets,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    with(LocalSingleRowTopAppBarOverride.current) { scope.SingleRowTopAppBar() }
}

/**
 * Provides the default behavior of the [SingleRowTopAppBar] component. This implementation is used
 * when no override is specified.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal object DefaultSingleRowTopAppBarOverride : SingleRowTopAppBarOverride {
    @Composable
    override fun SingleRowTopAppBarOverrideScope.SingleRowTopAppBar() {
        require(expandedHeight.isSpecified && expandedHeight.isFinite) {
            "The expandedHeight is expected to be specified and finite"
        }

        // Obtain the container color from the TopAppBarColors using the `overlapFraction`. This
        // ensures that the colors will adjust whether the app bar behavior is pinned or scrolled.
        // This may potentially animate or interpolate a transition between the container-color and
        // the container's scrolled-color according to the app bar's scroll state.
        val targetColor by
            remember(colors, scrollBehavior) {
                derivedStateOf {
                    val overlappingFraction = scrollBehavior?.state?.overlappedFraction ?: 0f
                    colors.containerColor(if (overlappingFraction > 0.01f) 1f else 0f)
                }
            }

        val appBarContainerColor =
            animateColorAsState(
                targetColor,
                // TODO Load the motionScheme tokens from the component tokens file
                animationSpec = MotionSchemeKeyTokens.DefaultEffects.value(),
            )

        // Wrap the given actions in a Row.
        val actionsRow =
            @Composable {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }

        // Set up support for resizing the top app bar when vertically dragging the bar itself.
        val appBarDragModifier =
            if (scrollBehavior != null && !scrollBehavior.isPinned) {
                Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state =
                        rememberDraggableState { delta ->
                            scrollBehavior.state.heightOffset += delta
                        },
                    onDragStopped = { velocity ->
                        settleAppBar(
                            scrollBehavior.state,
                            velocity,
                            scrollBehavior.flingAnimationSpec,
                            scrollBehavior.snapAnimationSpec,
                        )
                    },
                )
            } else {
                Modifier
            }

        // Compose a Surface with a TopAppBarLayout content.
        // The surface's background color is animated as specified above.
        // The height of the app bar is determined by subtracting the bar's height offset from the
        // app bar's defined constant height value (i.e. the ContainerHeight token).
        Box(
            modifier =
                modifier
                    .then(appBarDragModifier)
                    .drawBehind {
                        val color = appBarContainerColor.value
                        if (color != Color.Unspecified) {
                            drawRect(color = color)
                        }
                    }
                    .semantics { isTraversalGroup = true }
                    .pointerInput(Unit) {}
        ) {
            TopAppBarLayout(
                modifier =
                    Modifier.windowInsetsPadding(windowInsets)
                        // clip after padding so we don't show the title over the inset area
                        .clipToBounds()
                        .adjustHeightOffsetLimit(scrollBehavior),
                scrolledOffset = { scrollBehavior?.state?.heightOffset ?: 0f },
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                subtitleContentColor = colors.subtitleContentColor,
                title = title,
                titleTextStyle = titleTextStyle,
                subtitle = subtitle,
                subtitleTextStyle = subtitleTextStyle,
                titleAlpha = { 1f },
                titleVerticalArrangement = Arrangement.Center,
                titleHorizontalAlignment = titleHorizontalAlignment,
                titleBottomPadding = 0,
                hideTitleSemantics = false,
                navigationIcon = navigationIcon,
                actions = actionsRow,
                height = expandedHeight,
            )
        }
    }
}

/**
 * Interface that allows libraries to override the behavior of single-row `TopAppBar` components.
 *
 * To override this component, implement the member function of this interface, then provide the
 * implementation to [LocalSingleRowTopAppBarOverride] in the Compose hierarchy.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal interface SingleRowTopAppBarOverride {
    /** Behavior function that is called by the [SingleRowTopAppBar] component. */
    @Composable fun SingleRowTopAppBarOverrideScope.SingleRowTopAppBar()
}

/**
 * Parameters available to [SingleRowTopAppBar].
 *
 * @property modifier the [Modifier] to be applied to this top app bar
 * @property title the title to be displayed in the top app bar typically be an [IconButton] or
 *   [IconToggleButton].
 * @property titleTextStyle the title's text style
 * @property subtitle a lambda for providing an optional subtitle to be displayed in the top app bar
 *   in collapsed and expanded states.
 * @property subtitleTextStyle the subtitle's text style
 * @property titleHorizontalAlignment the title's horizontal alignment
 * @property navigationIcon the navigation icon displayed at the start of the top app bar. This
 *   should typically be an [IconButton] or [IconToggleButton].
 * @property actions the actions displayed at the end of the top app bar. This should typically be
 *   [IconButton]s. The default layout here is a [Row], so icons inside will be placed horizontally.
 * @property expandedHeight this app bar's height. When a specified [scrollBehavior] causes the app
 *   bar to collapse or expand, this value will represent the maximum height that the bar will be
 *   allowed to expand. This value must be specified and finite, otherwise it will be ignored and
 *   replaced with [TopAppBarDefaults.TopAppBarExpandedHeight].
 * @property windowInsets a window insets that app bar will respect.
 * @property colors [TopAppBarColors] that will be used to resolve the colors used for this top app
 *   bar in different states. See [TopAppBarDefaults.topAppBarColors].
 * @property scrollBehavior a [TopAppBarScrollBehavior] which holds various offset values that will
 *   be applied by this top app bar to set up its height and colors. A scroll behavior is designed
 *   to work in conjunction with a scrolled content to change the top app bar appearance as the
 *   content scrolls. See [TopAppBarScrollBehavior.nestedScrollConnection].
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class SingleRowTopAppBarOverrideScope
internal constructor(
    val modifier: Modifier,
    val title: @Composable () -> Unit,
    val titleTextStyle: TextStyle,
    val subtitle: (@Composable () -> Unit)?,
    val subtitleTextStyle: TextStyle,
    val titleHorizontalAlignment: Alignment.Horizontal,
    val navigationIcon: @Composable () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
    val expandedHeight: Dp,
    val windowInsets: WindowInsets,
    val colors: TopAppBarColors,
    val scrollBehavior: TopAppBarScrollBehavior?,
)

/** CompositionLocal containing the currently-selected [SingleRowTopAppBarOverride]. */
@OptIn(ExperimentalMaterial3Api::class)
internal val LocalSingleRowTopAppBarOverride:
    ProvidableCompositionLocal<SingleRowTopAppBarOverride> =
    compositionLocalOf {
        DefaultSingleRowTopAppBarOverride
    }

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.adjustHeightOffsetLimit(scrollBehavior: TopAppBarScrollBehavior?) =
    scrollBehavior?.state?.let {
        onSizeChanged { size ->
            val offset = size.height.toFloat() - it.heightOffset
            it.heightOffsetLimit = -offset
        }
    } ?: this

/**
 * The base [Layout] for all top app bars. This function lays out a top app bar navigation icon
 * (leading icon), a title (header), and action icons (trailing icons). Note that the navigation and
 * the actions are optional.
 *
 * @param modifier a [Modifier]
 * @param scrolledOffset a [FloatProducer] that provides the app bar offset in pixels (note that
 *   when the app bar is scrolled, the lambda will output negative values)
 * @param navigationIconContentColor the content color that will be applied via a
 *   [LocalContentColor] when composing the navigation icon
 * @param titleContentColor the color that will be applied via a [LocalContentColor] when composing
 *   the title
 * @param subtitleContentColor the color that will be applied via a [LocalContentColor] when
 *   composing the subtitle
 * @param actionIconContentColor the content color that will be applied via a [LocalContentColor]
 *   when composing the action icons
 * @param title the top app bar title (header)
 * @param titleTextStyle the title's text style
 * @param titleAlpha the title's alpha
 * @param titleVerticalArrangement the title's vertical arrangement
 * @param titleHorizontalAlignment the title's horizontal alignment
 * @param titleBottomPadding the title's bottom padding
 * @param hideTitleSemantics hides the title node from the semantic tree. Apply this boolean when
 *   this layout is part of a a two-rows app bar to hide the title's semantics from accessibility
 *   services. This is needed to avoid having multiple titles visible to accessibility services at
 *   the same time, when animating between collapsed / expanded states.
 * @param navigationIcon a navigation icon [Composable]
 * @param actions actions [Composable]
 * @param height this app bar's requested height
 */
@Composable
private fun TopAppBarLayout(
    modifier: Modifier,
    scrolledOffset: FloatProducer,
    navigationIconContentColor: Color,
    titleContentColor: Color,
    subtitleContentColor: Color,
    actionIconContentColor: Color,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    titleAlpha: () -> Float,
    titleVerticalArrangement: Arrangement.Vertical,
    titleHorizontalAlignment: Alignment.Horizontal,
    titleBottomPadding: Int,
    hideTitleSemantics: Boolean,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    height: Dp,
) {
    Layout(
        {
            Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides navigationIconContentColor,
                    content = navigationIcon,
                )
            }
            if (subtitle != null) {
                Column(
                    modifier =
                        Modifier.layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) Modifier.clearAndSetSemantics {}
                                else Modifier
                            )
                            .graphicsLayer { alpha = titleAlpha() },
                    horizontalAlignment = titleHorizontalAlignment,
                ) {
                    ProvideContentColorTextStyle(
                        contentColor = titleContentColor,
                        textStyle = titleTextStyle,
                        content = title,
                    )
                    ProvideContentColorTextStyle(
                        contentColor = subtitleContentColor,
                        textStyle = subtitleTextStyle,
                        content = subtitle,
                    )
                }
            } else { // TODO(b/352770398): Workaround to maintain compatibility
                Box(
                    modifier =
                        Modifier.layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) Modifier.clearAndSetSemantics {}
                                else Modifier
                            )
                            .graphicsLayer { alpha = titleAlpha() }
                ) {
                    ProvideContentColorTextStyle(
                        contentColor = titleContentColor,
                        textStyle = titleTextStyle,
                        content = title,
                    )
                }
            }
            Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides actionIconContentColor,
                    content = actions,
                )
            }
        },
        modifier = modifier,
        measurePolicy =
            remember(
                scrolledOffset,
                titleVerticalArrangement,
                titleHorizontalAlignment,
                titleBottomPadding,
                height,
            ) {
                TopAppBarMeasurePolicy(
                    scrolledOffset,
                    titleVerticalArrangement,
                    titleHorizontalAlignment,
                    titleBottomPadding,
                    height,
                )
            },
    )
}

private class TopAppBarMeasurePolicy(
    val scrolledOffset: FloatProducer,
    val titleVerticalArrangement: Arrangement.Vertical,
    val titleHorizontalAlignment: Alignment.Horizontal,
    val titleBottomPadding: Int,
    val height: Dp,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0))
        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = maxTitleWidth))

        // Locate the title's baseline.
        val titleBaseline =
            if (titlePlaceable[LastBaseline] != AlignmentLine.Unspecified) {
                titlePlaceable[LastBaseline]
            } else {
                0
            }

        // Subtract the scrolledOffset from the maxHeight. The scrolledOffset is expected to be
        // equal or smaller than zero.
        val scrolledOffsetValue = scrolledOffset()
        val heightOffset = if (scrolledOffsetValue.isNaN()) 0 else scrolledOffsetValue.roundToInt()

        val maxLayoutHeight = max(height.roundToPx(), titlePlaceable.height)
        val layoutHeight =
            if (constraints.maxHeight == Constraints.Infinity) {
                maxLayoutHeight
            } else {
                (maxLayoutHeight + heightOffset).coerceAtLeast(0)
            }

        return placeTopAppBar(
            constraints,
            layoutHeight,
            maxLayoutHeight,
            navigationIconPlaceable,
            titlePlaceable,
            actionIconsPlaceable,
            titleBaseline,
        )
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ) = measurables.fastSumBy { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        return max(
            height.roundToPx(),
            measurables.fastMaxOfOrNull { it.minIntrinsicHeight(width) } ?: 0,
        )
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ) = measurables.fastSumBy { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        return max(
            height.roundToPx(),
            measurables.fastMaxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0,
        )
    }

    private fun MeasureScope.placeTopAppBar(
        constraints: Constraints,
        layoutHeight: Int,
        maxLayoutHeight: Int,
        navigationIconPlaceable: Placeable,
        titlePlaceable: Placeable,
        actionIconsPlaceable: Placeable,
        titleBaseline: Int,
    ): MeasureResult =
        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = (layoutHeight - navigationIconPlaceable.height) / 2,
            )

            titlePlaceable.let {
                val start = max(TopAppBarTitleInset.roundToPx(), navigationIconPlaceable.width)
                val end = actionIconsPlaceable.width

                // Align using the maxWidth. We will adjust the position later according to the
                // start and end. This is done to ensure that a center alignment is still maintained
                // when the start and end have different widths. Note that the title is centered
                // relative to the entire app bar width, and not just centered between the
                // navigation icon and the actions.
                var titleX =
                    titleHorizontalAlignment.align(
                        size = titlePlaceable.width,
                        space = constraints.maxWidth,
                        // Using Ltr as we call placeRelative later on.
                        layoutDirection = LayoutDirection.Ltr,
                    )
                // Reposition the title based on the start and the end (i.e. the navigation and
                // action widths).
                if (titleX < start) {
                    titleX += (start - titleX)
                } else if (titleX + titlePlaceable.width > constraints.maxWidth - end) {
                    titleX += ((constraints.maxWidth - end) - (titleX + titlePlaceable.width))
                }

                // The titleVerticalArrangement is always one of Center or Bottom.
                val titleY =
                    when (titleVerticalArrangement) {
                        Arrangement.Center -> (layoutHeight - titlePlaceable.height) / 2
                        // Apply bottom padding from the title's baseline only when the Arrangement
                        // is "Bottom".
                        Arrangement.Bottom ->
                            if (titleBottomPadding == 0) {
                                layoutHeight - titlePlaceable.height
                            } else {
                                // Calculate the actual padding from the bottom of the title, taking
                                // into account its baseline.
                                val paddingFromBottom =
                                    titleBottomPadding - (titlePlaceable.height - titleBaseline)
                                // Adjust the bottom padding to a smaller number if there is no room
                                // to fit the title.
                                val heightWithPadding = paddingFromBottom + titlePlaceable.height
                                val adjustedBottomPadding =
                                    if (heightWithPadding > maxLayoutHeight) {
                                        paddingFromBottom - (heightWithPadding - maxLayoutHeight)
                                    } else {
                                        paddingFromBottom
                                    }

                                layoutHeight - titlePlaceable.height - max(0, adjustedBottomPadding)
                            }
                        // Arrangement.Top
                        else -> 0
                    }

                it.placeRelative(titleX, titleY)
            }

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = (layoutHeight - actionIconsPlaceable.height) / 2,
            )
        }
}

/**
 * Returns a [TopAppBarScrollBehavior] that only adjusts its content offset, without adjusting any
 * properties that affect the height of a top app bar.
 *
 * @param state a [TopAppBarState]
 * @param canScroll a callback used to determine whether scroll events are to be handled by this
 *   [PinnedScrollBehavior]
 */
@OptIn(ExperimentalMaterial3Api::class)
private class PinnedScrollBehavior(
    override val state: TopAppBarState,
    val canScroll: () -> Boolean = { true },
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = true
    override val snapAnimationSpec: AnimationSpec<Float>? = null
    override val flingAnimationSpec: DecayAnimationSpec<Float>? = null
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f) {
                    // Reset the total content offset to zero when scrolling all the way down.
                    // This will eliminate some float precision inaccuracies.
                    state.contentOffset = 0f
                }
                return super.onPostFling(consumed, available)
            }
        }
}

/**
 * A [TopAppBarScrollBehavior] that adjusts its properties to affect the colors and height of a top
 * app bar.
 *
 * A top app bar that is set up with this [TopAppBarScrollBehavior] will immediately collapse when
 * the nested content is pulled up, and will immediately appear when the content is pulled down.
 *
 * @param state a [TopAppBarState]
 * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps to
 *   either fully collapsed or fully extended state when a fling or a drag scrolled it into an
 *   intermediate position
 * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top app
 *   bar when the user flings the app bar itself, or the content below it
 * @param canScroll a callback used to determine whether scroll events are to be handled by this
 *   [EnterAlwaysScrollBehavior]
 * @param reverseLayout indicates that this behavior is applied to a scrollable content that has a
 *   reversed direction of scrolling and layout
 */
@OptIn(ExperimentalMaterial3Api::class)
private class EnterAlwaysScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
    val reverseLayout: Boolean = false,
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = false
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll()) return Offset.Zero
                val prevHeightOffset = state.heightOffset
                state.heightOffset += available.y
                // The state's heightOffset is coerce in a minimum value of heightOffsetLimit and a
                // maximum value 0f, so we check if its value was actually changed after the
                // available.y was added to it in order to tell if the top app bar is currently
                // collapsing or expanding.
                // Note that when the content was set with a revered layout, we always return a
                // zero offset.
                return if (!reverseLayout && prevHeightOffset != state.heightOffset) {
                    // We're in the middle of top app bar collapse or expand.
                    // Consume only the scroll on the Y axis.
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y
                if (!reverseLayout) state.heightOffset += consumed.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (
                    available.y > 0f &&
                        (state.heightOffset == 0f || state.heightOffset == state.heightOffsetLimit)
                ) {
                    // Reset the total content offset to zero when scrolling all the way down.
                    // This will eliminate some float precision inaccuracies.
                    state.contentOffset = 0f
                }
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(state, available.y, flingAnimationSpec, snapAnimationSpec)
            }
        }
}

/**
 * A [TopAppBarScrollBehavior] that adjusts its properties to affect the colors and height of a top
 * app bar.
 *
 * A top app bar that is set up with this [TopAppBarScrollBehavior] will immediately collapse when
 * the nested content is pulled up, and will expand back the collapsed area when the content is
 * pulled all the way down.
 *
 * @param state a [TopAppBarState]
 * @param snapAnimationSpec an optional [AnimationSpec] that defines how the top app bar snaps to
 *   either fully collapsed or fully extended state when a fling or a drag scrolled it into an
 *   intermediate position
 * @param flingAnimationSpec an optional [DecayAnimationSpec] that defined how to fling the top app
 *   bar when the user flings the app bar itself, or the content below it
 * @param canScroll a callback used to determine whether scroll events are to be handled by this
 *   [ExitUntilCollapsedScrollBehavior]
 */
@OptIn(ExperimentalMaterial3Api::class)
private class ExitUntilCollapsedScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = false
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Don't intercept if scrolling down.
                if (!canScroll() || available.y > 0f) return Offset.Zero

                val prevHeightOffset = state.heightOffset
                state.heightOffset = state.heightOffset + available.y
                return if (prevHeightOffset != state.heightOffset) {
                    // We're in the middle of top app bar collapse or expand.
                    // Consume only the scroll on the Y axis.
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y

                if (available.y < 0f || consumed.y < 0f) {
                    // When scrolling up, just update the state's height offset.
                    val oldHeightOffset = state.heightOffset
                    state.heightOffset = state.heightOffset + consumed.y
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }

                if (available.y > 0f) {
                    // Adjust the height offset in case the consumed delta Y is less than what was
                    // recorded as available delta Y in the pre-scroll.
                    val oldHeightOffset = state.heightOffset
                    state.heightOffset = state.heightOffset + available.y
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0) {
                    // Reset the total content offset to zero when scrolling all the way down. This
                    // will eliminate some float precision inaccuracies.
                    state.contentOffset = 0f
                }
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(state, available.y, flingAnimationSpec, snapAnimationSpec)
            }
        }
}

/**
 * Settles the app bar by flinging, in case the given velocity is greater than zero, and snapping
 * after the fling settles.
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    // In case there is an initial velocity that was left after a previous user fling, animate to
    // continue the motion to expand or collapse the app bar.
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
            flingAnimationSpec
        ) {
            val delta = value - lastValue
            val initialHeightOffset = state.heightOffset
            state.heightOffset = initialHeightOffset + delta
            val consumed = abs(initialHeightOffset - state.heightOffset)
            lastValue = value
            remainingVelocity = this.velocity
            // avoid rounding errors and stop if anything is unconsumed
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
    }
    // Snap if animation specs were provided.
    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) {
                    0f
                } else {
                    state.heightOffsetLimit
                },
                animationSpec = snapAnimationSpec,
            ) {
                state.heightOffset = value
            }
        }
    }

    return Velocity(0f, remainingVelocity)
}
internal val TopTitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)
private val TopAppBarHorizontalPadding = 4.dp

// A title inset when the App-Bar is a Medium or Large one. Also used to size a spacer when the
// navigation icon is missing.
private val TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding
