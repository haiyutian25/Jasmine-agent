package com.lhzkml.jasmine

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.lhzkml.jasmine.core.ui.theme.JasmineTheme
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.feature.main.impl.screens.CanvasScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8)
class MainScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun canvas_screenshot() {
    composeTestRule.setContent {
      JasmineTheme(cssVars = ProductionPalettes.GeistDark) {
        // The remaining home tab is intentionally blank now (its content was
        // cleared), so this captures an empty surface.
        CanvasScreen()
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/canvas.png")
  }
}
