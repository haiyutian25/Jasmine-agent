package com.lhzkml.jasmine.feature.provider.impl

import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "编辑态下返回先关编辑器" 这条规则（B1 修的那个真 bug 的回归）。
 *
 * 规则本身写在状态里：[ProviderState.canNavigateBack]。以前它只写在导航栏的 `onBack` 里，
 * **系统返回键不走它** —— 于是"编辑中按系统返回"会整页退出、草稿全丢。现在顶栏返回与系统返回
 * 共用这一个判断，所以这条规则必须对所有编辑入口都成立：
 *
 * - 新增（`AddClicked`）/ 编辑（`EditClicked`）打开的供应商表单；
 * - 表单里的模型目录面板（`FetchModelsClicked` → `ModelSheetState`）；
 * - 模型参数面板（`CustomModelClicked` → `ModelEditorState`）。
 *
 * 而且**关要一层一层关**：关掉最上面的模型参数面板，供应商表单还开着，页面仍然不让走。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderBackRuleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeProviderRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeProviderRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ProviderViewModel = ProviderViewModel(repository, FakeProviderProbe)

    /** 列表态：没有任何编辑中的东西，返回键该离开页面。 */
    @Test
    fun `the list is navigable`() = runTest(testDispatcher) {
        repository.providers.value = listOf(DEEPSEEK)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue("列表态返回该走导航", viewModel.stateFlow.value.canNavigateBack)
    }

    /** 三个编辑入口各自都会把页面"锁住"（返回改成关编辑器）。 */
    @Test
    fun `every way into editing makes back close the editor first`() = runTest(testDispatcher) {
        repository.providers.value = listOf(DEEPSEEK)

        // 新增
        val add = viewModel()
        advanceUntilIdle()
        add.trySendAction(ProviderAction.AddClicked)
        advanceUntilIdle()
        assertNotNull(add.stateFlow.value.editor)
        assertFalse("新增表单打开时不许返回离开页面", add.stateFlow.value.canNavigateBack)

        // 编辑已有供应商
        val edit = viewModel()
        advanceUntilIdle()
        edit.trySendAction(ProviderAction.EditClicked(DEEPSEEK.id))
        advanceUntilIdle()
        assertNotNull(edit.stateFlow.value.editor)
        assertFalse("编辑表单打开时不许返回离开页面", edit.stateFlow.value.canNavigateBack)

        // 表单里的模型目录面板（要先把地址与密钥填上，否则"获取模型列表"直接被挡回）
        val sheet = viewModel()
        advanceUntilIdle()
        sheet.trySendAction(ProviderAction.AddClicked)
        sheet.trySendAction(ProviderAction.BaseUrlChanged("https://api.acme.test"))
        sheet.trySendAction(ProviderAction.ApiKeyChanged("sk-acme"))
        sheet.trySendAction(ProviderAction.FetchModelsClicked)
        advanceUntilIdle()
        assertNotNull("模型目录面板该打开", sheet.stateFlow.value.editor?.modelSheet)
        assertFalse(sheet.stateFlow.value.canNavigateBack)

        // 表单里的模型参数面板
        val modelEditor = viewModel()
        advanceUntilIdle()
        modelEditor.trySendAction(ProviderAction.AddClicked)
        modelEditor.trySendAction(ProviderAction.CustomModelClicked)
        advanceUntilIdle()
        assertNotNull("模型参数面板该打开", modelEditor.stateFlow.value.editor?.modelEditor)
        assertFalse(modelEditor.stateFlow.value.canNavigateBack)
    }

    /** 一层一层关：关掉模型参数面板，供应商表单仍开着 → 页面仍然不让返回。 */
    @Test
    fun `closing the top layer keeps the editor rule`() = runTest(testDispatcher) {
        repository.providers.value = listOf(DEEPSEEK)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.AddClicked)
        viewModel.trySendAction(ProviderAction.CustomModelClicked)
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.ModelCancelClicked)
        advanceUntilIdle()

        assertNull("模型面板关了", viewModel.stateFlow.value.editor?.modelEditor)
        assertNotNull("供应商表单还在", viewModel.stateFlow.value.editor)
        assertFalse("最上面那层关了，但表单还开着：返回仍应关表单", viewModel.stateFlow.value.canNavigateBack)
    }

    /** 关掉表单本身（取消）→ 规则复位，返回回到"离开页面"。 */
    @Test
    fun `cancelling the editor hands the page back to navigation`() = runTest(testDispatcher) {
        repository.providers.value = listOf(DEEPSEEK)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.AddClicked)
        advanceUntilIdle()
        assertFalse(viewModel.stateFlow.value.canNavigateBack)

        viewModel.trySendAction(ProviderAction.CancelClicked)
        advanceUntilIdle()

        assertNull(viewModel.stateFlow.value.editor)
        assertTrue("草稿丢掉了（用户显式取消），返回该走导航", viewModel.stateFlow.value.canNavigateBack)
    }

    /** 保存成功同样回列表态（编辑器关掉、规则复位）。 */
    @Test
    fun `saving closes the editor and frees the back gesture`() = runTest(testDispatcher) {
        repository.providers.value = listOf(DEEPSEEK)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.AddClicked)
        viewModel.trySendAction(ProviderAction.NameChanged("Acme"))
        viewModel.trySendAction(ProviderAction.BaseUrlChanged("https://api.acme.test"))
        viewModel.trySendAction(ProviderAction.ApiKeyChanged("sk-acme"))
        viewModel.trySendAction(ProviderAction.SaveClicked)
        advanceUntilIdle()

        assertNull("存上了就不该还开着一个草稿", viewModel.stateFlow.value.editor)
        assertTrue(viewModel.stateFlow.value.canNavigateBack)
    }

    private companion object {
        val DEEPSEEK = ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            isBuiltIn = true,
            models = emptyList(),
        )
    }
}
