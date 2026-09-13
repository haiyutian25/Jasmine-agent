package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.core.ui.theme.MinimalTheme
import com.example.core.ui.theme.ProductionPalettes
import com.example.feature.greeting.impl.screens.TokensScreen
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
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun tokens_screenshot() {
    composeTestRule.setContent {
      MinimalTheme(cssVars = ProductionPalettes.GeistDark) {
        // The tokens tab is intentionally blank now (its content was cleared
        // from the home page), so this captures an empty surface.
        TokensScreen()
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/tokens.png")
  }
}
