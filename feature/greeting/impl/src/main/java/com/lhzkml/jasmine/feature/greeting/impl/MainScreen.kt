package com.lhzkml.jasmine.feature.greeting.impl

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
import com.lhzkml.jasmine.feature.greeting.impl.components.NavigationTab
import com.lhzkml.jasmine.feature.greeting.impl.components.ProductionBottomNavBar
import com.lhzkml.jasmine.feature.greeting.impl.components.ProductionTopNavBar
import com.lhzkml.jasmine.feature.greeting.impl.components.SidebarDrawer
import com.lhzkml.jasmine.feature.greeting.impl.components.SidebarEdgeZone
import com.lhzkml.jasmine.feature.greeting.impl.screens.CanvasScreen
import com.lhzkml.jasmine.feature.greeting.impl.screens.TokensScreen
import com.lhzkml.jasmine.feature.greeting.impl.screens.TypeStudioScreen

/**
 * Main destination: push-canvas sidebar + 4-tab scaffold. Stateless renderer of
 * [GreetingState]: every user intent leaves through [onAction] (wired to
 * [GreetingViewModel.trySendAction] by the host), keeping the single stateFlow
 * subscription at the activity root.
 *
 * The settings flow is NOT hosted here — it lives on the Navigation 3 back
 * stack as sibling destinations (see [GreetingNavHost]), so system back,
 * predictive back and process-death restore come from the navigation library.
 */
@Composable
fun MainScreen(
    state: GreetingState,
    onAction: (GreetingAction) -> Unit,
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
        onAction(GreetingAction.SidebarClosed)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Push-canvas sidebar drawer; the main scaffold is its pushed content.
        SidebarDrawer(
            isOpen = state.isSidebarOpen,
            currentTheme = state.theme,
            onOpen = { onAction(GreetingAction.SidebarOpened) },
            onOpenSettings = onOpenSettings,
            onClose = { onAction(GreetingAction.SidebarClosed) }
        ) {
            Scaffold(
                containerColor = animatedBg,
                contentColor = state.theme.foreground,
                topBar = {
                    ProductionTopNavBar(
                        currentTheme = state.theme,
                        onOpenSidebar = { onAction(GreetingAction.SidebarToggled) },
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
                    onTabSelected = { onAction(GreetingAction.TabSelected(it)) },
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
                        // All three craft tabs are intentionally blank surfaces:
                        // their content was cleared from the home page. Theme and
                        // font switching now live only in the settings flow
                        // (AppearanceSettings / FontSettings), which keeps their
                        // shared implementations in use.
                        NavigationTab.CANVAS -> CanvasScreen()
                        NavigationTab.TYPOGRAPHY -> TypeStudioScreen()
                        NavigationTab.TOKENS -> TokensScreen()
                        // 4th tab is intentionally blank (settings moved to the sidebar flow)
                        NavigationTab.SETTINGS -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
