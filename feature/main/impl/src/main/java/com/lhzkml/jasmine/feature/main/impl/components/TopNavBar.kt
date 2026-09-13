package com.lhzkml.jasmine.feature.main.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R

// ── Top nav bar dimensions ─────────────────────────────────────────────

/** Content row height (both main and sub-page shapes). */
private val TopNavBarHeight = 47.dp

/** Horizontal padding of the content row. */
private val TopNavBarPaddingHorizontal = 10.dp

/** Icon size of the leading action (menu / back). */
private val TopNavActionIconSize = 28.dp

/** Sub-page title typography. */
private val TopNavTitleFontSize = 14.5.sp
private val TopNavTitleLetterSpacing = (-0.2).sp

/** Height of the hairline divider below the bar. */
private val TopNavDividerHeight = 1.dp

/**
 * Minimal top navigation bar with two shapes:
 * - Main shape ([pageTitle] == null): sidebar toggle button on the left.
 * - Sub-page shape ([pageTitle] != null, e.g. settings flow): back button on
 *   the left + centered page title.
 * Always finished with the 1px bottom divider rule.
 */
@Composable
fun ProductionTopNavBar(
    currentTheme: CssVariables,
    onOpenSidebar: () -> Unit = {},
    pageTitle: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(currentTheme.background)
            .statusBarsPadding()
    ) {
        if (pageTitle != null) {
            // Sub-page shape: back button (left) + centered title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopNavBarHeight)
                    .padding(horizontal = TopNavBarPaddingHorizontal)
            ) {
                Button(
                    onClick = { onBack() },
                    modifier = Modifier.align(Alignment.CenterStart),
                    rippleEnabled = false,
                    testTag = "top_nav_back_btn"
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.top_nav_cd_back),
                        tint = currentTheme.foreground,
                        modifier = Modifier.size(TopNavActionIconSize)
                    )
                }

                Text(
                    text = pageTitle,
                    fontSize = TopNavTitleFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = TopNavTitleLetterSpacing,
                    color = currentTheme.foreground,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            // Main shape: sidebar toggle button (plain icon, no ripple)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopNavBarHeight)
                    .padding(horizontal = TopNavBarPaddingHorizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenSidebar() },
                    rippleEnabled = false,
                    testTag = "top_nav_sidebar_btn"
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.top_nav_cd_open_sidebar),
                        tint = currentTheme.foreground,
                        modifier = Modifier.size(TopNavActionIconSize)
                    )
                }
            }
        }

        // 1px Subtle Bottom Border Rule (Linear / Vercel style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopNavDividerHeight)
                .background(currentTheme.border)
        )
    }
}
