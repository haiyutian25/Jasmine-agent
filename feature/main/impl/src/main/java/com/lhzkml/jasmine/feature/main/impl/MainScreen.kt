package com.lhzkml.jasmine.feature.main.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhzkml.jasmine.core.ui.components.NavigationTab
import com.lhzkml.jasmine.core.ui.components.ProductionBottomNavBar
import com.lhzkml.jasmine.core.ui.components.ProductionTopNavBar
import com.lhzkml.jasmine.core.ui.components.SidebarDrawer
import com.lhzkml.jasmine.core.ui.components.SidebarEdgeZone
import com.lhzkml.jasmine.feature.main.impl.chat.ChatScreen
import com.lhzkml.jasmine.feature.main.impl.chat.ChatViewModel

/**
 * Main destination: push-canvas sidebar + a single chat tab. Stateless renderer of
 * [MainState]: every user intent leaves through [onAction] (wired to
 * [MainViewModel.trySendAction] by the host), keeping the single stateFlow
 * subscription at the activity root.
 *
 * The settings flow is NOT hosted here — it lives on the Navigation 3 back
 * stack as sibling destinations (see [MainNavHost]), so system back,
 * predictive back and process-death restore come from the navigation library.
 * The chat has its own [ChatViewModel], scoped to this navigation entry.
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
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                // Bottom navigation hosts the pages itself; swipe-to-switch
                // is its optional feature. While the drawer is open, drags
                // keep closing it, and the left edge zone stays reserved
                // for the drawer's edge swipe.
                ProductionBottomNavBar(
                    currentTab = state.currentTab,
                    onTabSelected = { onAction(MainAction.TabSelected(it)) },
                    currentTheme = state.theme,
                    swipeable = true,
                    swipeEnabled = !state.isSidebarOpen,
                    excludedStartZone = SidebarEdgeZone,
                    // Flush with the screen bottom: the bar must stay exactly
                    // its 66dp content height, so drop the Scaffold's
                    // navigation-bar inset from its bottom padding.
                    modifier = Modifier.padding(
                        PaddingValues(
                            start = innerPadding.calculateLeftPadding(LocalLayoutDirection.current),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateRightPadding(LocalLayoutDirection.current),
                            bottom = 0.dp
                        )
                    )
                ) { tab ->
                    when (tab) {
                        NavigationTab.CHAT -> {
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
    }
}
