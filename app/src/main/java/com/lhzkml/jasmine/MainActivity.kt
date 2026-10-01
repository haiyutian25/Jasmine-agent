package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhzkml.jasmine.core.ui.theme.LocalContentFontFamily
import com.lhzkml.jasmine.core.ui.theme.JasmineTheme
import com.lhzkml.jasmine.feature.main.impl.MainAction
import com.lhzkml.jasmine.feature.main.impl.MainNavHost
import com.lhzkml.jasmine.feature.main.impl.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * App main: single Activity, theme application and navigation assembly.
 * All feature state lives in [MainViewModel] (MVVM + unidirectional data
 * flow): the UI collects [MainViewModel.stateFlow] and reports every
 * change back through [MainViewModel.trySendAction].
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private companion object {
        // Scrims mirroring enableEdgeToEdge()'s auto defaults in activity-compose.
        val defaultLightScrim: Int = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val defaultDarkScrim: Int = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()

            // Keep the view model's system dark-mode state in sync so the
            // SYSTEM color mode follows the OS setting live.
            val isSystemDark = isSystemInDarkTheme()
            LaunchedEffect(isSystemDark) {
                viewModel.trySendAction(MainAction.SystemDarkModeChanged(isSystemDark))
            }

            // Re-apply system bar styles whenever the resolved in-app theme
            // flips between light and dark. The default enableEdgeToEdge()
            // above follows the resource-level mode (themes.xml stays Light),
            // so pinning dark mode in-app would leave dark status bar icons
            // on a dark background.
            val isAppDark = state.theme.isDark
            LaunchedEffect(isAppDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isAppDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isAppDark) {
                        SystemBarStyle.dark(defaultDarkScrim)
                    } else {
                        SystemBarStyle.light(defaultLightScrim, defaultDarkScrim)
                    }
                )
            }

            // Broadcast the resolved content font (system engine or an installed
            // custom font) and font scale to the whole tree. fontScale multiplies
            // every .sp text size app-wide.
            //
            // 系统无障碍字号与设置页的字号滑块是**相乘**关系：前者是用户在系统里设的
            // 无障碍偏好，不该被应用吞掉；后者是应用内的额外缩放（100% = 跟随系统）。
            // `baseDensity` 取的是应用树之外的系统密度，所以它的 fontScale 就是系统值。
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalContentFontFamily provides state.activeContentFont,
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale * state.fontScale
                )
            ) {
                JasmineTheme(cssVars = state.theme) {
                    MainNavHost(viewModel = viewModel, state = state)
                }
            }
        }
    }
}
