package com.lhzkml.jasmine

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.lhzkml.jasmine.core.data.model.ChatRole
import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.ui.theme.JasmineTheme
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.feature.main.impl.chat.ChatMessage
import com.lhzkml.jasmine.feature.main.impl.chat.ChatScreen
import com.lhzkml.jasmine.feature.main.impl.chat.ChatState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the home chat surface with a representative transcript.
 *
 * Roborazzi only compares against the committed golden when a mode is selected
 * (`-Proborazzi.test.record=true` to regenerate, `-Proborazzi.test.verify=true`
 * in CI); a plain `testDebugUnitTest` run renders but does not assert.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8)
class MainScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun chat_screenshot() {
    composeTestRule.setContent {
      JasmineTheme(cssVars = ProductionPalettes.GeistDark) {
        ChatScreen(
          state = ChatState(
            messages = listOf(
              ChatMessage(id = "1", role = ChatRole.USER, text = "What is Jasmine?"),
              ChatMessage(
                id = "2",
                role = ChatRole.ASSISTANT,
                text = "A multi-module Compose app driven by live CSS-variable design tokens.",
              ),
            ),
            providers = listOf(PROVIDER),
            activeProviderId = PROVIDER.id,
            activeModelId = MODEL_ID,
          ),
          onAction = {},
          currentTheme = ProductionPalettes.GeistDark,
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/chat.png")
  }

  private companion object {
    const val MODEL_ID = "model-1"

    val PROVIDER = ProviderConfig(
      id = "deepseek",
      name = "DeepSeek",
      baseUrl = "https://api.deepseek.com",
      apiKey = "sk-test",
      apiType = ProviderApiType.CHAT_COMPLETIONS,
      models = listOf(ModelConfig(id = MODEL_ID, modelId = "deepseek-chat")),
    )
  }
}
