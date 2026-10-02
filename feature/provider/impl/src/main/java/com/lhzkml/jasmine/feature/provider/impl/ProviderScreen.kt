package com.lhzkml.jasmine.feature.provider.impl

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.ui.components.BottomSheet
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.components.ReasoningEffort
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.text.Text as WidgetsText
import com.lhzkml.jasmine.core.ui.theme.CssVariables

// ── Provider screen dimensions ─────────────────────────────────────────

/** List row: icon, texts and trailing actions. */
private val ProviderRowIconSize = 20.dp
private val ProviderRowIconTextSpacing = 12.dp
private val ProviderRowPaddingHorizontal = 16.dp
private val ProviderRowPaddingVertical = 13.dp
private val ProviderRowNameFontSize = 13.5.sp
private val ProviderRowBaseUrlFontSize = 11.sp
private val ProviderRowActionIconSize = 18.dp
private val ProviderRowDividerHeight = 1.dp

/** Add-provider entry row. */
private val ProviderAddIconSize = 20.dp
private val ProviderAddFontSize = 13.5.sp

/** Editor form. */
private val ProviderFieldLabelFontSize = 11.sp
private val ProviderFieldTextFontSize = 13.5.sp
private val ProviderFieldPaddingHorizontal = 12.dp
private val ProviderFieldPaddingVertical = 11.dp
private val ProviderFieldLabelSpacing = 6.dp
private val ProviderSectionSpacing = 18.dp
private val ProviderApiTypeCheckSize = 16.dp
private val ProviderActionButtonFontSize = 12.5.sp

/** Model picker sheet. */
private val ModelSheetTitleFontSize = 14.sp
private val ModelSheetPaddingHorizontal = 20.dp
private val ModelSheetListMaxHeight = 380.dp
private val ModelSheetSpinnerSize = 20.dp

/**
 * Model-provider management screen (list + in-screen add/edit form +
 * bottom-sheet model catalog / model parameter editor).
 *
 * List mode: one grouped card per provider (DeepSeek preset first), each row
 * showing icon + name + base URL with edit (and, for non-presets, delete)
 * actions; below it an "add provider" entry.
 *
 * Editor mode: name / base URL / API key fields, a two-row API-type picker
 * (Chat Completions / Responses API), and a **Models** section — configured
 * models with context/output budgets, plus "fetch model list" (opens the
 * catalog sheet) and "custom model ID" (opens the parameter sheet directly).
 */
@Composable
fun ProviderScreen(
    state: ProviderState,
    onAction: (ProviderAction) -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    // 编辑态下按系统返回键/手势：先关闭表单回到列表，而不是退出页面丢失草稿。
    BackHandler(enabled = state.editor != null) {
        onAction(ProviderAction.CancelClicked)
    }

    // 这四层（提供商列表 / 提供商表单 / 模型列表 / 模型表单）是叠着渲染的。开着键盘进入下一层，
    // 键盘会压在它上面；退出时下面那层里被点过的输入框还握着焦点，于是再进来键盘就自己弹回原来那个
    // 框 —— 所以每换一层都把键盘和焦点一起收掉（与侧边栏同一套做法）。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val overlay = when {
        state.editor?.modelEditor != null -> "model-editor"
        state.editor?.modelSheet != null -> "model-list"
        state.editor != null -> "provider-editor"
        else -> "provider-list"
    }
    LaunchedEffect(overlay) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

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
        val editor = state.editor
        if (editor == null) {
            ProviderListContent(
                providers = state.providers,
                currentTheme = currentTheme,
                onAction = onAction,
            )
        } else {
            ProviderEditorContent(
                editor = editor,
                currentTheme = currentTheme,
                onAction = onAction,
            )
        }
    }

    // Sheets render above the screen content; both are driven by editor state.
    val editor = state.editor
    if (editor != null) {
        editor.modelSheet?.let { sheet ->
            ModelPickerSheet(
                sheet = sheet,
                currentTheme = currentTheme,
                onAction = onAction,
            )
        }
        editor.modelEditor?.let { modelEditor ->
            ModelEditorSheet(
                editor = modelEditor,
                currentTheme = currentTheme,
                onAction = onAction,
            )
        }
    }
}

// ── List mode ──────────────────────────────────────────────────────────

@Composable
private fun ProviderListContent(
    providers: List<ProviderConfig>,
    currentTheme: CssVariables,
    onAction: (ProviderAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, AppShapes.large)
    ) {
        providers.forEachIndexed { index, provider ->
            ProviderRow(
                provider = provider,
                currentTheme = currentTheme,
                onEdit = { onAction(ProviderAction.EditClicked(provider.id)) },
                onDelete = { onAction(ProviderAction.DeleteClicked(provider.id)) },
            )
            if (index < providers.lastIndex) {
                ProviderDivider(currentTheme)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Add-provider entry (same card chrome as the list above).
    Button(
        onClick = { onAction(ProviderAction.AddClicked) },
        modifier = Modifier.fillMaxWidth(),
        testTag = "provider_add_entry"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, AppShapes.large)
                .padding(horizontal = ProviderRowPaddingHorizontal, vertical = ProviderRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProviderRowIconTextSpacing)
        ) {
            Icon(
                imageVector = LucideIcons.Plus,
                contentDescription = null,
                tint = currentTheme.foreground,
                modifier = Modifier.size(ProviderAddIconSize)
            )
            Text(
                text = stringResource(R.string.provider_add),
                fontSize = ProviderAddFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = null,
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    currentTheme: CssVariables,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ProviderRowPaddingHorizontal,
                end = 6.dp,
                top = ProviderRowPaddingVertical,
                bottom = ProviderRowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = LucideIcons.Cloud,
            contentDescription = null,
            tint = currentTheme.foreground,
            modifier = Modifier.size(ProviderRowIconSize)
        )

        Spacer(modifier = Modifier.width(ProviderRowIconTextSpacing))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                fontSize = ProviderRowNameFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
            Text(
                text = provider.baseUrl,
                fontSize = ProviderRowBaseUrlFontSize,
                color = currentTheme.mutedForeground
            )
        }

        Button(
            onClick = onEdit,
            rippleEnabled = false,
            testTag = "provider_edit_${provider.id}"
        ) {
            Icon(
                imageVector = LucideIcons.Pencil,
                contentDescription = stringResource(R.string.provider_edit_cd),
                tint = currentTheme.mutedForeground,
                modifier = Modifier
                    .padding(8.dp)
                    .size(ProviderRowActionIconSize)
            )
        }

        if (!provider.isBuiltIn) {
            Button(
                onClick = onDelete,
                rippleEnabled = false,
                testTag = "provider_delete_${provider.id}"
            ) {
                Icon(
                    imageVector = LucideIcons.Trash,
                    contentDescription = stringResource(R.string.provider_delete_cd),
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(ProviderRowActionIconSize)
                )
            }
        }
    }
}

// ── Editor mode ────────────────────────────────────────────────────────

