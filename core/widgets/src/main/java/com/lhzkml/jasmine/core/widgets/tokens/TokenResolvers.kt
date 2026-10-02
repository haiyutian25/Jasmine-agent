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

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.Shapes
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.lhzkml.jasmine.core.ui.theme.AppTypography
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsShapes

/**
 * 组件颜色令牌 → **本应用现有主题**（[CssVariables]，即 `JasmineTheme` 注入的那份调色板）的取值表。
 *
 * 这张表是 `core/ui/theme/Theme.kt` 里「[CssVariables] → M3 `ColorScheme`」那份映射的**镜像**，
 * 逐条对齐（`PrimaryContainer -> accent`、`SurfaceContainerHighest -> subtleSurface`、
 * `Outline -> border`、`OutlineVariant -> muted` …），所以取值与"经 M3 主题取"完全一致；
 * 区别只在于组件**不再借 M3 主题**，而是直接读我们自己的主题。
 * 6 个家族 × 明暗共 12 套调色板因此自动生效 —— 它们换的就是 [CssVariables]。
 */
@Stable
internal fun CssVariables.fromToken(value: ColorSchemeKeyTokens): Color {
    return when (value) {
        // 这两项现有 12 套调色板都还没有自己的槽位，暂用 M3 基线值（与改造前取到的值一致）：
        // 错误色分深浅两套基线，遮罩色两套都是纯黑。将来若要给每套调色板单独的 error，
        // 给 CssVariables 加槽后改这两行即可。
        ColorSchemeKeyTokens.Error -> if (isDark) M3DarkError else M3LightError
        ColorSchemeKeyTokens.Scrim -> M3BaselineScrim
        ColorSchemeKeyTokens.OnPrimary -> primaryForeground
        ColorSchemeKeyTokens.OnPrimaryContainer -> accentForeground
        ColorSchemeKeyTokens.OnSecondaryContainer -> mutedForeground
        ColorSchemeKeyTokens.OnSurface -> cardForeground
        ColorSchemeKeyTokens.OnSecondary -> foreground
        ColorSchemeKeyTokens.InverseSurface -> foreground
        ColorSchemeKeyTokens.InverseOnSurface -> background
        ColorSchemeKeyTokens.OnSurfaceVariant -> mutedForeground
        ColorSchemeKeyTokens.Outline -> border
        ColorSchemeKeyTokens.OutlineVariant -> muted
        ColorSchemeKeyTokens.Primary -> primary
        ColorSchemeKeyTokens.PrimaryContainer -> accent
        ColorSchemeKeyTokens.Secondary -> mutedForeground
        ColorSchemeKeyTokens.SecondaryContainer -> subtleSurface
        ColorSchemeKeyTokens.Surface -> card
        ColorSchemeKeyTokens.SurfaceContainer -> card
        ColorSchemeKeyTokens.SurfaceContainerHigh -> subtleSurface
        ColorSchemeKeyTokens.SurfaceContainerHighest -> subtleSurface
        ColorSchemeKeyTokens.SurfaceContainerLow -> card
        ColorSchemeKeyTokens.SurfaceVariant -> subtleSurface
    }
}

/** M3 浅色基线错误色（= `lightColorScheme().error`）。 */
private val M3LightError = Color(0xFFB3261E)

/** M3 深色基线错误色（= `darkColorScheme().error`）。 */
private val M3DarkError = Color(0xFFF2B8B5)

/** M3 基线遮罩色（浅色与深色两套都是纯黑）。 */
private val M3BaselineScrim = Color(0xFF000000)

/**
 * **过渡用的旧入口**：仍是"从 M3 `ColorScheme` 的角色取"。
 *
 * 只服务于那些自己声明了 `internal val CssVariables.defaultXxxColors` 的组件文件
 *（`TopAppBar` / `Button` / `Card` / `Switch` …）—— 它们还没改成读我们的主题。
 * 因为 `Theme.kt` 的映射与上面 [CssVariables.fromToken] 逐条对齐，两条路取到的值完全一致。
 *
 * 组件侧（`ColorSchemeKeyTokens.value`）已经走 [CssVariables.fromToken]；
 * 等那批构造器也改成 `CssVariables` 之后，本函数即可删除（见 `CORE_WIDGETS_M3_AUDIT.md` §8）。
 */
@Stable
internal fun ColorScheme.fromToken(value: ColorSchemeKeyTokens): Color {
    return when (value) {
        ColorSchemeKeyTokens.Error -> error
        ColorSchemeKeyTokens.OnPrimary -> onPrimary
        ColorSchemeKeyTokens.OnPrimaryContainer -> onPrimaryContainer
        ColorSchemeKeyTokens.OnSecondaryContainer -> onSecondaryContainer
        ColorSchemeKeyTokens.OnSurface -> onSurface
        ColorSchemeKeyTokens.OnSecondary -> onSecondary
        ColorSchemeKeyTokens.InverseSurface -> inverseSurface
        ColorSchemeKeyTokens.InverseOnSurface -> inverseOnSurface
        ColorSchemeKeyTokens.OnSurfaceVariant -> onSurfaceVariant
        ColorSchemeKeyTokens.Outline -> outline
        ColorSchemeKeyTokens.OutlineVariant -> outlineVariant
        ColorSchemeKeyTokens.Primary -> primary
        ColorSchemeKeyTokens.PrimaryContainer -> primaryContainer
        ColorSchemeKeyTokens.Scrim -> scrim
        ColorSchemeKeyTokens.Secondary -> secondary
        ColorSchemeKeyTokens.SecondaryContainer -> secondaryContainer
        ColorSchemeKeyTokens.Surface -> surface
        ColorSchemeKeyTokens.SurfaceContainer -> surfaceContainer
        ColorSchemeKeyTokens.SurfaceContainerHigh -> surfaceContainerHigh
        ColorSchemeKeyTokens.SurfaceContainerHighest -> surfaceContainerHighest
        ColorSchemeKeyTokens.SurfaceContainerLow -> surfaceContainerLow
        ColorSchemeKeyTokens.SurfaceVariant -> surfaceVariant
    }
}

