package com.lhzkml.jasmine.feature.main.impl.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Craft canvas tab — intentionally blank.
 *
 * Everything that used to live here (preset palette switcher, hero main
 * card, custom main editor, typography engine selector, live CSS token
 * panel and the one-click "copy CSS" action) has been removed from the home
 * page.
 *
 * Theme switching and font switching are NOT gone from the app: they are
 * reachable only from the settings flow —
 * [com.lhzkml.jasmine.core.ui.theme.ThemeResolver] drives AppearanceSettings and
 * [AppTypographyChoice] drives FontSettings — so their shared implementations
 * stay in use and must not be deleted. The tab itself is deliberately left as
 * an empty surface (same treatment as the SETTINGS tab in the host).
 */
@Composable
fun CanvasScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}
