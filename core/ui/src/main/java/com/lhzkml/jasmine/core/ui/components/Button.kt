package com.lhzkml.jasmine.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

// ── Button defaults ────────────────────────────────────────────────────

/** Themed-mode chrome defaults: inner padding and hairline border width. */
private val ButtonDefaultContentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val ButtonDefaultBorderWidth = 1.dp

/**
 * Minimum touch target per the Android accessibility guidelines
 * (48dp x 48dp, see developer.android.com/guide/topics/ui/accessibility).
 * Smaller visuals keep their size; the extra area stays transparent.
 */
private val ButtonMinTouchTarget = 48.dp

/** Card button defaults: selection-aware border widths and content padding. */
private val CardButtonSelectedBorderWidth = 2.dp
private val CardButtonUnselectedBorderWidth = 1.dp
private val CardButtonDefaultContentPadding = PaddingValues(14.dp)

/**
 * Shared button: click handling + ripple control + optional themed chrome.
 *
 * Two modes:
 * - Behavior-only (default, [currentTheme] == null): no appearance of its own;
 *   the call site draws everything via [content]. This is the original mode —
 *   every existing usage stays pixel-identical without any change.
 * - Themed ([currentTheme] != null): the button draws its own chrome
 *   (subtleSurface background, 1dp border, radiusSm corners, 12x10 padding),
 *   and every aspect is overridable: [shape], [containerColor], [border],
 *   [contentPadding], [contentAlignment], [fillWidth]. Pass
 *   `border = BorderStroke(0.dp, Color.Transparent)` to drop the border.
 *   Width wraps the content by default; set [fillWidth] to true for
 *   full-width row/card buttons.
 *
 * Note: this is the feature's own button, distinct from Material 3's
 * `androidx.compose.material3.Button` (import with an alias where both meet).
 *
 * @param shape         clip shape so the press ripple follows rounded corners
 * @param rippleEnabled set false to suppress press feedback entirely
 * @param content       the visual content (in behavior-only mode it also
 *                      provides all appearance)
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    rippleEnabled: Boolean = true,
    testTag: String? = null,
    currentTheme: CssVariables? = null,
    containerColor: Color? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    contentAlignment: Alignment? = null,
    fillWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    val themedShape: Shape =
        if (currentTheme != null) RoundedCornerShape(currentTheme.radiusSm) else RectangleShape
    val resolvedShape = shape ?: themedShape

    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier
    val clickModifier = if (rippleEnabled) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    }

    Box(
        modifier = tagged
            .sizeIn(minWidth = ButtonMinTouchTarget, minHeight = ButtonMinTouchTarget)
            .clip(resolvedShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (currentTheme != null) {
            // Themed mode: the button owns the chrome; every value overridable.
            Box(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .sizeIn(minWidth = ButtonMinTouchTarget, minHeight = ButtonMinTouchTarget)
                    .background(containerColor ?: currentTheme.subtleSurface)
                    .border(
                        border = border ?: BorderStroke(ButtonDefaultBorderWidth, currentTheme.border),
                        shape = resolvedShape
                    )
                    .padding(contentPadding ?: ButtonDefaultContentPadding),
                contentAlignment = contentAlignment ?: Alignment.TopStart
            ) {
                content()
            }
        } else {
            // Behavior-only mode: unchanged legacy rendering.
            content()
        }
    }
}

/**
 * Selectable card button: themed [Button] whose border highlights when
 * selected (primary color + thicker). Used for mode / font-family pickers.
 *
 * No longer an independent implementation — a stable, thin API over the
 * merged button so existing call sites keep working unchanged.
 */
@Composable
fun CardButton(
    onClick: () -> Unit,
    isSelected: Boolean,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(currentTheme.radiusMd),
    containerColor: Color = currentTheme.card,
    selectedBorderWidth: Dp = CardButtonSelectedBorderWidth,
    contentPadding: PaddingValues = CardButtonDefaultContentPadding,
    contentAlignment: Alignment = Alignment.TopStart,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        testTag = testTag,
        currentTheme = currentTheme,
        containerColor = containerColor,
        border = BorderStroke(
            width = if (isSelected) selectedBorderWidth else CardButtonUnselectedBorderWidth,
            color = if (isSelected) currentTheme.primary else currentTheme.border
        ),
        contentPadding = contentPadding,
        contentAlignment = contentAlignment,
        fillWidth = true
    ) {
        content()
    }
}
