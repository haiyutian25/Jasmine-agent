package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * 本应用的形状表 —— **自有类型**（不再拿 M3 的 `Shapes` 当载体），值照抄 Material3 的默认形状
 * （上游 `tokens/ShapeTokens.kt`）：extraSmall = 4dp、small = 8dp、medium = 12dp、large = 16dp、
 * extraLarge = 28dp —— **一个都没改**，观感与之前完全一致。
 *
 * 属性名与上游逐字一致，所以调用点（`feature/settings/impl` 里多处 `AppShapes.medium` /
 * `AppShapes.large`）不用动。
 *
 * 与 M3 的唯一接触点是 [toM3Shapes]：`MaterialTheme(shapes = …)` 只接受 M3 的 `Shapes`，
 * 在主题那一处转换一次即可 —— 与颜色走 `ColorScheme` 的接缝同理。
 */
@Immutable
object AppShapes {
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val small: CornerBasedShape = RoundedCornerShape(8.dp)
    val medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val large: CornerBasedShape = RoundedCornerShape(16.dp)
    val extraLarge: CornerBasedShape = RoundedCornerShape(28.dp)
}

/** 接缝：把自有形状表转成 M3 的 `Shapes`，只给 `MaterialTheme()` 那一处用。 */
internal fun AppShapes.toM3Shapes(): Shapes =
    Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )
