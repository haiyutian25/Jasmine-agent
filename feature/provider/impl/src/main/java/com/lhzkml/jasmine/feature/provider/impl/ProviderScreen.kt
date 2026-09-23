package com.lhzkml.jasmine.feature.provider.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.ui.components.BottomSheet
import com.lhzkml.jasmine.core.ui.components.Button
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
            .clip(RoundedCornerShape(currentTheme.radiusLg))
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
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
                .clip(RoundedCornerShape(currentTheme.radiusLg))
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
                .padding(horizontal = ProviderRowPaddingHorizontal, vertical = ProviderRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProviderRowIconTextSpacing)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
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
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
            imageVector = Icons.Outlined.Cloud,
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
                imageVector = Icons.Outlined.Edit,
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
                    imageVector = Icons.Outlined.Delete,
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
            .clip(RoundedCornerShape(currentTheme.radiusLg))
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
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
                            imageVector = Icons.Default.Check,
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
                .clip(RoundedCornerShape(currentTheme.radiusLg))
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
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
        Button(
            onClick = { onAction(ProviderAction.FetchModelsClicked) },
            modifier = Modifier.weight(1f),
            currentTheme = currentTheme,
            fillWidth = true,
            contentAlignment = Alignment.Center,
            testTag = "provider_fetch_models_btn"
        ) {
            Text(
                text = stringResource(R.string.provider_fetch_models),
                fontSize = ProviderActionButtonFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
        }
        Button(
            onClick = { onAction(ProviderAction.CustomModelClicked) },
            modifier = Modifier.weight(1f),
            currentTheme = currentTheme,
            fillWidth = true,
            contentAlignment = Alignment.Center,
            testTag = "provider_custom_model_btn"
        ) {
            Text(
                text = stringResource(R.string.provider_custom_model),
                fontSize = ProviderActionButtonFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onAction(ProviderAction.CancelClicked) },
            modifier = Modifier.weight(1f),
            currentTheme = currentTheme,
            fillWidth = true,
            contentAlignment = Alignment.Center,
            testTag = "provider_cancel_btn"
        ) {
            Text(
                text = stringResource(R.string.provider_cancel),
                fontSize = ProviderRowNameFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
        }
        Button(
            onClick = { onAction(ProviderAction.SaveClicked) },
            modifier = Modifier.weight(1f),
            currentTheme = currentTheme,
            fillWidth = true,
            containerColor = currentTheme.primary,
            border = null,
            contentAlignment = Alignment.Center,
            testTag = "provider_save_btn"
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
                text = model.modelId,
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
                imageVector = Icons.Outlined.Edit,
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
                imageVector = Icons.Outlined.Delete,
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
                                imageVector = Icons.Outlined.Edit,
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

                    if (sheet.modelIds.isEmpty()) {
                        Text(
                            text = stringResource(R.string.provider_fetch_empty),
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
                            sheet.modelIds.forEach { modelId ->
                                Button(
                                    onClick = { onAction(ProviderAction.ModelSelected(modelId)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "provider_model_pick_$modelId"
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = modelId,
                                            fontSize = ProviderRowNameFontSize,
                                            color = currentTheme.foreground,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
    BottomSheet(
        onDismiss = { onAction(ProviderAction.ModelCancelClicked) },
        currentTheme = currentTheme,
        // 表单 sheet：整面可拖拽会让切换输入框时的微小滑动把 sheet 拖下去、
        // 丢焦点弹键盘，故禁用拖拽关闭（点遮罩/返回键仍可关）。
        allowDismissByDrag = false,
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
                testTag = "provider_model_field_id"
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
                testTag = "provider_model_field_output"
            )
            Spacer(modifier = Modifier.height(18.dp))
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
            textStyle = TextStyle(
                fontSize = ProviderFieldTextFontSize,
                color = currentTheme.foreground
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
                .clip(RoundedCornerShape(currentTheme.radiusSm))
                .background(currentTheme.subtleSurface)
                .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusSm))
                .padding(
                    horizontal = ProviderFieldPaddingHorizontal,
                    vertical = ProviderFieldPaddingVertical
                )
        )
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
