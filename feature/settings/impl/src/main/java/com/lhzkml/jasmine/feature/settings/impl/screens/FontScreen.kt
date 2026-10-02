package com.lhzkml.jasmine.feature.settings.impl.screens

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.widgets.icon.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.InstalledFont
import com.lhzkml.jasmine.core.data.model.PresetFont
import com.lhzkml.jasmine.core.data.model.PresetFontCatalog
import com.lhzkml.jasmine.core.ui.theme.AppTypographyChoice
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.R
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Accepted MIME types for the system font picker.
 */
private val FontPickerMimeTypes = arrayOf(
    "font/ttf",
    "font/otf",
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/octet-stream",
    "*/*"
)

/**
 * Dedicated font settings page: the system typography engine, user-installed
 * custom fonts (upload / download / select / delete), a downloadable font
 * library, and an entry that opens the font-size adjustment page.
 */
@Composable
fun FontScreen(
    currentTheme: CssVariables,
    selectedTypography: AppTypographyChoice,
    onTypographyChange: (AppTypographyChoice) -> Unit,
    fontScale: Float,
    onOpenFontSize: () -> Unit,
    installedFonts: List<InstalledFont>,
    activeCustomFontId: String,
    downloadProgress: Map<String, Float>,
    fontFamilyFor: (String) -> FontFamily?,
    onSelectCustomFont: (String) -> Unit,
    onDeleteCustomFont: (String) -> Unit,
    onDownloadFont: (PresetFont) -> Unit,
    onImportFont: (Uri, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onImportFont(uri, "font.ttf")
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
        // ==========================================
        // SECTION 1: Typography engine (system fonts)
        // ==========================================
        FontSectionHeader(
            title = stringResource(R.string.settings_section_typography),
            icon = LucideIcons.FormatSize,
            currentTheme = currentTheme
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, AppShapes.large)
        ) {
            AppTypographyChoice.entries.forEachIndexed { index, style ->
                // A system engine reads as selected only while no custom font overrides it.
                val isSelected = activeCustomFontId.isEmpty() && selectedTypography == style

                Button(
                    onClick = { onTypographyChange(style) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_typography_${style.name}"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = style.font,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) currentTheme.primary else currentTheme.mutedForeground,
                                modifier = Modifier.width(26.dp)
                            )

                            Text(
                                text = stringResource(style.titleRes),
                                fontFamily = style.font,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = currentTheme.foreground
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = LucideIcons.Check,
                                contentDescription = null,
                                tint = currentTheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (index < AppTypographyChoice.entries.size - 1) {
                    FontRowDivider(currentTheme)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 2: Custom fonts (installed)
        // ==========================================
        FontSectionHeader(
            title = stringResource(R.string.settings_section_custom_fonts),
            icon = LucideIcons.Upload,
            currentTheme = currentTheme
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Upload entry: opens the system document picker for a .ttf / .otf file.
        Button(
            onClick = { fontPickerLauncher.launch(FontPickerMimeTypes) },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.medium,
            testTag = "font_upload_entry"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, AppShapes.medium)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = LucideIcons.Upload,
                    contentDescription = null,
                    tint = currentTheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_custom_fonts_upload),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = currentTheme.foreground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_custom_fonts_upload_subtitle),
                        fontSize = 11.sp,
                        color = currentTheme.mutedForeground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (installedFonts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.medium)
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, AppShapes.medium)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_custom_fonts_empty),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.large)
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, AppShapes.large)
            ) {
                installedFonts.forEachIndexed { index, font ->
                    val isActive = activeCustomFontId == font.id
                    val uploadedBadge = if (!font.isPreset) stringResource(R.string.settings_font_badge_uploaded) else null
                    val subtitle = if (uploadedBadge != null) {
                        "${formatSize(font.sizeBytes)}  •  $uploadedBadge"
                    } else {
                        formatSize(font.sizeBytes)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCustomFont(font.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = fontFamilyFor(font.id) ?: FontFamily.Default,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) currentTheme.primary else currentTheme.mutedForeground,
                                modifier = Modifier.width(26.dp)
                            )

                            Column {
                                Text(
                                    text = font.displayName,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = currentTheme.foreground
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 10.5.sp,
                                    color = currentTheme.mutedForeground
                                )
                            }
                        }

                        if (isActive) {
                            Icon(
                                imageVector = LucideIcons.Check,
                                contentDescription = null,
                                tint = currentTheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(role = Role.Button) { onDeleteCustomFont(font.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.Trash,
                                contentDescription = stringResource(R.string.settings_font_delete_cd),
                                tint = currentTheme.mutedForeground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (index < installedFonts.size - 1) {
                        FontRowDivider(currentTheme)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 3: Font library (downloadable presets)
        // ==========================================
        val downloadable = PresetFontCatalog.ALL.filter { preset ->
            installedFonts.none { it.fileName == preset.fileName }
        }
        if (downloadable.isNotEmpty()) {
            FontSectionHeader(
                title = stringResource(R.string.settings_section_font_library),
                icon = LucideIcons.CloudDownload,
                currentTheme = currentTheme
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.large)
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, AppShapes.large)
            ) {
                downloadable.forEachIndexed { index, preset ->
                    val progress = downloadProgress[preset.fileName]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Aa",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTheme.mutedForeground,
                                modifier = Modifier.width(26.dp)
                            )

                            Column {
                                Text(
                                    text = preset.displayName,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = currentTheme.foreground
                                )
                                Text(
                                    text = formatSize(preset.sizeBytes),
                                    fontSize = 10.5.sp,
                                    color = currentTheme.mutedForeground
                                )
                            }
                        }

                        if (progress != null) {
                            Column(
                                modifier = Modifier.width(96.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = currentTheme.primary,
                                    trackColor = currentTheme.border
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${(progress * 100).roundToInt()}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = currentTheme.primary
                                )
                            }
                        } else {
                            // 不做任何外观定制：直接用自有 Button 的默认外观（primary 填充按钮），
                            // 图标与文字的颜色/字型由组件下发、自动继承。
                            WidgetsButton(
                                onClick = { onDownloadFont(preset) },
                                modifier = Modifier.testTag("font_download_${preset.id}")
                            ) {
                                Icon(
                                    imageVector = LucideIcons.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text(text = stringResource(R.string.settings_font_download))
                            }
                        }
                    }

                    if (index < downloadable.size - 1) {
                        FontRowDivider(currentTheme)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==========================================
        // SECTION 4: Font size -> dedicated adjust page
        // ==========================================
        FontSectionHeader(
            title = stringResource(R.string.settings_section_font_size),
            icon = LucideIcons.Type,
            currentTheme = currentTheme
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onOpenFontSize() },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.medium,
            testTag = "font_size_entry"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, AppShapes.medium)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_font_size_label),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = currentTheme.foreground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_font_size_entry_subtitle),
                        fontSize = 11.sp,
                        color = currentTheme.mutedForeground
                    )
                }

                Text(
                    text = "${(fontScale * 100).roundToInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = null,
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun FontSectionHeader(
    title: String,
    icon: ImageVector,
    currentTheme: CssVariables
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = currentTheme.primary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = title,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = currentTheme.mutedForeground
        )
    }
}

@Composable
private fun FontRowDivider(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(currentTheme.border.copy(alpha = 0.5f))
    )
}

/** Formats a byte count as a short human-readable size label. Locale.ROOT pins the decimal dot (some locales render a comma). */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(Locale.ROOT, bytes / 1_048_576f)
    bytes >= 1024L -> "%.0f KB".format(Locale.ROOT, bytes / 1024f)
    else -> "$bytes B"
}
