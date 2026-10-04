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

// 本项目自有的组件代码（移植自上游对应源码后自行维护），不再跟随上游生成，可直接改。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 组件颜色令牌 → **自有主题**（[CssVariables]，即 `JasmineTheme` 注入的那份调色板）的取值表。
 *
 * 上游 `ColorSchemeKeyTokens` 的**全部 48 个角色键**都在这里有对应分支（不裁剪），
 * 每个键指向 `CssVariables` 上一个**独立**的槽（槽与槽之间取值互不重合）；
 * 6 个家族 × 明暗共 12 套调色板因此自动生效 —— 它们换的就是 [CssVariables]。
 */
@Stable
internal fun CssVariables.fromToken(value: ColorSchemeKeyTokens): Color {
    return when (value) {
        ColorSchemeKeyTokens.Background -> background
        ColorSchemeKeyTokens.Error -> error
        ColorSchemeKeyTokens.ErrorContainer -> errorContainer
        ColorSchemeKeyTokens.InverseOnSurface -> inverseOnSurface
        ColorSchemeKeyTokens.InversePrimary -> inversePrimary
        ColorSchemeKeyTokens.InverseSurface -> inverseSurface
        ColorSchemeKeyTokens.OnBackground -> foreground
        ColorSchemeKeyTokens.OnError -> onError
        ColorSchemeKeyTokens.OnErrorContainer -> onErrorContainer
        ColorSchemeKeyTokens.OnPrimary -> primaryForeground
        ColorSchemeKeyTokens.OnPrimaryContainer -> onPrimaryContainer
        ColorSchemeKeyTokens.OnPrimaryFixed -> onPrimaryFixed
        ColorSchemeKeyTokens.OnPrimaryFixedVariant -> onPrimaryFixedVariant
        ColorSchemeKeyTokens.OnSecondary -> onSecondary
        ColorSchemeKeyTokens.OnSecondaryContainer -> onSecondaryContainer
        ColorSchemeKeyTokens.OnSecondaryFixed -> onSecondaryFixed
        ColorSchemeKeyTokens.OnSecondaryFixedVariant -> onSecondaryFixedVariant
        ColorSchemeKeyTokens.OnSurface -> cardForeground
        ColorSchemeKeyTokens.OnSurfaceVariant -> mutedForeground
        ColorSchemeKeyTokens.OnTertiary -> onTertiary
        ColorSchemeKeyTokens.OnTertiaryContainer -> onTertiaryContainer
        ColorSchemeKeyTokens.OnTertiaryFixed -> onTertiaryFixed
        ColorSchemeKeyTokens.OnTertiaryFixedVariant -> onTertiaryFixedVariant
        ColorSchemeKeyTokens.Outline -> border
        ColorSchemeKeyTokens.OutlineVariant -> muted
        ColorSchemeKeyTokens.Primary -> primary
        ColorSchemeKeyTokens.PrimaryContainer -> primaryContainer
        ColorSchemeKeyTokens.PrimaryFixed -> primaryFixed
        ColorSchemeKeyTokens.PrimaryFixedDim -> primaryFixedDim
        ColorSchemeKeyTokens.Scrim -> scrim
        ColorSchemeKeyTokens.Secondary -> secondary
        ColorSchemeKeyTokens.SecondaryContainer -> secondaryContainer
        ColorSchemeKeyTokens.SecondaryFixed -> secondaryFixed
        ColorSchemeKeyTokens.SecondaryFixedDim -> secondaryFixedDim
        ColorSchemeKeyTokens.Surface -> surface
        ColorSchemeKeyTokens.SurfaceBright -> surfaceBright
        ColorSchemeKeyTokens.SurfaceContainer -> surfaceContainer
        ColorSchemeKeyTokens.SurfaceContainerHigh -> surfaceContainerHigh
        ColorSchemeKeyTokens.SurfaceContainerHighest -> surfaceContainerHighest
        ColorSchemeKeyTokens.SurfaceContainerLow -> surfaceContainerLow
        ColorSchemeKeyTokens.SurfaceContainerLowest -> surfaceContainerLowest
        ColorSchemeKeyTokens.SurfaceDim -> surfaceDim
        ColorSchemeKeyTokens.SurfaceTint -> surfaceTint
        ColorSchemeKeyTokens.SurfaceVariant -> surfaceVariant
        ColorSchemeKeyTokens.Tertiary -> tertiary
        ColorSchemeKeyTokens.TertiaryContainer -> tertiaryContainer
        ColorSchemeKeyTokens.TertiaryFixed -> tertiaryFixed
        ColorSchemeKeyTokens.TertiaryFixedDim -> tertiaryFixedDim
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
        // 与 AppShapes 中已有的档位对应
        ShapeKeyTokens.CornerExtraLarge -> extraLarge
        ShapeKeyTokens.CornerExtraLargeTop -> extraLarge.top()
        ShapeKeyTokens.CornerExtraSmall -> extraSmall
        ShapeKeyTokens.CornerExtraSmallTop -> extraSmall.top()
        ShapeKeyTokens.CornerFull -> CircleShape
        ShapeKeyTokens.CornerLarge -> large
        ShapeKeyTokens.CornerLargeEnd -> large.end()
        ShapeKeyTokens.CornerLargeStart -> large.start()
        ShapeKeyTokens.CornerLargeTop -> large.top()
        ShapeKeyTokens.CornerMedium -> medium
        ShapeKeyTokens.CornerNone -> RectangleShape
        ShapeKeyTokens.CornerSmall -> small
        // AppShapes 没有的档位：照上游 ShapeTokens 的取值直接构造（48dp / 32dp / 20dp）
        ShapeKeyTokens.CornerExtraExtraLarge -> RoundedCornerShape(48.0.dp)
        ShapeKeyTokens.CornerExtraLargeIncreased -> RoundedCornerShape(32.0.dp)
        ShapeKeyTokens.CornerLargeIncreased -> RoundedCornerShape(20.0.dp)
    }
}

/**
 * 形状令牌 → 当前主题的形状；随 [LocalWidgetsShapes] 变化自动重组。
 * （注入的是**自有**的 [AppShapes] 类型，不再经 上游的 `Shapes`；改形状口径只需改 `AppShapes`。）
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

// 排版槽位不再经过"字体令牌键 → 上游字体表"这层映射：各令牌表（AppBarSmallTokens /
// NavigationBarTokens / DialogTokens / BadgeTokens / PrimaryNavigationTabTokens）直接指向
// 自有排版档 `AppTypography.*`，所以 `Typography.fromToken` 与 `TypographyKeyTokens` 已删除。
