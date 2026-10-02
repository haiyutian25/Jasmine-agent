// 本项目自有的组件代码（移植自 AndroidX Material3 1.4.0 的 IconButtonDefaults.kt 后自行维护），不再跟随上游生成，可直接改。
// 上游位置：androidx/compose/material3/IconButtonDefaults.kt —— 各变体的默认色 / 边框 / 形状 / 尺寸。
// 差异：默认色 / 边框 / 形状 / 尺寸都直接读自有主题（`CssVariables`）与自有令牌表（不再经角色键映射）；
// 上游那套包级 `…Cached` 缓存按本库惯例去掉（直接返回，行为一致）。
/*
 * Copyright 2025 The Android Open Source Project
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

package com.lhzkml.jasmine.core.widgets.button

import androidx.compose.foundation.BorderStroke
import com.lhzkml.jasmine.core.widgets.tokens.FilledIconButtonTokens
import com.lhzkml.jasmine.core.widgets.tokens.FilledTonalIconButtonTokens
import com.lhzkml.jasmine.core.widgets.tokens.OutlinedIconButtonTokens
import com.lhzkml.jasmine.core.widgets.tokens.SmallIconButtonTokens
import com.lhzkml.jasmine.core.widgets.tokens.StandardIconButtonTokens
import com.lhzkml.jasmine.core.widgets.tokens.value
import androidx.compose.runtime.Composable
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsContentColor
import com.lhzkml.jasmine.core.ui.theme.contentColorFor
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsShapes
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline

/** Contains the default values for all four icon and icon toggle button types. */
object IconButtonDefaults {
    /**
     * Contains the default values used by [IconButton]. [LocalWidgetsContentColor] will be applied to the
     * icon and down the UI tree.
     *
     * See [iconButtonVibrantColors] for default values that applies the recommended high contrast
     * colors.
     */
    @Composable
    fun iconButtonColors(): IconButtonColors {
        val contentColor = LocalWidgetsContentColor.current
        val colors = LocalCssVariables.current.defaultIconButtonColors(contentColor)
        return if (colors.contentColor == contentColor) {
            colors
        } else {
            colors.copy(
                contentColor = contentColor,
                disabledContentColor =
                    contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
            )
        }
    }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a [IconButton].
     * [LocalWidgetsContentColor] will be applied to the icon and down the UI tree unless a custom
     * [contentColor] is provided.
     *
     * See [iconButtonVibrantColors] for default values that applies the recommended high contrast
     * colors.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled. By default, this will
     *   use the current LocalWidgetsContentColor value.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun iconButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalWidgetsContentColor.current,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
    ): IconButtonColors =
        LocalCssVariables.current
            .defaultIconButtonColors(LocalWidgetsContentColor.current)
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
            )

    internal fun CssVariables.defaultIconButtonColors(localContentColor: Color): IconButtonColors {
        return run {
                IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = localContentColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            localContentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
                    )
            }
    }

    /**
     * Creates a [IconButtonColors] that represents the recommended high contrast colors used in an
     * [IconButton].
     *
     * See [iconButtonColors] for default values that applies [LocalWidgetsContentColor] to the icon and
     * down the UI tree.
     */
    @Composable
    fun iconButtonVibrantColors(): IconButtonColors =
        LocalCssVariables.current.defaultIconButtonVibrantColors()

    /**
     * Creates a [IconButtonColors] that represents the recommended high contrast colors used in an
     * [IconButton].
     *
     * See [iconButtonColors] for default values that applies [LocalWidgetsContentColor] to the icon and
     * down the UI tree.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun iconButtonVibrantColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
    ): IconButtonColors =
        LocalCssVariables.current
            .defaultIconButtonVibrantColors()
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
            )

    internal fun CssVariables.defaultIconButtonVibrantColors(): IconButtonColors {
        return run {
                IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = StandardIconButtonTokens.Color(this),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            StandardIconButtonTokens.DisabledColor(this)
                                .copy(alpha = StandardIconButtonTokens.DisabledOpacity),
                    )
            }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [IconToggleButton]. [LocalWidgetsContentColor] will be applied to the icon and down the UI tree.
     *
     * See [iconToggleButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     */
    @Composable
    fun iconToggleButtonColors(): IconToggleButtonColors {
        val contentColor = LocalWidgetsContentColor.current
        val colors = LocalCssVariables.current.defaultIconToggleButtonColors(contentColor)
        if (colors.contentColor == contentColor) {
            return colors
        } else {
            return colors.copy(
                contentColor = contentColor,
                disabledContentColor =
                    contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
            )
        }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [IconToggleButton]. [LocalWidgetsContentColor] will be applied to the icon and down the UI tree
     * unless a custom [contentColor] is provided.
     *
     * See [iconToggleButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun iconToggleButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalWidgetsContentColor.current,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = Color.Unspecified,
    ): IconToggleButtonColors =
        LocalCssVariables.current
            .defaultIconToggleButtonColors(LocalWidgetsContentColor.current)
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            )

    internal fun CssVariables.defaultIconToggleButtonColors(
        localContentColor: Color
    ): IconToggleButtonColors {
        return run {
                IconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = localContentColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            localContentColor.copy(
                                alpha = StandardIconButtonTokens.DisabledOpacity
                            ),
                        checkedContainerColor = Color.Transparent,
                        checkedContentColor = StandardIconButtonTokens.SelectedColor(this),
                    )
            }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the recommended high contrast colors used
     * in a [IconToggleButton]. See [iconToggleButtonColors] for default values that applies
     * [LocalWidgetsContentColor] to the icon and down the UI tree.
     */
    @Composable
    fun iconToggleButtonVibrantColors(): IconToggleButtonColors =
        LocalCssVariables.current.defaultIconToggleButtonVibrantColors()

    /**
     * Creates a [IconToggleButtonColors] that represents the recommended high contrast colors used
     * in a [IconToggleButton].
     *
     * See [iconToggleButtonColors] for default values that applies [LocalWidgetsContentColor] to the icon
     * and down the UI tree.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun iconToggleButtonVibrantColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = StandardIconButtonTokens.DisabledOpacity),
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = Color.Unspecified,
    ): IconToggleButtonColors =
        LocalCssVariables.current
            .defaultIconToggleButtonVibrantColors()
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            )

    internal fun CssVariables.defaultIconToggleButtonVibrantColors(): IconToggleButtonColors {
        return run {
                IconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = StandardIconButtonTokens.UnselectedColor(this),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            StandardIconButtonTokens.DisabledColor(this)
                                .copy(alpha = StandardIconButtonTokens.DisabledOpacity),
                        checkedContainerColor = Color.Transparent,
                        checkedContentColor = StandardIconButtonTokens.SelectedColor(this),
                    )
            }
    }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a [FilledIconButton].
     */
    @Composable
    fun filledIconButtonColors(): IconButtonColors =
        LocalCssVariables.current.defaultFilledIconButtonColors

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a [FilledIconButton].
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun filledIconButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalCssVariables.current.contentColorFor(containerColor),
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color = Color.Unspecified,
    ): IconButtonColors =
        LocalCssVariables.current.defaultFilledIconButtonColors.copy(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        )

    internal val CssVariables.defaultFilledIconButtonColors: IconButtonColors
        get() {
            return IconButtonColors(
                        containerColor = FilledIconButtonTokens.ContainerColor(this),
                        contentColor = FilledIconButtonTokens.Color(this),
                        disabledContainerColor =
                            FilledIconButtonTokens.DisabledContainerColor(this)
                                .copy(alpha = FilledIconButtonTokens.DisabledContainerOpacity),
                        disabledContentColor =
                            FilledIconButtonTokens.DisabledColor(this)
                                .copy(alpha = FilledIconButtonTokens.DisabledOpacity),
                    )
        }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [FilledIconToggleButton].
     */
    @Composable
    fun filledIconToggleButtonColors(): IconToggleButtonColors =
        LocalCssVariables.current.defaultFilledIconToggleButtonColors

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [FilledIconToggleButton].
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun filledIconToggleButtonColors(
        containerColor: Color = Color.Unspecified,
        // TODO(b/228455081): Using contentColorFor here will return OnSurfaceVariant,
        //  while the token value is Primary.
        contentColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color = Color.Unspecified,
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = LocalCssVariables.current.contentColorFor(checkedContainerColor),
    ): IconToggleButtonColors =
        LocalCssVariables.current.defaultFilledIconToggleButtonColors.copy(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = checkedContentColor,
        )

    internal val CssVariables.defaultFilledIconToggleButtonColors: IconToggleButtonColors
        get() {
            return IconToggleButtonColors(
                        containerColor = FilledIconButtonTokens.UnselectedContainerColor(this),
                        // TODO(b/228455081): Using contentColorFor here will return
                        // OnSurfaceVariant,
                        //  while the token value is Primary.
                        contentColor = FilledIconButtonTokens.UnselectedColor(this),
                        disabledContainerColor =
                            FilledIconButtonTokens.DisabledContainerColor(this)
                                .copy(alpha = FilledIconButtonTokens.DisabledContainerOpacity),
                        disabledContentColor =
                            FilledIconButtonTokens.DisabledColor(this)
                                .copy(alpha = FilledIconButtonTokens.DisabledOpacity),
                        checkedContainerColor =
                            FilledIconButtonTokens.SelectedContainerColor(this),
                        checkedContentColor = FilledIconButtonTokens.SelectedColor(this),
                    )
        }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [FilledTonalIconButton].
     */
    @Composable
    fun filledTonalIconButtonColors(): IconButtonColors =
        LocalCssVariables.current.defaultFilledTonalIconButtonColors

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [FilledTonalIconButton].
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun filledTonalIconButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalCssVariables.current.contentColorFor(containerColor),
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color = Color.Unspecified,
    ): IconButtonColors =
        LocalCssVariables.current.defaultFilledTonalIconButtonColors.copy(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        )

    internal val CssVariables.defaultFilledTonalIconButtonColors: IconButtonColors
        get() {
            return IconButtonColors(
                        containerColor = FilledTonalIconButtonTokens.ContainerColor(this),
                        contentColor = FilledTonalIconButtonTokens.Color(this),
                        disabledContainerColor =
                            FilledTonalIconButtonTokens.DisabledContainerColor(this)
                                .copy(alpha = FilledTonalIconButtonTokens.DisabledContainerOpacity),
                        disabledContentColor =
                            FilledTonalIconButtonTokens.DisabledColor(this)
                                .copy(alpha = FilledTonalIconButtonTokens.DisabledOpacity),
                    )
        }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [FilledTonalIconToggleButton].
     */
    @Composable
    fun filledTonalIconToggleButtonColors(): IconToggleButtonColors =
        LocalCssVariables.current.defaultFilledTonalIconToggleButtonColors

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [FilledTonalIconToggleButton].
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun filledTonalIconToggleButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalCssVariables.current.contentColorFor(containerColor),
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color = Color.Unspecified,
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = LocalCssVariables.current.contentColorFor(checkedContainerColor),
    ): IconToggleButtonColors =
        LocalCssVariables.current.defaultFilledTonalIconToggleButtonColors.copy(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = checkedContentColor,
        )

    internal val CssVariables.defaultFilledTonalIconToggleButtonColors: IconToggleButtonColors
        get() {
            return IconToggleButtonColors(
                        containerColor =
                            FilledTonalIconButtonTokens.UnselectedContainerColor(this),
                        contentColor = FilledTonalIconButtonTokens.UnselectedColor(this),
                        disabledContainerColor =
                            FilledTonalIconButtonTokens.DisabledContainerColor(this)
                                .copy(alpha = FilledTonalIconButtonTokens.DisabledContainerOpacity),
                        disabledContentColor =
                            FilledTonalIconButtonTokens.DisabledColor(this)
                                .copy(alpha = FilledTonalIconButtonTokens.DisabledOpacity),
                        checkedContainerColor =
                            FilledTonalIconButtonTokens.SelectedContainerColor(this),
                        checkedContentColor = FilledTonalIconButtonTokens.SelectedColor(this),
                    )
        }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [OutlinedIconButton]. [LocalWidgetsContentColor] will be applied to the icon and down the UI tree.
     *
     * See [outlinedIconButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     */
    @Composable
    fun outlinedIconButtonColors(): IconButtonColors {
        val contentColor = LocalWidgetsContentColor.current
        val colors = LocalCssVariables.current.defaultOutlinedIconButtonColors(contentColor)
        if (colors.contentColor == contentColor) {
            return colors
        } else {
            return colors.copy(
                contentColor = contentColor,
                disabledContentColor =
                    contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
            )
        }
    }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [OutlinedIconButton].
     *
     * See [outlinedIconButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun outlinedIconButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalWidgetsContentColor.current,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
    ): IconButtonColors =
        LocalCssVariables.current
            .defaultOutlinedIconButtonColors(LocalWidgetsContentColor.current)
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
            )

    internal fun CssVariables.defaultOutlinedIconButtonColors(
        localContentColor: Color
    ): IconButtonColors {
        return run {
                IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = localContentColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            localContentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
                    )
            }
    }

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [OutlinedIconButton].
     *
     * See [outlinedIconButtonColors] for default values that applies [LocalWidgetsContentColor] to the
     * icon and down the UI tree.
     */
    @Composable
    fun outlinedIconButtonVibrantColors(): IconButtonColors =
        LocalCssVariables.current.defaultOutlinedIconButtonVibrantColors()

    /**
     * Creates a [IconButtonColors] that represents the default colors used in a
     * [OutlinedIconButton].
     *
     * See [outlinedIconButtonColors] for default values that applies [LocalWidgetsContentColor] to the
     * icon and down the UI tree.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun outlinedIconButtonVibrantColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
    ): IconButtonColors =
        LocalCssVariables.current
            .defaultOutlinedIconButtonVibrantColors()
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
            )

    internal fun CssVariables.defaultOutlinedIconButtonVibrantColors(): IconButtonColors {
        return run {
                IconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = OutlinedIconButtonTokens.Color(this),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            OutlinedIconButtonTokens.DisabledColor(this)
                                .copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
                    )
            }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [OutlinedIconToggleButton]. [LocalWidgetsContentColor] will be applied to the icon and down the UI
     * tree.
     *
     * See [outlinedIconButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     */
    @Composable
    fun outlinedIconToggleButtonColors(): IconToggleButtonColors {
        val contentColor = LocalWidgetsContentColor.current
        val colors = LocalCssVariables.current.defaultOutlinedIconToggleButtonColors(contentColor)
        if (colors.contentColor == contentColor) {
            return colors
        } else {
            return colors.copy(
                contentColor = contentColor,
                disabledContentColor =
                    contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
            )
        }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [OutlinedIconToggleButton]. [LocalWidgetsContentColor] will be applied to the icon and down the UI
     * tree.
     *
     * See [outlinedIconButtonVibrantColors] for default values that applies the recommended high
     * contrast colors.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun outlinedIconToggleButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalWidgetsContentColor.current,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = LocalCssVariables.current.contentColorFor(checkedContainerColor),
    ): IconToggleButtonColors =
        LocalCssVariables.current
            .defaultOutlinedIconToggleButtonColors(LocalWidgetsContentColor.current)
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            )

    internal fun CssVariables.defaultOutlinedIconToggleButtonColors(
        localContentColor: Color
    ): IconToggleButtonColors {
        return run {
                IconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = localContentColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            localContentColor.copy(
                                alpha = OutlinedIconButtonTokens.DisabledOpacity
                            ),
                        checkedContainerColor =
                            OutlinedIconButtonTokens.SelectedContainerColor(this),
                        checkedContentColor =
                            contentColorFor(
                                OutlinedIconButtonTokens.SelectedContainerColor(this)
                            ),
                    )
            }
    }

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [OutlinedIconToggleButton].
     *
     * See [outlinedIconToggleButtonColors] for default values that applies [LocalWidgetsContentColor] to
     * the icon and down the UI tree.
     */
    @Composable
    fun outlinedIconToggleButtonVibrantColors(): IconToggleButtonColors =
        LocalCssVariables.current.defaultOutlinedIconToggleButtonVibrantColors()

    /**
     * Creates a [IconToggleButtonColors] that represents the default colors used in a
     * [OutlinedIconToggleButton].
     *
     * See [outlinedIconToggleButtonColors] for default values that applies [LocalWidgetsContentColor] to
     * the icon and down the UI tree.
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     * @param checkedContainerColor the container color of this icon button when checked.
     * @param checkedContentColor the content color of this icon button when checked.
     */
    @Composable
    fun outlinedIconToggleButtonVibrantColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color =
            contentColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = LocalCssVariables.current.contentColorFor(checkedContainerColor),
    ): IconToggleButtonColors =
        LocalCssVariables.current
            .defaultOutlinedIconToggleButtonVibrantColors()
            .copy(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = disabledContainerColor,
                disabledContentColor = disabledContentColor,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            )

    internal fun CssVariables.defaultOutlinedIconToggleButtonVibrantColors():
        IconToggleButtonColors {
        return run {
                IconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = OutlinedIconButtonTokens.UnselectedColor(this),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor =
                            OutlinedIconButtonTokens.DisabledColor(this)
                                .copy(alpha = OutlinedIconButtonTokens.DisabledOpacity),
                        checkedContainerColor =
                            OutlinedIconButtonTokens.SelectedContainerColor(this),
                        checkedContentColor = OutlinedIconButtonTokens.SelectedColor(this),
                    )
            }
    }

    /**
     * Represents the [BorderStroke] for an [OutlinedIconButton], depending on its [enabled] and
     * [checked] state. [LocalWidgetsContentColor] will be used as the border color.
     *
     * See [outlinedIconToggleButtonVibrantBorder] for a [BorderStroke] that uses the spec
     * recommended color as the border color.
     *
     * @param enabled whether the icon button is enabled
     * @param checked whether the icon button is checked
     */
    @Composable
    fun outlinedIconToggleButtonBorder(enabled: Boolean, checked: Boolean): BorderStroke? {
        if (checked) {
            return null
        }
        return outlinedIconButtonBorder(enabled)
    }

    /**
     * Represents the [BorderStroke] for an [OutlinedIconButton], depending on its [enabled] and
     * [checked] state. The spec recommended color will be used as the border color.
     *
     * @param enabled whether the icon button is enabled
     * @param checked whether the icon button is checked
     */
    @Composable
    fun outlinedIconToggleButtonVibrantBorder(enabled: Boolean, checked: Boolean): BorderStroke? {
        if (checked) {
            return null
        }
        return outlinedIconButtonVibrantBorder(enabled)
    }

    /**
     * Represents the [BorderStroke] for an [OutlinedIconButton], depending on its [enabled] state.
     * [LocalWidgetsContentColor] will be used as the border color.
     *
     * See [outlinedIconToggleButtonVibrantBorder] for a [BorderStroke] that uses the spec
     * recommended color as the border color.
     *
     * @param enabled whether the icon button is enabled
     */
    @Composable
    fun outlinedIconButtonBorder(enabled: Boolean): BorderStroke {
        val outlineColor = LocalWidgetsContentColor.current
        val color: Color =
            if (enabled) {
                outlineColor
            } else {
                outlineColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity)
            }
        return remember(color) { BorderStroke(SmallIconButtonTokens.OutlinedOutlineWidth, color) }
    }

    /**
     * Represents the [BorderStroke] for an [OutlinedIconButton], depending on its [enabled] state.
     * The spec recommended color will be used as the border color.
     *
     * @param enabled whether the icon button is enabled
     */
    @Composable
    fun outlinedIconButtonVibrantBorder(enabled: Boolean): BorderStroke {
        val outlineColor = OutlinedIconButtonTokens.OutlineColor(LocalCssVariables.current)
        val color: Color =
            if (enabled) {
                outlineColor
            } else {
                outlineColor.copy(alpha = OutlinedIconButtonTokens.DisabledOpacity)
            }
        return remember(color) { BorderStroke(SmallIconButtonTokens.OutlinedOutlineWidth, color) }
    }

    /** Default ripple shape for a standard icon button. */
    val standardShape: Shape
        @Composable get() = SmallIconButtonTokens.ContainerShapeRound.value

    /** Default shape for a filled icon button. */
    val filledShape: Shape
        @Composable get() = SmallIconButtonTokens.ContainerShapeRound.value

    /** Default shape for an outlined icon button. */
    val outlinedShape: Shape
        @Composable get() = SmallIconButtonTokens.ContainerShapeRound.value

    /**
     * Default container size for any small icon button.
     *
     * @param widthOption the width of the container
     */
    internal fun smallContainerSize(
        widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform
    ): DpSize {
        val horizontalSpace =
            when (widthOption) {
                IconButtonWidthOption.Narrow ->
                    SmallIconButtonTokens.NarrowLeadingSpace +
                        SmallIconButtonTokens.NarrowTrailingSpace
                IconButtonWidthOption.Uniform ->
                    SmallIconButtonTokens.DefaultLeadingSpace +
                        SmallIconButtonTokens.DefaultLeadingSpace
                IconButtonWidthOption.Wide ->
                    SmallIconButtonTokens.WideLeadingSpace + SmallIconButtonTokens.WideTrailingSpace
                else -> 0.dp
            }
        return DpSize(
            SmallIconButtonTokens.IconSize + horizontalSpace,
            SmallIconButtonTokens.ContainerHeight,
        )
    }

    /** Class that describes the different supported widths of the [IconButton]. */
    @JvmInline
    value class IconButtonWidthOption private constructor(private val value: Int) {
        companion object {
            // TODO(b/342666275): update this kdoc with spec guidance
            /*
             * This configuration is recommended for small screens.
             */
            val Narrow = IconButtonWidthOption(0)

            /*
             * This configuration is recommended for medium width screens.
             */
            val Uniform = IconButtonWidthOption(1)

            /*
             * This configuration is recommended for wide screens.
             */
            val Wide = IconButtonWidthOption(2)
        }

        override fun toString() =
            when (this) {
                Narrow -> "Narrow"
                Uniform -> "Uniform"
                Wide -> "Wide"
                else -> "Unknown"
            }
    }
}
