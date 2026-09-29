package com.lhzkml.jasmine.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.core.ui.R
import com.lhzkml.jasmine.core.ui.theme.CssVariables

// ── BottomSheet dimensions ─────────────────────────────────────────────

/**
 * 右上角那个 X 的触摸区：按 Android 无障碍指南取 48dp，图形本身小得多，多出来的部分
 * 是透明的。
 */
private val BottomSheetCloseTouchTarget = 48.dp

/** X 图形本身的大小。 */
private val BottomSheetCloseIconSize = 20.dp

/** X 触摸区与 sheet 右上角之间的内缩（触摸区自带留白，这里只补一点）。 */
private val BottomSheetCloseEndInset = 4.dp
private val BottomSheetCloseTopInset = 4.dp

/**
 * Shared themed modal bottom sheet: a Material 3 [ModalBottomSheet] already
 * skinned with the theme's design tokens — `card` container and `cardForeground`
 * content. Stateless: the caller shows or hides it simply by including or
 * omitting it.
 *
 * Closing — there is no drag handle, and dragging never dismisses: the sheet
 * surface used to be draggable as a whole, so a finger slip while switching
 * text fields or scrolling could pull the sheet down. The way out is the X at
 * the top right, plus the scrim and the system back gesture, all through
 * [onDismiss]. The X is a bare icon with a 48dp touch target: no button chrome,
 * and deliberately not the feature's own `Button` — a component does not reach
 * into the business components.
 *
 * IME handling — the official recipe (Android "Window insets in Compose" +
 * M3 troubleshooting): a ModalBottomSheet's own layout does NOT include the
 * IME inset by default, so `contentWindowInsets` is set to
 * `ime ∪ navigationBars` here. M3 then sizes/anchors the sheet against the
 * keyboard and consumes those insets for the content. Sheet content
 * composables must NOT add their own `imePadding()` / `navigationBarsPadding()`
 * — nested inset modifiers would see them already consumed (no double padding)
 * but keeping a single source of truth here avoids per-frame anchor churn.
 *
 * Prerequisite (app level): `android:windowSoftInputMode="adjustResize"` plus
 * `enableEdgeToEdge()`, otherwise no IME inset is ever dispatched to Compose.
 *
 * Note: this is the feature's own bottom-sheet wrapper, distinct from Material
 * 3's `androidx.compose.material3.ModalBottomSheet` (used internally).
 *
 * @param onDismiss    called when the user dismisses the sheet (X, scrim, back)
 * @param currentTheme drives container / content / X colors
 * @param content      the sheet body, drawn under the X row
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // The sheet renders in its own popup window, which resets LocalDensity to
    // the window default and would drop the user's font scale. Capture the
    // ambient density (carrying the font scale) and restore it inside.
    val ambientDensity = LocalDensity.current

    val sheetState = rememberModalBottomSheetState(
        // 只有一个落点：全高。没有半展开档，也就没有别的可拖去处。
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = currentTheme.card,
        contentColor = currentTheme.cardForeground,
        // 顶部那条小横条不要了：它本来就是"往下拖"的提示。
        dragHandle = null,
        // 禁掉拖拽关闭靠的是这个开关，**不要**改用 confirmValueChange 去拦 Hidden：
        // M3 的遮罩点击是"先把 sheet 收到 Hidden、收到位了才调 onDismissRequest"，
        // 拦下 Hidden 会连遮罩关闭一起堵死（X 与返回键是直接回调，所以当时看着还正常）。
        sheetGesturesEnabled = false,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
        modifier = modifier
    ) {
        CompositionLocalProvider(LocalDensity provides ambientDensity) {
            // X 单独占一行放在内容之上，避免压住各 sheet 自己右上角的内容。
            Column(modifier = Modifier.fillMaxWidth()) {
                BottomSheetCloseButton(currentTheme, onDismiss)
                content()
            }
        }
    }
}

/** 右上角的关闭入口：一个朴素的 X，加一个 48dp 的触摸区。 */
@Composable
private fun BottomSheetCloseButton(currentTheme: CssVariables, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BottomSheetCloseTopInset, end = BottomSheetCloseEndInset),
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .size(BottomSheetCloseTouchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onDismiss
                )
                .testTag("bottom_sheet_close"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LucideIcons.Close,
                contentDescription = stringResource(R.string.bottom_sheet_cd_close),
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(BottomSheetCloseIconSize)
            )
        }
    }
}
