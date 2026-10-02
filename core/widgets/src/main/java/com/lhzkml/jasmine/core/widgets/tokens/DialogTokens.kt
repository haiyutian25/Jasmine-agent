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
// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION v0_210），不再由上游生成，可直接改。
// 只留本组件库真正用到的槽位（对话框容器、标题/正文/图标/按钮四组颜色与字体）；
// 上游那份里的 Focus/Hover/Pressed 三态按钮色、容器高度与图标尺寸没有任何组件在用，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

internal object DialogTokens {
    val ActionLabelTextColor = ColorSchemeKeyTokens.Primary
    val ActionLabelTextFont = TypographyKeyTokens.LabelLarge
    val ContainerColor = ColorSchemeKeyTokens.SurfaceContainerHigh
    val ContainerShape = ShapeKeyTokens.CornerExtraLarge
    val HeadlineColor = ColorSchemeKeyTokens.OnSurface
    val HeadlineFont = TypographyKeyTokens.HeadlineSmall
    val IconColor = ColorSchemeKeyTokens.Secondary
    val SupportingTextColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val SupportingTextFont = TypographyKeyTokens.BodyMedium
}
