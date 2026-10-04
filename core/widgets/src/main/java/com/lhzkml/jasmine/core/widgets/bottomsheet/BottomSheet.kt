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

// 本项目自有的组件代码（照 core:ui 的 `components/BottomSheet.kt` 搬过来，改用自有的
// `ModalBottomSheet`，于是 App 里所有弹层都不再经过 上游的 ModalBottomSheet）。

package com.lhzkml.jasmine.core.widgets.bottomsheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lhzkml.jasmine.core.ui.theme.CssVariables

/**
 * 全 App 共用的主题化底部弹层，基于自有的 [ModalBottomSheet]。
 *
 * 外观与关闭方式由 [ModalBottomSheet] 的默认值给足：没有拖动小横条、拖拽不关闭，右上角有一个
 * X（56dp 触摸区）—— 加上点遮罩与系统返回键，一共三种关闭方式，都走 [onDismiss]。
 *
 * IME 处理（官方配方）：[ModalBottomSheet] 自己的布局默认**不含** IME inset，所以这里把
 * `contentWindowInsets` 设成 `ime ∪ navigationBars`，由它负责对键盘做尺寸与落点。弹层内容
 * 不要自己再加 `imePadding()` / `navigationBarsPadding()` —— 那样会看到已被消费的 inset
 * （不会双份内边距）但保持单一真相源能避免每帧锚点抖动。
 *
 * 前置条件（应用侧）：`android:windowSoftInputMode="adjustResize"` + `enableEdgeToEdge()`，
 * 否则 Compose 根本收不到 IME inset。
 *
 * @param onDismiss    用户关闭弹层时调用（X / 点遮罩 / 返回键）
 * @param currentTheme 决定容器色与内容色
 * @param content      弹层内容（X 那一行由 [ModalBottomSheet] 画在内容之上）
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 弹层渲染在自己的 popup window 里，窗口会把 LocalDensity 重置成默认值、丢掉用户的字体
    // 缩放。记下调用处的 density（带着字体缩放），在窗口内部再提供回去。
    val ambientDensity = LocalDensity.current

    val sheetState = rememberModalBottomSheetState(
        // 只有一个落点：全高。没有半展开档，也就没有别的可拖去处。
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = currentTheme.card,
        contentColor = currentTheme.cardForeground,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
        modifier = modifier
    ) {
        CompositionLocalProvider(LocalDensity provides ambientDensity) {
            content()
        }
    }
}