@Composable
private fun ProviderEditorContent(
    editor: ProviderEditorState,
    currentTheme: CssVariables,
    onAction: (ProviderAction) -> Unit,
) {
    ProviderField(
        label = stringResource(R.string.provider_field_name),
        value = editor.name,
        onValueChange = { onAction(ProviderAction.NameChanged(it)) },
        currentTheme = currentTheme,
        keyboardType = KeyboardType.Text,
        testTag = "provider_field_name"
    )

    Spacer(modifier = Modifier.height(ProviderSectionSpacing))

    ProviderField(
        label = stringResource(R.string.provider_field_base_url),
        value = editor.baseUrl,
        onValueChange = { onAction(ProviderAction.BaseUrlChanged(it)) },
        currentTheme = currentTheme,
        keyboardType = KeyboardType.Uri,
        testTag = "provider_field_base_url"
    )

    Spacer(modifier = Modifier.height(ProviderSectionSpacing))

    ProviderField(
        label = stringResource(R.string.provider_field_api_key),
        value = editor.apiKey,
        onValueChange = { onAction(ProviderAction.ApiKeyChanged(it)) },
        currentTheme = currentTheme,
        keyboardType = KeyboardType.Password,
        obscure = true,
        testTag = "provider_field_api_key"
    )

    Spacer(modifier = Modifier.height(ProviderSectionSpacing))

    // API type picker: two mutually exclusive rows in a grouped card.
    ProviderSectionLabel(
        text = stringResource(R.string.provider_api_type_label),
        currentTheme = currentTheme
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.large)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, AppShapes.large)
    ) {
        ProviderApiType.entries.forEachIndexed { index, type ->
            val isSelected = editor.apiType == type
            Button(
                onClick = { onAction(ProviderAction.ApiTypeSelected(type)) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "provider_api_type_${type.name}"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ProviderRowPaddingHorizontal, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            when (type) {
                                ProviderApiType.CHAT_COMPLETIONS -> R.string.provider_api_type_chat
                                ProviderApiType.RESPONSES -> R.string.provider_api_type_responses
                            }
                        ),
                        fontSize = ProviderRowNameFontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = currentTheme.foreground
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = null,
                            tint = currentTheme.primary,
                            modifier = Modifier.size(ProviderApiTypeCheckSize)
                        )
                    }
                }
            }
            if (index < ProviderApiType.entries.lastIndex) {
                ProviderDivider(currentTheme)
            }
        }
    }

    Spacer(modifier = Modifier.height(ProviderSectionSpacing))

    // Models: configured entries + catalog fetch / custom id actions.
    ProviderSectionLabel(
        text = stringResource(R.string.provider_models_label),
        currentTheme = currentTheme
    )
    if (editor.models.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, AppShapes.large)
        ) {
            editor.models.forEachIndexed { index, model ->
                ModelRow(
                    model = model,
                    currentTheme = currentTheme,
                    onEdit = { onAction(ProviderAction.ModelEditClicked(model.id)) },
                    onDelete = { onAction(ProviderAction.ModelDeleteClicked(model.id)) },
                )
                if (index < editor.models.lastIndex) {
                    ProviderDivider(currentTheme)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 用自有 Button 的默认外观（primary 填充），不做任何外观定制。
        WidgetsButton(
            onClick = { onAction(ProviderAction.FetchModelsClicked) },
            modifier = Modifier
                .weight(1f)
                .testTag("provider_fetch_models_btn")
        ) {
            WidgetsText(text = stringResource(R.string.provider_fetch_models))
        }
        WidgetsButton(
            onClick = { onAction(ProviderAction.CustomModelClicked) },
            modifier = Modifier
                .weight(1f)
                .testTag("provider_custom_model_btn")
        ) {
            WidgetsText(text = stringResource(R.string.provider_custom_model))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Connectivity check: a real round trip through the draft credentials and
    // the first configured model — verifies the provider before saving.
    WidgetsButton(
        onClick = { onAction(ProviderAction.TestConnectionClicked) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_test_connection_btn")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editor.isProbing) {
                // 默认外观是 primary 实心底，进度圈随之改用前景色才可见。
                CircularProgressIndicator(
                    modifier = Modifier.size(ModelSheetSpinnerSize),
                    color = currentTheme.primaryForeground,
                    strokeWidth = 2.dp
                )
            }
            WidgetsText(
                text = stringResource(
                    if (editor.isProbing) {
                        R.string.provider_testing
                    } else {
                        R.string.provider_test_connection
                    }
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WidgetsButton(
            onClick = { onAction(ProviderAction.CancelClicked) },
            modifier = Modifier
                .weight(1f)
                .testTag("provider_cancel_btn")
        ) {
            WidgetsText(text = stringResource(R.string.provider_cancel))
        }
        WidgetsButton(
            onClick = { onAction(ProviderAction.SaveClicked) },
            modifier = Modifier
                .weight(1f)
                .testTag("provider_save_btn")
        ) {
            WidgetsText(text = stringResource(R.string.provider_save))
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelConfig,
    currentTheme: CssVariables,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ProviderRowPaddingHorizontal,
                end = 6.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 出厂目录给了名字就用它（例如 `GPT-5.5`），否则显示线上 id。
                text = model.name.ifEmpty { model.modelId },
                fontSize = ProviderRowNameFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
            if (model.contextLength > 0 || model.maxOutputLength > 0) {
                Text(
                    text = stringResource(
                        R.string.provider_model_params,
                        model.contextLength,
                        model.maxOutputLength
                    ),
                    fontSize = ProviderRowBaseUrlFontSize,
                    color = currentTheme.mutedForeground
                )
            }
        }

        Button(
            onClick = onEdit,
            rippleEnabled = false,
            testTag = "provider_model_edit_${model.id}"
        ) {
            Icon(
                imageVector = LucideIcons.Pencil,
                contentDescription = stringResource(R.string.provider_model_edit_cd),
                tint = currentTheme.mutedForeground,
                modifier = Modifier
                    .padding(8.dp)
                    .size(ProviderRowActionIconSize)
            )
        }
        Button(
            onClick = onDelete,
            rippleEnabled = false,
            testTag = "provider_model_delete_${model.id}"
        ) {
            Icon(
                imageVector = LucideIcons.Trash,
                contentDescription = stringResource(R.string.provider_model_delete_cd),
                tint = currentTheme.mutedForeground,
                modifier = Modifier
                    .padding(8.dp)
                    .size(ProviderRowActionIconSize)
            )
        }
    }
}

// ── Model picker sheet ─────────────────────────────────────────────────

/**
 * Catalog sheet: shows the fetch progress, the fetched model ids (tap to
 * configure), or the failure state with retry / custom-id escape hatches.
 * A "custom model ID" entry is always available so a provider without a
 * working /models endpoint is still usable.
 */
@Composable
private fun ModelPickerSheet(
    sheet: ModelSheetState,
    currentTheme: CssVariables,
    onAction: (ProviderAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ProviderAction.ModelSheetDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("provider_model_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ModelSheetPaddingHorizontal)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.provider_select_model),
                fontSize = ModelSheetTitleFontSize,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (sheet) {
                ModelSheetState.Fetching -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ModelSheetSpinnerSize),
                            color = currentTheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.provider_fetching),
                            fontSize = ProviderRowNameFontSize,
                            color = currentTheme.mutedForeground
                        )
                    }
                }

                ModelSheetState.Error -> {
                    Text(
                        text = stringResource(R.string.provider_fetch_failed),
                        fontSize = ProviderRowNameFontSize,
                        color = currentTheme.mutedForeground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAction(ProviderAction.FetchModelsClicked) },
                            currentTheme = currentTheme,
                            fillWidth = true,
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                            testTag = "provider_model_retry_btn"
                        ) {
                            Text(
                                text = stringResource(R.string.provider_retry),
                                fontSize = ProviderActionButtonFontSize,
                                fontWeight = FontWeight.Medium,
                                color = currentTheme.foreground
                            )
                        }
                        Button(
                            onClick = { onAction(ProviderAction.CustomModelClicked) },
                            currentTheme = currentTheme,
                            fillWidth = true,
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                            testTag = "provider_model_custom_btn"
                        ) {
                            Text(
                                text = stringResource(R.string.provider_custom_model),
                                fontSize = ProviderActionButtonFontSize,
                                fontWeight = FontWeight.Medium,
                                color = currentTheme.foreground
                            )
                        }
                    }
                }

                is ModelSheetState.ModelList -> {
                    // 搜索：聚合网关那种动辄几百个模型的端点，先过滤再列。按 id 或目录名字匹配。
                    var query by remember { mutableStateOf("") }
                    val keyword = query.trim()
                    val matches = sheet.items.filter { item ->
                        keyword.isEmpty() ||
                            item.modelId.contains(keyword, ignoreCase = true) ||
                            item.name?.contains(keyword, ignoreCase = true) == true
                    }

                    ProviderField(
                        label = stringResource(R.string.provider_model_search),
                        value = query,
                        onValueChange = { query = it },
                        currentTheme = currentTheme,
                        keyboardType = KeyboardType.Text,
                        testTag = "provider_model_search_field"
                    )
                    Spacer(modifier = Modifier.height(ProviderFieldLabelSpacing))

                    // Always-available custom entry on top of the catalog.
                    Button(
                        onClick = { onAction(ProviderAction.CustomModelClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "provider_model_custom_entry"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ProviderRowIconTextSpacing)
                        ) {
                            Icon(
                                imageVector = LucideIcons.Pencil,
                                contentDescription = null,
                                tint = currentTheme.primary,
                                modifier = Modifier.size(ProviderRowIconSize)
                            )
                            Text(
                                text = stringResource(R.string.provider_custom_model),
                                fontSize = ProviderRowNameFontSize,
                                fontWeight = FontWeight.Medium,
                                color = currentTheme.primary
                            )
                        }
                    }
                    ProviderDivider(currentTheme)

                    if (sheet.items.isEmpty()) {
                        Text(
                            text = stringResource(R.string.provider_fetch_empty),
                            fontSize = ProviderRowNameFontSize,
                            color = currentTheme.mutedForeground,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else if (matches.isEmpty()) {
                        // 有模型，但都被搜索词滤掉了。
                        Text(
                            text = stringResource(R.string.provider_model_search_empty),
                            fontSize = ProviderRowNameFontSize,
                            color = currentTheme.mutedForeground,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = ModelSheetListMaxHeight)
                                .verticalScroll(rememberScrollState())
                        ) {
                            matches.forEach { item ->
                                Button(
                                    onClick = { onAction(ProviderAction.ModelSelected(item.modelId)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "provider_model_pick_${item.modelId}"
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            // 目录给了名字就用它（例如 `DeepSeek-V4-Pro`），否则显示线上 id。
                                            text = item.name ?: item.modelId,
                                            fontSize = ProviderRowNameFontSize,
                                            color = currentTheme.foreground,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = LucideIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = currentTheme.mutedForeground,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                ProviderDivider(currentTheme)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Model editor sheet ─────────────────────────────────────────────────

/**
 * Parameter sheet for one model: the wire model id (prefilled when picked
 * from the catalog, free-form when custom) plus context / max-output token
 * budgets. Save appends or replaces the entry on the provider draft.
 */
@Composable
private fun ModelEditorSheet(
    editor: ModelEditorState,
    currentTheme: CssVariables,
    onAction: (ProviderAction) -> Unit,
) {
    // sheet 是独立窗口，页面那层的键盘控制器收不到它里面的输入框：关掉它之前先在自己这层把键盘
    // 收下来、焦点清干净。不然键盘会留在屏幕上，下次进来还会弹回原来那个框。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    BottomSheet(
        onDismiss = { onAction(ProviderAction.ModelCancelClicked) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("provider_model_editor_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ModelSheetPaddingHorizontal)
                .padding(bottom = 16.dp)
        ) {
            ProviderField(
                label = stringResource(R.string.provider_model_id),
                value = editor.modelId,
                onValueChange = { onAction(ProviderAction.ModelIdChanged(it)) },
                currentTheme = currentTheme,
                keyboardType = KeyboardType.Text,
                testTag = "provider_model_field_id",
                // 从「获取模型列表」里挑来的模型：这个 id 就是端点报的那一个，不给改 —— 要自己写 id 就走
                // 「自定义模型 ID」那条路（那种模型的 id 本来就该由用户定）。
                readOnly = editor.modelIdLocked,
                hint = if (editor.modelIdLocked) {
                    stringResource(R.string.provider_model_id_locked_hint)
                } else {
                    null
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
            ProviderField(
                label = stringResource(R.string.provider_context_length),
                value = editor.contextLength,
                onValueChange = { onAction(ProviderAction.ModelContextLengthChanged(it)) },
                currentTheme = currentTheme,
                keyboardType = KeyboardType.Number,
                testTag = "provider_model_field_context"
            )
            Spacer(modifier = Modifier.height(14.dp))
            ProviderField(
                label = stringResource(R.string.provider_max_output_length),
                value = editor.maxOutputLength,
                onValueChange = { onAction(ProviderAction.ModelMaxOutputChanged(it)) },
                currentTheme = currentTheme,
                keyboardType = KeyboardType.Number,
                testTag = "provider_model_field_output",
                hint = stringResource(R.string.provider_max_output_hint)
            )

            // 「推理强度」：这个模型的默认档（新建会话的起点）。**目录里认得的模型不显示它** —— 它的档位
            // 由后端目录直接给（那套档位会自动出现在聊天页的档位面板里），这里再摆一个只会读成重复设置。
            // 目录外的模型（自己填的 id）没有这层数据，才需要在这儿自己定。
            //
            // 这里**没有**"这个模型支持哪些档"那一项：那是目录的事（codex 的
            // `supported_reasoning_levels` 同样来自后端目录），界面上再填一遍等于第二份真源。
            if (!editor.isCatalogModel) {
                ProviderReasoningEffortField(
                    value = editor.reasoningEffort,
                    onValueChange = { onAction(ProviderAction.ModelReasoningEffortSelected(it)) },
                    currentTheme = currentTheme
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAction(ProviderAction.ModelCancelClicked) },
                    modifier = Modifier.weight(1f),
                    currentTheme = currentTheme,
                    fillWidth = true,
                    contentAlignment = Alignment.Center,
                    testTag = "provider_model_cancel_btn"
                ) {
                    Text(
                        text = stringResource(R.string.provider_cancel),
                        fontSize = ProviderRowNameFontSize,
                        fontWeight = FontWeight.Medium,
                        color = currentTheme.foreground
                    )
                }
                Button(
                    onClick = { onAction(ProviderAction.ModelSaveClicked) },
                    modifier = Modifier.weight(1f),
                    currentTheme = currentTheme,
                    fillWidth = true,
                    containerColor = currentTheme.primary,
                    border = null,
                    contentAlignment = Alignment.Center,
                    testTag = "provider_model_save_btn"
                ) {
                    Text(
                        text = stringResource(R.string.provider_save),
                        fontSize = ProviderRowNameFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = currentTheme.primaryForeground
                    )
                }
            }
        }
    }
}

// ── Shared bits ────────────────────────────────────────────────────────

/** Small-caps style section label used above grouped cards in the editor. */
@Composable
private fun ProviderSectionLabel(text: String, currentTheme: CssVariables) {
    Text(
        text = text,
        fontSize = ProviderFieldLabelFontSize,
        fontWeight = FontWeight.SemiBold,
        color = currentTheme.mutedForeground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ProviderFieldLabelSpacing)
    )
}

/**
 * Themed single-line text field: label above a `subtleSurface` input box with
 * a hairline border (same chrome language as the rest of the settings flow).
 */


@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    currentTheme: CssVariables,
    keyboardType: KeyboardType,
    testTag: String,
    obscure: Boolean = false,
    /** 字段底下的一句说明；不传就没有。 */
    hint: String? = null,
    /** 只读：值照常显示（还能选中复制），但点不动、不弹键盘 —— 用于"这个值不是在这儿填的"。 */
    readOnly: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = ProviderFieldLabelFontSize,
            fontWeight = FontWeight.SemiBold,
            color = currentTheme.mutedForeground
        )
        Spacer(modifier = Modifier.height(ProviderFieldLabelSpacing))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = readOnly,
            textStyle = TextStyle(
                fontSize = ProviderFieldTextFontSize,
                color = if (readOnly) currentTheme.mutedForeground else currentTheme.foreground
            ),
            cursorBrush = SolidColor(currentTheme.primary),
            visualTransformation =
                if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .clip(AppShapes.small)
                .background(currentTheme.subtleSurface)
                .border(1.dp, currentTheme.border, AppShapes.small)
                .padding(
                    horizontal = ProviderFieldPaddingHorizontal,
                    vertical = ProviderFieldPaddingVertical
                )
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(ProviderFieldLabelSpacing))
            Text(
                text = hint,
                fontSize = ProviderRowBaseUrlFontSize,
                color = currentTheme.mutedForeground
            )
        }
    }
}

/** Hairline separator between rows inside a grouped card. */
@Composable
private fun ProviderDivider(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProviderRowDividerHeight)
            .background(currentTheme.border)
    )
}

// ── 「推理强度」下拉（只在目录外的模型上出现） ─────────────────────────────

private val ProviderReasoningChevronSize = 14.dp
private val ProviderReasoningPaddingVertical = 6.dp

/**
 * 推理强度：一行「标签 + 当前档位」，点开是个下拉。
 *
 * 说明写在选项里（见 strings 的 hint），所以这里不再重复一行小字。
 */
@Composable
private fun ProviderReasoningEffortField(
    value: String,
    onValueChange: (String) -> Unit,
    currentTheme: CssVariables,
) {
    var open by remember { mutableStateOf(false) }
    val current = ReasoningEffort.options.firstOrNull { it.value == value }
        ?: ReasoningEffort.options.first()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.provider_reasoning_effort),
            fontSize = ProviderRowBaseUrlFontSize,
            color = currentTheme.mutedForeground
        )
        Spacer(modifier = Modifier.width(ProviderFieldLabelSpacing))
        Box {
            Row(
                modifier = Modifier
                    .clip(AppShapes.small)
                    .border(1.dp, currentTheme.border, AppShapes.small)
                    .clickable { open = true }
                    .padding(
                        horizontal = ProviderFieldPaddingHorizontal,
                        vertical = ProviderReasoningPaddingVertical
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(current.labelRes),
                    fontSize = ProviderRowBaseUrlFontSize,
                    color = currentTheme.foreground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = LucideIcons.ChevronDown,
                    contentDescription = null,
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier.size(ProviderReasoningChevronSize)
                )
            }
            if (open) {
                // 用我们自己的 BottomSheet（与同页的模型列表、供应商表单同一个做法），不用 M3 的
                // DropdownMenu —— 全仓最后一处 Material 菜单早先也是这样换掉的。
                BottomSheet(
                    onDismiss = { open = false },
                    currentTheme = currentTheme,
                    modifier = Modifier.testTag("provider_reasoning_effort_sheet")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ProviderFieldPaddingHorizontal)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.provider_reasoning_effort),
                            fontSize = ModelSheetTitleFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = currentTheme.foreground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ReasoningEffort.options.forEach { option ->
                            val selected = option.value == value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.small)
                                    .clickable {
                                        onValueChange(option.value)
                                        open = false
                                    }
                                    .padding(vertical = ProviderReasoningPaddingVertical),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    fontSize = ProviderRowBaseUrlFontSize,
                                    color = if (selected) {
                                        currentTheme.primary
                                    } else {
                                        currentTheme.foreground
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = LucideIcons.Check,
                                        contentDescription = null,
                                        tint = currentTheme.primary,
                                        modifier = Modifier.size(ProviderReasoningChevronSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(ProviderFieldLabelSpacing))
    Text(
        text = stringResource(R.string.provider_reasoning_effort_hint),
        fontSize = ProviderRowBaseUrlFontSize,
        color = currentTheme.mutedForeground
    )
}
