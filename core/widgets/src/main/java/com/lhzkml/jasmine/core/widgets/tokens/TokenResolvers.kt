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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.AppShapes
import com.lhzkml.jasmine.core.ui.theme.AppTypography
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalCssVariables
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsShapes
import com.lhzkml.jasmine.core.ui.theme.LocalWidgetsTonalElevationEnabled
import kotlin.math.ln

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
        ColorSchemeKeyTokens.Error -> error
        ColorSchemeKeyTokens.InverseOnSurface -> inverseOnSurface
        ColorSchemeKeyTokens.InverseSurface -> inverseSurface
        ColorSchemeKeyTokens.OnPrimary -> primaryForeground
        ColorSchemeKeyTokens.OnPrimaryContainer -> onPrimaryContainer
        ColorSchemeKeyTokens.OnSecondary -> onSecondary
        ColorSchemeKeyTokens.OnSecondaryContainer -> onSecondaryContainer
        ColorSchemeKeyTokens.OnSurface -> cardForeground
        ColorSchemeKeyTokens.OnSurfaceVariant -> mutedForeground
        ColorSchemeKeyTokens.Outline -> border
        ColorSchemeKeyTokens.OutlineVariant -> muted
        ColorSchemeKeyTokens.Primary -> primary
        ColorSchemeKeyTokens.PrimaryContainer -> primaryContainer
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
 * 错误色的解析器，供各组件令牌表直读用（类型与其它颜色槽的 `(CssVariables) -> Color` 一致）。
 *
 * 值来自自有主题的 `CssVariables.error` 槽（现有 12 套调色板都还没给它单独配色，槽内沿用
 * M3 基线值，深浅两套分开）——与改造前经 `ColorSchemeKeyTokens.Error` 取到的值完全一致。
 * 将来若要给每套调色板单独配色，只改 `CssVariables.error` 一处即可。
 */
internal val ErrorColorResolver: (CssVariables) -> Color = { it.error }

/** M3 浅色基线错误色（= `lightColorScheme().error`）。 */
private val M3LightError = Color(0xFFB3261E)

/** M3 深色基线错误色（= `darkColorScheme().error`）。 */
private val M3DarkError = Color(0xFFF2B8B5)

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
internal fun AppShapes.fromToken(value: ShapeKeyTokens): Shape {
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
 * （注入的是**自有**的 [AppShapes] 类型，不再经 M3 的 `Shapes`；改形状口径只需改 `AppShapes`。）
 */
internal val ShapeKeyTokens.value: Shape
    @Composable @ReadOnlyComposable get() = LocalWidgetsShapes.current.fromToken(this)

/**
 * 「表面色 + 海拔」的染色，**本项目自己实现**（上游对应 `ColorScheme.kt` 的
 * `ColorScheme.applyTonalElevation` 与 `ColorScheme.surfaceColorAtElevation`，算法就是下面这两段）。
 * 取值与改造前逐项一致：
 * - 比较基准 `surface` → 自有主题的 [CssVariables.card]（`Theme.kt` 里 `surface = cssVars.card`）；
 * - 染色 tint `surfaceTint` → `Theme.kt` 里映射的 [CssVariables.primary]；
 * - 开关换成自有的 [LocalWidgetsTonalElevationEnabled]（默认开，与上游 `LocalTonalElevationEnabled` 一致）。
 *
 * @param backgroundColor 要判断的底色；不是主题 surface（= [CssVariables.card]）时原样返回
 * @param elevation 海拔；0.dp 时也原样返回
 */
@Composable
@ReadOnlyComposable
internal fun CssVariables.applyTonalElevation(backgroundColor: Color, elevation: Dp): Color {
    return if (backgroundColor == card && LocalWidgetsTonalElevationEnabled.current) {
        surfaceColorAtElevation(elevation)
    } else {
        backgroundColor
    }
}

/**
 * 海拔 [elevation] 处的表面色：把 [CssVariables.primary] 以 `(4.5·ln(e+1) + 2) / 100` 的透明度
 * 叠在 [CssVariables.card] 上（上游 `ColorScheme.surfaceColorAtElevation` 的原式，逐字照抄）。
 */
@Composable
@ReadOnlyComposable
private fun CssVariables.surfaceColorAtElevation(elevation: Dp): Color {
    if (elevation == 0.dp) return card
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return primary.copy(alpha = alpha).compositeOver(card)
}

// 排版槽位不再经过"字体令牌键 → M3 Typography"这层映射：各令牌表（AppBarSmallTokens /
// NavigationBarTokens / DialogTokens / BadgeTokens / PrimaryNavigationTabTokens）直接指向
// 自有排版档 `AppTypography.*`，所以 `Typography.fromToken` 与 `TypographyKeyTokens` 已删除。
