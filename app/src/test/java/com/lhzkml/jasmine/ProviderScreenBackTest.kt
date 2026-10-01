package com.lhzkml.jasmine

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.feature.provider.impl.ProviderAction
import com.lhzkml.jasmine.feature.provider.impl.ProviderEditorState
import com.lhzkml.jasmine.feature.provider.impl.ProviderScreen
import com.lhzkml.jasmine.feature.provider.impl.ProviderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 供应商页的**系统返回键**行为（B1 那个真 bug 的界面侧回归）。
 *
 * 当时的 bug：规则只写在顶栏返回里，系统返回键直接走导航 —— "编辑中按返回"整页退出、草稿全丢。
 * 修法是把规则放进状态（[ProviderState.canNavigateBack]），顶栏与系统返回共用它。这里钉的是
 * **系统返回**这一侧：[ProviderScreen] 在编辑态要把返回拦下来、换成"取消"，列表态则不拦。
 *
 * （`MainScreen` 抽屉那条返回规则同理，但它要真 Hilt（`MainNavHost`/`MainScreen` 里
 * `hiltViewModel()` 会去要 Hilt 的 ViewModel 工厂），本批没做，记在 `F_G_FIX_PLAN.md` 里。）
 */
@RunWith(RobolectricTestRunner::class)
@Config
class ProviderScreenBackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** 编辑态：系统返回该关掉表单（发 CancelClicked），而不是退出页面。 */
    @Test
    fun `back closes the editor instead of leaving the page`() {
        val actions = mutableListOf<ProviderAction>()
        composeTestRule.setContent {
            ProviderScreen(
                state = ProviderState(editor = openDraft()),
                onAction = { actions += it },
                currentTheme = ProductionPalettes.GeistDark,
            )
        }

        pressBack()

        assertEquals(
            "编辑态的返回键要变成「取消编辑」，实际动作=$actions",
            listOf<ProviderAction>(ProviderAction.CancelClicked),
            actions,
        )
    }

    /** 列表态：没有编辑中的东西，返回不拦（交给导航退出页面）。 */
    @Test
    fun `back is not intercepted while the list is showing`() {
        val actions = mutableListOf<ProviderAction>()
        composeTestRule.setContent {
            ProviderScreen(
                state = ProviderState(),
                onAction = { actions += it },
                currentTheme = ProductionPalettes.GeistDark,
            )
        }

        pressBack()

        assertTrue("列表态不该拦返回，实际动作=$actions", actions.isEmpty())
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun openDraft() = ProviderEditorState(
        id = null,
        name = "Acme",
        baseUrl = "https://api.acme.test",
        apiKey = "sk-acme",
        apiType = ProviderApiType.CHAT_COMPLETIONS,
        isBuiltIn = false,
        models = emptyList(),
        modelSheet = null,
        modelEditor = null,
    )
}
