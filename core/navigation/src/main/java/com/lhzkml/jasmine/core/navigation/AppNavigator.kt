package com.lhzkml.jasmine.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Thin MVVM-friendly wrapper around Navigation 3's [NavBackStack].
 *
 * The [navigationState] is the single source of truth for the back stack;
 * mutation happens exclusively through the intent methods below, keeping
 * navigation state observable and testable.
 */
@Stable
class AppNavigator(val navigationState: NavBackStack<NavKey>) {

    fun navigate(key: NavKey) {
        navigationState.add(key)
    }

    fun replace(key: NavKey) {
        navigationState.clear()
        navigationState.add(key)
    }

    fun goBack(): Boolean {
        if (navigationState.size <= 1) return false
        navigationState.removeAt(navigationState.size - 1)
        return true
    }
}

/**
 * Creates an [AppNavigator] whose back stack survives configuration changes
 * and process death (Navigation 3 handles serialization of [NavKey]s).
 */
@Composable
fun rememberAppNavigator(vararg startKeys: NavKey): AppNavigator {
    val backStack = rememberNavBackStack(*startKeys)
    return remember(backStack) { AppNavigator(backStack) }
}
