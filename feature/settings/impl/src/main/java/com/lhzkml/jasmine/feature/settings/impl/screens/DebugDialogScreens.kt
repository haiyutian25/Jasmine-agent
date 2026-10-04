package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.button.TextButton
import com.lhzkml.jasmine.core.widgets.dialog.AlertDialog
import com.lhzkml.jasmine.core.widgets.dialog.AlertDialogDefaults
import com.lhzkml.jasmine.core.widgets.dialog.BasicAlertDialog
import com.lhzkml.jasmine.core.widgets.surface.Surface
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugDialogVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 图标尺寸、容器内边距与行间距。 */
private val DebugDialogIconSize = 24.dp
private val DebugDialogIconButtonSize = 24.dp
private val DebugDialogPadding = 24.dp
private val DebugDialogTitleGap = 12.dp
private val DebugDialogActionGap = 24.dp

/**
 * `core:widgets` 对话框的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。进来对话框
 * 就是打开的（关掉之后页内有个按钮可以再打开）。
 *
 * 3 档：基础档用实验性的 [BasicAlertDialog]（容器由调用方自己给 —— 这里就照 [AlertDialogDefaults]
 * 拼一个），另两档用稳定的 [AlertDialog]（图标 + 标题 + 正文 + 两个按钮），最后一档故意把标题/
 * 正文/按钮文案都拉长，用来看按钮**自动换行**（内部是自带的小号 FlowRow）。
 */
@Composable
fun DebugDialogScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugDialogTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugDialogIconButtonSize)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_dialog_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(onClick = { open = true }) {
                    Text(stringResource(R.string.debug_dialog_reopen))
                }
            }
        }

        if (open) {
            val dismiss = { open = false }
            when (variant) {
                SettingsDebugDialogVariant.CLASSIC, SettingsDebugDialogVariant.CLASSIC_LONG ->
                    AlertDialog(
                        onDismissRequest = dismiss,
                        icon = {
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(DebugDialogIconSize)
                            )
                        },
                        title = { Text(stringResource(debugDialogHeadingRes(variant))) },
                        text = { Text(stringResource(debugDialogBodyRes(variant))) },
                        confirmButton = {
                            TextButton(onClick = dismiss) {
                                Text(stringResource(debugDialogConfirmRes(variant)))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = dismiss) {
                                Text(stringResource(R.string.debug_dialog_dismiss))
                            }
                        }
                    )

                else ->
                    BasicAlertDialog(onDismissRequest = dismiss) {
                        Surface(
                            shape = AlertDialogDefaults.shape,
                            color = AlertDialogDefaults.containerColor
                        ) {
                            Column(modifier = Modifier.padding(DebugDialogPadding)) {
                                Text(
                                    text = stringResource(R.string.debug_dialog_basic_title),
                                    fontSize = 20.sp,
                                    color = AlertDialogDefaults.titleContentColor
                                )
                                Spacer(modifier = Modifier.height(DebugDialogTitleGap))
                                Text(
                                    text = stringResource(R.string.debug_dialog_basic_text),
                                    fontSize = 14.sp,
                                    color = AlertDialogDefaults.textContentColor
                                )
                                Spacer(modifier = Modifier.height(DebugDialogActionGap))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = dismiss) {
                                        Text(stringResource(R.string.debug_dialog_dismiss))
                                    }
                                    TextButton(onClick = dismiss) {
                                        Text(stringResource(R.string.debug_dialog_confirm))
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugDialogTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugDialogVariant.CLASSIC_LONG -> R.string.debug_dialog_entry_classic_long
        SettingsDebugDialogVariant.CLASSIC -> R.string.debug_dialog_entry_classic
        else -> R.string.debug_dialog_entry_basic
    }

/** 经典档的标题文案（长文案那档换一套）。 */
private fun debugDialogHeadingRes(variant: String): Int =
    if (variant == SettingsDebugDialogVariant.CLASSIC_LONG) {
        R.string.debug_dialog_long_title
    } else {
        R.string.debug_dialog_title
    }

/** 经典档的正文文案。 */
private fun debugDialogBodyRes(variant: String): Int =
    if (variant == SettingsDebugDialogVariant.CLASSIC_LONG) {
        R.string.debug_dialog_long_text
    } else {
        R.string.debug_dialog_text
    }

/** 经典档的确认按钮文案（长文案那档特别长，用来看按钮换行）。 */
private fun debugDialogConfirmRes(variant: String): Int =
    if (variant == SettingsDebugDialogVariant.CLASSIC_LONG) {
        R.string.debug_dialog_confirm_long
    } else {
        R.string.debug_dialog_confirm
    }
