package com.lhzkml.jasmine.feature.main.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhzkml.jasmine.core.ui.components.ProductionTopNavBar
import com.lhzkml.jasmine.core.ui.components.SidebarDrawer
import com.lhzkml.jasmine.feature.main.impl.chat.ChatScreen
import com.lhzkml.jasmine.feature.main.impl.chat.ChatViewModel

/**
 * Main destination: push-canvas sidebar + the chat surface. Stateless renderer of
 * [MainState]: every user intent leaves through [onAction] (wired to
 * [MainViewModel.trySendAction] by the host), keeping the single stateFlow
 * subscription at the activity root.
 *
 * The settings flow is NOT hosted here — it lives on the Navigation 3 back
 * stack as sibling destinations (see [MainNavHost]), so system back,
 * predictive back and process-death restore come from the navigation library.
 * The chat has its own [ChatViewModel], scoped to this navigation entry.
 *
 * There is no bottom navigation bar: the app has a single top-level surface, so a
 * tab bar would only cost vertical space and drag a whole layer of IME/inset
 * choreography along with it (the bar had to step aside for the keyboard, which
 * made a focused composer jump).
 */
@Composable
fun MainScreen(
    state: MainState,
    onAction: (MainAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedBg by animateColorAsState(
        targetValue = state.theme.background,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bg_color"
    )

    // Only the drawer still needs an explicit back intercept; every other back
    // navigation is the NavDisplay back stack's job.
    BackHandler(enabled = state.isSidebarOpen) {
        onAction(MainAction.SidebarClosed)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Push-canvas sidebar drawer; the main scaffold is its pushed content.
        SidebarDrawer(
            isOpen = state.isSidebarOpen,
            currentTheme = state.theme,
            onOpen = { onAction(MainAction.SidebarOpened) },
            onOpenSettings = onOpenSettings,
            onClose = { onAction(MainAction.SidebarClosed) }
        ) {
            Scaffold(
                containerColor = animatedBg,
                contentColor = state.theme.foreground,
                topBar = {
                    ProductionTopNavBar(
                        currentTheme = state.theme,
                        onOpenSidebar = { onAction(MainAction.SidebarToggled) },
                    )
                },
                // safeDrawing = systemBars ∪ displayCutout ∪ ime ∪ tappableElement
                // — it already includes the keyboard. Scaffold consumes all of it
                // and hands the result back as innerPadding, so the content only
                // needs Modifier.padding(innerPadding).
                //
                // Do NOT also apply imePadding() here: safeDrawing already covers
                // the IME, and applying both double-counts the bottom inset (the
                // official insets guide calls this out explicitly — "导航栏 inset
                // 与 IME inset 被同时应用 → 底部出现两个条形空白"). Pick one owner:
                // either Scaffold via contentWindowInsets, or the content via
                // imePadding() — never both.
                contentWindowInsets = WindowInsets.safeDrawing,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Collect the chat state here rather than at the root: a
                    // streaming reply then recomposes only the chat surface,
                    // not the whole NavHost tree.
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
                    ChatScreen(
                        state = chatState,
                        onAction = chatViewModel::trySendAction,
                        currentTheme = state.theme,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }
}