/**
 * 颜色令牌 → 当前主题的颜色；随 [LocalCssVariables] 变化自动重组（换调色板即刻生效）。
 */
internal val ColorSchemeKeyTokens.value: Color
    @ReadOnlyComposable @Composable get() = LocalCssVariables.current.fromToken(this)

/** Helper function for component shape tokens. Used to grab the top values of a shape parameter. */
internal fun CornerBasedShape.top(
    bottomSize: CornerSize = ShapeTokens.CornerValueNone
): CornerBasedShape {
    return copy(bottomStart = bottomSize, bottomEnd = bottomSize)
}

/**
 * Helper function for component shape tokens. Used to grab the bottom values of a shape parameter.
 */
internal fun CornerBasedShape.bottom(
    topSize: CornerSize = ShapeTokens.CornerValueNone
): CornerBasedShape {
    return copy(topStart = topSize, topEnd = topSize)
}

/**
 * Helper function for component shape tokens. Used to grab the start values of a shape parameter.
 */
internal fun CornerBasedShape.start(
    endSize: CornerSize = ShapeTokens.CornerValueNone
): CornerBasedShape {
    return copy(topEnd = endSize, bottomEnd = endSize)
}

/** Helper function for component shape tokens. Used to grab the end values of a shape parameter. */
internal fun CornerBasedShape.end(
    startSize: CornerSize = ShapeTokens.CornerValueNone
): CornerBasedShape {
    return copy(topStart = startSize, bottomStart = startSize)
}

/**
 * Helper function for component shape tokens. Here is an example on how to use component color
 * tokens: ``LocalWidgetsShapes.current.fromToken(FabPrimarySmallTokens.ContainerShape)``
 */
internal fun Shapes.fromToken(value: ShapeKeyTokens): Shape {
    return when (value) {
        ShapeKeyTokens.CornerExtraLarge -> extraLarge
        ShapeKeyTokens.CornerExtraLargeTop -> extraLarge.top()
        ShapeKeyTokens.CornerExtraSmall -> extraSmall
        ShapeKeyTokens.CornerExtraSmallTop -> extraSmall.top()
        ShapeKeyTokens.CornerFull -> CircleShape
        ShapeKeyTokens.CornerLargeEnd -> large.end()
        ShapeKeyTokens.CornerMedium -> medium
        ShapeKeyTokens.CornerNone -> RectangleShape
        ShapeKeyTokens.CornerSmall -> small
    }
}

/**
 * 形状令牌 → 当前主题的形状；随 [LocalWidgetsShapes] 变化自动重组。
 * （`JasmineTheme` 目前注入的还是 M3 默认形状，改动形状口径只需改注入值。）
 */
internal val ShapeKeyTokens.value: Shape
    @Composable @ReadOnlyComposable get() = LocalWidgetsShapes.current.fromToken(this)

/**
 * Returns [ColorScheme.surfaceColorAtElevation] with the provided elevation if
 * [LocalTonalElevationEnabled] is set to true, and the provided background color matches
 * [ColorScheme.surface]. Otherwise, the provided color is returned unchanged.
 *
 * @param backgroundColor The background color to compare to [ColorScheme.surface]
 * @param elevation The elevation provided to [ColorScheme.surfaceColorAtElevation] if
 *   [backgroundColor] matches surface.
 * @return [ColorScheme.surfaceColorAtElevation] at [elevation] if [backgroundColor] ==
 *   [ColorScheme.surface] and [LocalTonalElevationEnabled] is set to true. Else [backgroundColor]
 */
@Composable
@ReadOnlyComposable
internal fun ColorScheme.applyTonalElevation(backgroundColor: Color, elevation: Dp): Color {
    val tonalElevationEnabled = LocalTonalElevationEnabled.current
    return if (backgroundColor == surface && tonalElevationEnabled) {
        surfaceColorAtElevation(elevation)
    } else {
        backgroundColor
    }
}

/** Helper function for component typography tokens. */
internal fun Typography.fromToken(value: TypographyKeyTokens): TextStyle {
    return when (value) {
        TypographyKeyTokens.BodyMedium -> bodyMedium
        TypographyKeyTokens.HeadlineSmall -> headlineSmall
        TypographyKeyTokens.LabelLarge -> labelLarge
        TypographyKeyTokens.LabelMedium -> labelMedium
        TypographyKeyTokens.LabelSmall -> labelSmall
        TypographyKeyTokens.TitleLarge -> titleLarge
        TypographyKeyTokens.TitleSmall -> titleSmall
    }
}

/**
 * 字体令牌 → 当前主题的排版样式；用的是 `JasmineTheme` 注入给 M3 的同一份 [AppTypography]。
 */
internal val TypographyKeyTokens.value: TextStyle
    @Composable @ReadOnlyComposable get() = AppTypography.fromToken(this)
