package com.example.feature.greeting.impl.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Type studio tab — intentionally blank.
 *
 * The typography playground that used to live here (type engine cards, hero
 * specimen, size / tracking sliders, italic toggle and the glyph matrix) has
 * been removed from the home page.
 *
 * Font switching is NOT gone from the app: [AppTypographyChoice] still drives
 * FontSettings in the settings flow, so the model and the shared components it
 * relies on (Slider, CardButton, the design-token theme, ...) remain in use and
 * must not be deleted. This tab is deliberately left as an empty surface.
 */
@Composable
fun TypeStudioScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}
