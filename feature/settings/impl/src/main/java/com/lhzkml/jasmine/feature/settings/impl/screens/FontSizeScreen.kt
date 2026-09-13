package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.R
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.components.Slider
import kotlin.math.roundToInt

/**
 * Font-size adjustment page with live preview and explicit save.
 *
 * Behaviour:
 * - The slider edits a LOCAL draft; only the preview box follows the draft in
 *   real time.
 * - The page's own chrome is pinned to the scale it was ENTERED with, so it
 *   does NOT live-rescale while dragging. A newly saved scale is only reflected
 *   after leaving and re-entering this page.
 * - "Save" commits the draft to the app-wide font scale (persisted); every
 *   other page picks it up immediately.
 */
@Composable
fun FontSizeScreen(
    currentTheme: CssVariables,
    fontScale: Float,
    onSave: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Scale captured on entry; pins this page's chrome so it ignores live
    // changes until the user leaves and comes back.
    val entryScale = remember { fontScale }
    // Local draft driven by the slider; committed only on Save.
    var draftScale by remember { mutableStateOf(fontScale) }
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(density = baseDensity.density, fontScale = entryScale)
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
            // ==========================================
            // SECTION 1: Live preview (follows the draft)
            // ==========================================
            FontSizeSectionHeader(
                title = stringResource(R.string.font_size_preview_label),
                currentTheme = currentTheme
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(currentTheme.radiusLg))
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
                    .padding(16.dp)
                    .testTag("font_size_preview_box")
            ) {
                // Only this box re-scales with the draft value.
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = draftScale)
                ) {
                    Text(
                        text = stringResource(R.string.font_size_preview_text),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = currentTheme.foreground
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // SECTION 2: Size slider (edits the draft)
            // ==========================================
            FontSizeSectionHeader(
                title = stringResource(R.string.settings_section_font_size),
                currentTheme = currentTheme
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(currentTheme.radiusLg))
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Slider(
                    value = draftScale,
                    onValueChange = { draftScale = it },
                    label = stringResource(R.string.settings_font_size_label),
                    valueText = "${(draftScale * 100).roundToInt()}%",
                    valueRange = 0.8f..1.6f,
                    currentTheme = currentTheme,
                    modifier = Modifier.testTag("font_size_slider")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save: commit the draft to the app-wide font scale. The
            // confirmation toast is emitted by the ViewModel (UDF event).
            Button(
                onClick = { onSave(draftScale) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("font_size_save_btn"),
                shape = RoundedCornerShape(currentTheme.radiusMd),
                currentTheme = currentTheme,
                containerColor = currentTheme.primary,
                border = BorderStroke(0.dp, Color.Transparent),
                contentAlignment = Alignment.Center,
                fillWidth = true
            ) {
                Text(
                    text = stringResource(R.string.font_size_save),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTheme.primaryForeground
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FontSizeSectionHeader(
    title: String,
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
            imageVector = Icons.Outlined.FormatSize,
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
