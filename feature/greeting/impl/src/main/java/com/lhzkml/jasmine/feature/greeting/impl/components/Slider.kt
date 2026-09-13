package com.lhzkml.jasmine.feature.greeting.impl.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import com.lhzkml.jasmine.core.ui.theme.CssVariables

// ── Slider dimensions ──────────────────────────────────────────────────

/** Title-row typography: label (left) and live value readout (right). */
private val SliderLabelFontSize = 12.5.sp
private val SliderValueFontSize = 11.5.sp

/**
 * Shared labeled value slider: title row (label + live value readout) above a
 * CSS-token themed Material slider. Stateless — value is hoisted to the caller.
 *
 * Note: this is the feature's own slider, distinct from Material 3's
 * `androidx.compose.material3.Slider` (imported with an alias internally).
 *
 * @param valueText formatted readout shown at the right (e.g. "42 sp")
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = SliderLabelFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
            Text(
                text = valueText,
                fontFamily = FontFamily.Monospace,
                fontSize = SliderValueFontSize,
                fontWeight = FontWeight.Bold,
                color = currentTheme.primary
            )
        }

        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = currentTheme.primary,
                activeTrackColor = currentTheme.primary,
                inactiveTrackColor = currentTheme.border
            )
        )
    }
}
