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
// 本项目自有的设计令牌：不再跟随上游生成，可由本项目直接改。
// 只留本组件库真正用到的槽位：经典圆形进度条用 Size / TrackActiveSpace / TrackThickness；
// 波形圆形进度条另用 ActiveThickness / ActiveWaveWavelength / WaveSize。
// 上游那份里的 ActiveWaveAmplitude 没有任何组件在用，已随裁剪删掉。

package com.lhzkml.jasmine.core.widgets.tokens

import androidx.compose.ui.unit.dp

internal object CircularProgressIndicatorTokens {
    val ActiveThickness = 4.0.dp
    val ActiveWaveWavelength = 15.0.dp
    val Size = 40.0.dp
    val TrackActiveSpace = 4.0.dp
    val TrackThickness = 4.0.dp
    val WaveSize = 48.0.dp
}
