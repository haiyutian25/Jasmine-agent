/*
 * Copyright 2024 The Android Open Source Project
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

// 本项目自有的设计令牌（来源：AndroidX Material3 token VERSION 14_0_0），不再由上游生成，可直接改。
// 只留本组件库真正用到的槽位（小号顶部栏的容器高度与标题字体）；上游那份里的副标题字体没有任何组件
// 在用（我们的顶部栏没搬 subtitle 那套），已随裁剪删掉 —— 要加就照这里补一行。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object AppBarSmallTokens {
    val ContainerHeight = 64.0.dp
    val TitleFont = TypographyKeyTokens.TitleLarge
}
