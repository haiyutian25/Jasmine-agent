// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 上游位置：tokens/StandardMotionTokens.kt —— **完整保留上游的全量槽位**（不裁剪）。
// 颜色槽位写成 `(CssVariables) -> Color`，直读自有主题（与 TokenResolvers.fromToken 一一对应）；
// 形状（ShapeKeyTokens -> LocalWidgetsShapes）、尺寸（dp）、字体（AppTypography）照上游取值。

package com.lhzkml.jasmine.core.widgets.tokens


internal object StandardMotionTokens {
    val SpringDefaultSpatialDamping = 0.9f
    val SpringDefaultSpatialStiffness = 700.0f
    val SpringDefaultEffectsDamping = 1.0f
    val SpringDefaultEffectsStiffness = 1600.0f
    val SpringFastSpatialDamping = 0.9f
    val SpringFastSpatialStiffness = 1400.0f
    val SpringFastEffectsDamping = 1.0f
    val SpringFastEffectsStiffness = 3800.0f
    val SpringSlowSpatialDamping = 0.9f
    val SpringSlowSpatialStiffness = 300.0f
    val SpringSlowEffectsDamping = 1.0f
    val SpringSlowEffectsStiffness = 800.0f
}
