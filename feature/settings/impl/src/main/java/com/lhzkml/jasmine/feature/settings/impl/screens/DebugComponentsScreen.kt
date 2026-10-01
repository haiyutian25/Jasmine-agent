package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.button.TextButton
import com.lhzkml.jasmine.core.widgets.textfield.OutlinedTextField
import com.lhzkml.jasmine.core.widgets.textfield.TextField
import com.lhzkml.jasmine.feature.settings.impl.R

/** Row label column width; padding and spacing inside a group card. */
private val DebugLabelWidth = 92.dp
private val DebugGroupSpacing = 16.dp
private val DebugRowPaddingHorizontal = 16.dp
private val DebugRowPaddingVertical = 8.dp
private val DebugGroupBottomPadding = 12.dp

/** Title typography; gap above a sub-title inside a group card. */
private val DebugTitleFontSize = 12.sp
private val DebugSubTitleTopPadding = 10.dp

/**
 * 组件层（`core:widgets`）的独立调试页：每个状态下把自建组件各渲一遍，只为在真机上看真实效果。
 * 按钮点了不做事、输入框只接受输入，都不读任何业务状态 —— 它和聊天链路、和别的任何页面都没有关系。
 */
@Composable
fun DebugComponentsScreen(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DebugStateGroup(
            title = stringResource(R.string.debug_screen_section_enabled),
            enabled = true,
            currentTheme = currentTheme,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugStateGroup(
            title = stringResource(R.string.debug_screen_section_disabled),
            enabled = false,
            currentTheme = currentTheme,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 一组卡片：给定状态下，按钮与输入框各渲一遍。 */
@Composable
private fun DebugStateGroup(
    title: String,
    enabled: Boolean,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = title,
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugSubTitle(
            text = stringResource(R.string.debug_screen_section_buttons),
            currentTheme = currentTheme
        )
        DebugRow(label = "Filled", currentTheme = currentTheme) {
            Button(onClick = {}, enabled = enabled) { Text("Button") }
        }
        DebugRow(label = "Text", currentTheme = currentTheme) {
            TextButton(onClick = {}, enabled = enabled) { Text("Button") }
        }

        DebugSubTitle(
            text = stringResource(R.string.debug_screen_section_text_fields),
            currentTheme = currentTheme
        )
        DebugFieldRow(label = "Filled", currentTheme = currentTheme) {
            DebugTextField(label = "Filled", enabled = enabled, outlined = false)
        }
        DebugFieldRow(label = "Outlined", currentTheme = currentTheme) {
            DebugTextField(label = "Outlined", enabled = enabled, outlined = true)
        }

        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 组内的小标题（按钮 / 输入框）。 */
@Composable
private fun DebugSubTitle(
    text: String,
    currentTheme: CssVariables
) {
    Text(
        text = text,
        fontSize = DebugTitleFontSize,
        color = currentTheme.mutedForeground,
        modifier = Modifier.padding(
            start = DebugRowPaddingHorizontal,
            top = DebugSubTitleTopPadding,
            bottom = DebugRowPaddingVertical
        )
    )
}

/** 一个按钮行：固定宽度的标签列 + 按钮本身。 */
@Composable
private fun DebugRow(
    label: String,
    currentTheme: CssVariables,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DebugRowPaddingHorizontal,
                vertical = DebugRowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = DebugTitleFontSize,
            color = currentTheme.mutedForeground,
            modifier = Modifier.width(DebugLabelWidth)
        )
        content()
    }
}

/** 一个输入框行：标签在上、输入框占满整行（输入框本身有宽度）。 */
@Composable
private fun DebugFieldRow(
    label: String,
    currentTheme: CssVariables,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DebugRowPaddingHorizontal,
                vertical = DebugRowPaddingVertical
            )
    ) {
        Text(
            text = label,
            fontSize = DebugTitleFontSize,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(bottom = DebugRowPaddingVertical)
        )
        content()
    }
}

/** 输入框样本：自带一份输入状态（只为能真的敲字，不接任何业务）。 */
@Composable
private fun DebugTextField(
    label: String,
    enabled: Boolean,
    outlined: Boolean
) {
    var text by remember { mutableStateOf("") }
    if (outlined) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Text") },
            singleLine = true
        )
    } else {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Text") },
            singleLine = true
        )
    }
}
