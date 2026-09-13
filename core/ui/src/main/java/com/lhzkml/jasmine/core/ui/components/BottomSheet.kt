package com.lhzkml.jasmine.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.theme.CssVariables

// ── BottomSheet dimensions ─────────────────────────────────────────────

/** Drag-handle geometry and its vertical inset from the sheet's top edge. */
private val BottomSheetHandleWidth = 36.dp
private val BottomSheetHandleHeight = 4.dp
private val BottomSheetHandleCornerRadius = 2.dp
private val BottomSheetHandleTopInset = 12.dp
private val BottomSheetHandleBottomInset = 8.dp

/**
 * Shared themed modal bottom sheet: a Material 3 [ModalBottomSheet] already
 * skinned with the theme's design tokens — `card` container, `cardForeground`
 * content and a themed drag handle. Stateless: the caller shows or hides it
 * simply by including or omitting it.
 *
 * Note: this is the feature's own bottom-sheet wrapper, distinct from Material
 * 3's `androidx.compose.material3.ModalBottomSheet` (used internally).
 *
 * @param onDismiss             called when the user dismisses the sheet
 * @param currentTheme          drives container / content / handle colors
 * @param skipPartiallyExpanded expand straight to full height when true
 * @param showDragHandle        draw the themed grab handle at the top
 * @param content               the sheet body
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    showDragHandle: Boolean = true,
    content: @Composable () -> Unit
) {
    // The sheet renders in its own popup window, which resets LocalDensity to
    // the window default and would drop the user's font scale. Capture the
    // ambient density (carrying the font scale) and restore it inside.
    val ambientDensity = LocalDensity.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val dragHandle: @Composable (() -> Unit)? = if (showDragHandle) {
        { BottomSheetDragHandle(currentTheme) }
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = currentTheme.card,
        contentColor = currentTheme.cardForeground,
        dragHandle = dragHandle,
        modifier = modifier
    ) {
        CompositionLocalProvider(LocalDensity provides ambientDensity) {
            content()
        }
    }
}

/** Default themed grab handle: a small rounded bar in muted foreground. */
@Composable
private fun BottomSheetDragHandle(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .padding(top = BottomSheetHandleTopInset, bottom = BottomSheetHandleBottomInset)
            .width(BottomSheetHandleWidth)
            .height(BottomSheetHandleHeight)
            .clip(RoundedCornerShape(BottomSheetHandleCornerRadius))
            .background(currentTheme.mutedForeground.copy(alpha = 0.4f))
    )
}
