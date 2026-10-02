package com.lhzkml.jasmine.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 本应用的形状表 —— **值照抄 Material3 的默认形状**（上游 `tokens/ShapeTokens.kt`），
 * 不自创：extraSmall = 4dp、small = 8dp、medium = 12dp、large = 16dp、extraLarge = 28dp。
 *
 * **为什么自己持有一份**：此前 `MaterialTheme` 没传 `shapes`，于是 `core:widgets` 的
 * `ShapeKeyTokens` 最终取到的是 M3 主题内部的默认值 —— 形状的真值不在我们手里。
 * 现在由这里提供同一份对象（既喂给 `MaterialTheme`，也喂给 `LocalWidgetsShapes`）：
 * **值不变、观感不变**，但以后要调形状只需改这一处。
 *
 * 注意：`Shapes` 的另外三个 expressive 参数（`largeIncreased` / `extraLargeIncreased` /
 * `extraExtraLarge`）在上游是 `internal`，外部构造不了，所以这里不传，保持 M3 默认。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
