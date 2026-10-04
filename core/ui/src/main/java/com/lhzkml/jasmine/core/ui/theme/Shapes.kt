package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * 本应用的形状表 —— **自有类型**（不再拿 M3 的 `Shapes` 当载体），值照抄 Material3 的默认形状
 * （上游 `tokens/ShapeTokens.kt`）：extraSmall = 4dp、small = 8dp、medium = 12dp、large = 16dp、
 * extraLarge = 28dp —— **一个都没改**，观感与之前完全一致。
 *
 * 属性名与上游逐字一致，所以调用点（`feature/settings/impl` 里多处 `AppShapes.medium` /
 * `AppShapes.large`）不用动。主题层通过 `LocalWidgetsShapes` 下发本表（`core:widgets` 读它）。
 */
@Immutable
object AppShapes {
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val small: CornerBasedShape = RoundedCornerShape(8.dp)
    val medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val large: CornerBasedShape = RoundedCornerShape(16.dp)
    val extraLarge: CornerBasedShape = RoundedCornerShape(28.dp)
}

