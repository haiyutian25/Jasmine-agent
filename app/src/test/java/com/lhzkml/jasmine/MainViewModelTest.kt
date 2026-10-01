package com.lhzkml.jasmine

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.lhzkml.jasmine.core.data.datasource.FontRemoteDataSource
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.model.AgentOutputLanguage
import com.lhzkml.jasmine.core.data.model.ColorMode
import com.lhzkml.jasmine.core.data.model.PresetFont
import com.lhzkml.jasmine.core.data.model.UserPreferences
import com.lhzkml.jasmine.core.data.repository.CustomFontRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.network.FontDownloadApi
import com.lhzkml.jasmine.core.ui.theme.AppTypographyChoice
import com.lhzkml.jasmine.core.ui.theme.ProductionPalettes
import com.lhzkml.jasmine.feature.main.impl.MainAction
import com.lhzkml.jasmine.feature.main.impl.MainEvent
import com.lhzkml.jasmine.feature.main.impl.MainState
import com.lhzkml.jasmine.feature.main.impl.MainViewModel
import com.lhzkml.jasmine.feature.main.impl.R as FeatureR
import com.lhzkml.jasmine.feature.main.impl.fonts.CustomFontFamilyCache
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MainViewModel` 的行为（G6）。
 *
 * 整类此前**零测试**，而它持有全应用最要紧的一条不变量：**乐观写的身份守卫回滚** ——
 * 界面上先动、落盘在后台，落盘失败时把状态回退到"那条命令写下的值"，但**只回退它写下的那一份**：
 * 用户在这期间又选了别的，迟到的失败不许把新选择抹掉（P0 §3.4）。
 *
 * 跑在 Robolectric 下只为一件事：`MainViewModel` 需要真的 `Context`（读系统深色模式），
 * 而字体仓库要 `filesDir`。字体仓库本身用**真的**（配假的下载接口），所以"删字体 / 下载失败"
 * 这两条走的是真实路径，不是替身。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var downloadApi: FakeFontDownloadApi

    /** 造出来的实例：每个用例结束前要取消它的作用域（收集器都活在里面）。 */
    private val created = mutableListOf<MainViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = FakeUserPreferencesRepository(UserPreferences.DEFAULT)
        downloadApi = FakeFontDownloadApi()
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        Dispatchers.resetMain()
    }

    // ── 偏好 ↔ 状态 ────────────────────────────────────────────────────

    /**
     * 偏好流回灌进状态；**侧栏不在其中**（它是会话瞬态位置，进程重启后必须从头开始，
     * 见 `UserPreferences` 与 `MainState` 的注释）。
     */
    @Test
    fun `preferences fill the shell state but never restore the sidebar`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 先把侧栏打开，再让偏好回灌一次 —— 它不该被带回关闭状态之外的东西。
        viewModel.trySendAction(MainAction.SidebarOpened)
        preferences.emit(
            UserPreferences.DEFAULT.copy(
                themeId = "geist-dark",
                colorMode = ColorMode.DARK.id,
                typographyChoice = AppTypographyChoice.MONO.name,
                fontScale = 1.25f,
                activeCustomFontId = "upload_1.ttf",
                agentOutputLanguage = AgentOutputLanguage.ENGLISH,
            )
        )
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("geist-dark", state.themeId)
        assertEquals(ColorMode.DARK, state.colorMode)
        assertEquals(AppTypographyChoice.MONO, state.typographyChoice)
        assertEquals(1.25f, state.fontScale, 0.0001f)
        assertEquals("upload_1.ttf", state.activeCustomFontId)
        assertEquals(AgentOutputLanguage.ENGLISH, state.agentOutputLanguage)
        assertTrue("侧栏是会话瞬态位置，偏好回灌不该动它", state.isSidebarOpen)
    }

    /** 侧栏三个动作只改状态，**不进偏好**（没有对应写口）。 */
    @Test
    fun `the sidebar toggles without touching preferences`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.SidebarToggled)
        assertTrue(viewModel.stateFlow.value.isSidebarOpen)
        viewModel.trySendAction(MainAction.SidebarToggled)
        assertEquals(false, viewModel.stateFlow.value.isSidebarOpen)
        viewModel.trySendAction(MainAction.SidebarOpened)
        viewModel.trySendAction(MainAction.SidebarClosed)

        assertEquals("侧栏不是偏好，一条写口都不该碰", emptyList<String>(), preferences.writes)
    }

    // ── 乐观写的身份守卫回滚（P0 §3.4） ────────────────────────────────

    /**
     * 六条乐观写，失败后都回到**它替换掉的那个值**。
     *
     * 每条用一个新建的实例，免得互相串味。
     */
    @Test
    fun `every rejected write rolls back to the value it replaced`() = runTest(dispatcher) {
        val cases = listOf(
            RollbackCase(
                name = "主题",
                optimistic = MainAction.ThemeSelected(ProductionPalettes.GeistDark),
                rejected = MainAction.Internal.ThemePersistRejected("geist-dark", "editorial-light"),
                read = MainState::themeId,
                expected = "editorial-light",
            ),
            RollbackCase(
                name = "明暗模式",
                optimistic = MainAction.ColorModeChanged(ColorMode.DARK),
                rejected = MainAction.Internal.ColorModePersistRejected(ColorMode.DARK, ColorMode.SYSTEM),
                read = { it.colorMode.id },
                expected = ColorMode.SYSTEM.id,
            ),
            RollbackCase(
                name = "排版引擎",
                optimistic = MainAction.TypographySelected(AppTypographyChoice.MONO),
                rejected = MainAction.Internal.TypographyPersistRejected(
                    AppTypographyChoice.MONO,
                    AppTypographyChoice.EDITORIAL,
                    "upload_old.ttf",
                ),
                read = { it.typographyChoice.name + "|" + it.activeCustomFontId },
                expected = AppTypographyChoice.EDITORIAL.name + "|upload_old.ttf",
            ),
            RollbackCase(
                name = "自定义字体",
                optimistic = MainAction.CustomFontSelected("upload_new.ttf"),
                rejected = MainAction.Internal.CustomFontPersistRejected("upload_new.ttf", "upload_old.ttf"),
                read = MainState::activeCustomFontId,
                expected = "upload_old.ttf",
            ),
            RollbackCase(
                name = "字号",
                optimistic = MainAction.FontScaleSaved(1.5f),
                rejected = MainAction.Internal.FontScalePersistRejected(1.5f, 1.0f),
                read = MainState::fontScale,
                expected = 1.0f,
            ),
            RollbackCase(
                name = "回复语言",
                optimistic = MainAction.AgentOutputLanguageSelected(AgentOutputLanguage.ENGLISH),
                rejected = MainAction.Internal.AgentOutputLanguagePersistRejected(
                    AgentOutputLanguage.ENGLISH,
                    AgentOutputLanguage.FOLLOW_INPUT,
                ),
                read = MainState::agentOutputLanguage,
                expected = AgentOutputLanguage.FOLLOW_INPUT,
            ),
        )

        cases.forEach { case ->
            val viewModel = createViewModel()
            advanceUntilIdle()
            val events = eventsOf(viewModel)

            viewModel.trySendAction(case.optimistic)
            advanceUntilIdle()
            assertTrue("${case.name}：先要乐观地改掉界面", case.read(viewModel.stateFlow.value) != case.expected)

            viewModel.trySendAction(case.rejected)
            advanceUntilIdle()

            assertEquals("${case.name}：落盘失败要回退到被替换掉的值", case.expected, case.read(viewModel.stateFlow.value))
            assertTrue(
                "${case.name}：失败要说一声，实际事件=$events",
                events.any { it is MainEvent.ShowToast && it.messageRes == FeatureR.string.setting_save_failed_toast },
            )
        }
    }

    /**
     * **迟到的失败不许把新选择抹掉**。
     *
     * 连点两次主题：第一次的落盘失败晚于第二次的选择到达 —— 那时状态里已经是第二个值，
     * 与"第一次写下的乐观值"不等，守卫就该放它过去（回退会退回到更早的值，等于吞掉用户的新选择）。
     */
    @Test
    fun `a late rejection does not undo a newer choice`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.ThemeSelected(ProductionPalettes.GeistDark))
        advanceUntilIdle()
        // 第二次选择（用户改主意）：界面现在是 notion。
        viewModel.trySendAction(MainAction.ThemeSelected(ProductionPalettes.NotionWarmLight))
        advanceUntilIdle()

        // 第一次那条命令的失败**现在**才回来。
        viewModel.trySendAction(
            MainAction.Internal.ThemePersistRejected("geist-dark", "editorial-light")
        )
        advanceUntilIdle()

        assertEquals(
            "迟到的失败不该把用户后来选的主题顶掉",
            ProductionPalettes.NotionWarmLight.themeId,
            viewModel.stateFlow.value.themeId,
        )
    }

    /**
     * 排版引擎那条命令守的是**两个字段**：它同时清掉了自定义字体。
     *
     * 用户随后选了自定义字体（`activeCustomFontId` 不再是空），说明这条命令写下的那份状态已经被
     * 后来的选择接管 —— 回滚它会顺手把用户刚选的自定义字体清掉，所以必须整体放行。
     */
    @Test
    fun `a rejected typography write does not undo a custom font picked after it`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.TypographySelected(AppTypographyChoice.MONO))
        advanceUntilIdle()
        viewModel.trySendAction(MainAction.CustomFontSelected("upload_picked.ttf"))
        advanceUntilIdle()

        viewModel.trySendAction(
            MainAction.Internal.TypographyPersistRejected(
                AppTypographyChoice.MONO,
                AppTypographyChoice.EDITORIAL,
                "upload_old.ttf",
            )
        )
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals("后选的自定义字体不能被回滚清掉", "upload_picked.ttf", state.activeCustomFontId)
        assertEquals("排版引擎也不该被回退", AppTypographyChoice.MONO, state.typographyChoice)
    }

    // ── 字体 ───────────────────────────────────────────────────────────

    /** 删当前激活的字体：选择**立刻**清空（不能等到列表回来才让界面指向一个不存在的文件），并说一声。 */
    @Test
    fun `deleting the active font clears the selection at once`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.trySendAction(MainAction.CustomFontSelected("upload_1.ttf"))
        advanceUntilIdle()
        val events = eventsOf(viewModel)

        viewModel.trySendAction(MainAction.FontDeleteClicked("upload_1.ttf"))
        advanceUntilIdle()

        assertEquals("", viewModel.stateFlow.value.activeCustomFontId)
        assertTrue(
            "删掉的是当前字体，但回退值不是它 —— 这里只要求清空与提示，实际事件=$events",
            events.any { it is MainEvent.ShowToast && it.messageRes == FeatureR.string.font_deleted_toast },
        )
    }

    /** 删别的字体不动选择。 */
    @Test
    fun `deleting another font leaves the selection alone`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.trySendAction(MainAction.CustomFontSelected("upload_1.ttf"))
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.FontDeleteClicked("upload_2.ttf"))
        advanceUntilIdle()

        assertEquals("upload_1.ttf", viewModel.stateFlow.value.activeCustomFontId)
    }

    /** 删除失败：列表不回灌（本来就没变），但要说出来。 */
    @Test
    fun `a failed delete only says so`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val events = eventsOf(viewModel)

        viewModel.trySendAction(MainAction.Internal.FontDeleteCompleted(success = false))
        advanceUntilIdle()

        assertEquals(
            "删除失败要让用户知道，实际事件=$events",
            listOf(FeatureR.string.font_delete_failed_toast),
            events.filterIsInstance<MainEvent.ShowToast>().map { it.messageRes },
        )
    }

    /** 下载失败：走真实仓库与真实的校验路径，失败要以 toast 说出来（不是静默）。 */
    @Test
    fun `a failed download says so`() = runTest(dispatcher) {
        downloadApi.failure = IOException("连不上")
        val viewModel = createViewModel()
        advanceUntilIdle()
        val events = eventsOf(viewModel)

        viewModel.trySendAction(MainAction.FontDownloadClicked(FIRST_PRESET))
        advanceUntilIdle()

        assertEquals(
            "下载失败要提示，实际事件=$events",
            listOf(FeatureR.string.font_download_failed_toast),
            events.filterIsInstance<MainEvent.ShowToast>().map { it.messageRes },
        )
    }

    /** 下载成功与导入完成各有各的说法；导入成功还顺手把新字体设成当前。 */
    @Test
    fun `download and import completions each report their own toast`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val events = eventsOf(viewModel)

        viewModel.trySendAction(MainAction.Internal.FontDownloadCompleted(success = true))
        viewModel.trySendAction(MainAction.Internal.FontImportCompleted(fontId = null))
        viewModel.trySendAction(MainAction.Internal.FontImportCompleted(fontId = "upload_imported.ttf"))
        advanceUntilIdle()

        assertEquals(
            "三条结果各一条提示，实际事件=$events",
            listOf(
                FeatureR.string.font_download_complete_toast,
                FeatureR.string.font_import_failed_toast,
                FeatureR.string.font_imported_toast,
            ),
            events.filterIsInstance<MainEvent.ShowToast>().map { it.messageRes },
        )
        assertEquals("导入成功后它就是当前字体", "upload_imported.ttf", viewModel.stateFlow.value.activeCustomFontId)
    }

    /** 字号保存：状态立刻跟上并提示（落盘在后台）。 */
    @Test
    fun `saving the font scale applies it and says so`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val events = eventsOf(viewModel)

        viewModel.trySendAction(MainAction.FontScaleSaved(1.3f))
        advanceUntilIdle()

        assertEquals(1.3f, viewModel.stateFlow.value.fontScale, 0.0001f)
        assertTrue(
            "保存字号要给回执，实际事件=$events",
            events.any { it is MainEvent.ShowToast && it.messageRes == FeatureR.string.font_size_saved_toast },
        )
    }

    // ── 派生字段 ───────────────────────────────────────────────────────

    /**
     * 主题是**派生**的：只看 themeId + 明暗模式（+ 系统深色），三次更新都跟着重算。
     *
     * 这里钉的是最容易错的一条：`SYSTEM` 模式下系统深色翻转要换到深色变体，
     * 而显式 `LIGHT` 模式下系统翻转**不许**影响它。
     */
    @Test
    fun `the theme follows the raw inputs`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.ColorModeChanged(ColorMode.LIGHT))
        viewModel.trySendAction(MainAction.ThemeSelected(ProductionPalettes.GeistDark))
        advanceUntilIdle()
        assertEquals(
            "显式浅色模式下选深色家族，取的是浅色变体",
            ProductionPalettes.GeistLight,
            viewModel.stateFlow.value.theme,
        )

        // 系统深色翻转：显式浅色不该受影响。
        viewModel.trySendAction(MainAction.SystemDarkModeChanged(true))
        advanceUntilIdle()
        assertEquals(ProductionPalettes.GeistLight, viewModel.stateFlow.value.theme)

        // 切到跟随系统：同一份 themeId 立刻变深色。
        viewModel.trySendAction(MainAction.ColorModeChanged(ColorMode.SYSTEM))
        advanceUntilIdle()
        assertEquals(ProductionPalettes.GeistDark, viewModel.stateFlow.value.theme)
    }

    /** 没有自定义字体时，正文字体回落排版引擎自己的字体。 */
    @Test
    fun `the content font falls back to the typography engine`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.trySendAction(MainAction.TypographySelected(AppTypographyChoice.MONO))
        advanceUntilIdle()

        val state = viewModel.stateFlow.value
        assertEquals(AppTypographyChoice.MONO.font, state.activeContentFont)
        assertEquals("没装字体时预览表是空的", emptyMap<String, Any>(), state.fontPreviews)
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

    private fun createViewModel(): MainViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fontRepository = CustomFontRepository(
            context = context,
            dispatcherManager = TestDispatchers(dispatcher),
            fontRemoteDataSource = FontRemoteDataSource(
                fontDownloadApi = downloadApi,
                dispatcherManager = TestDispatchers(dispatcher),
            ),
        )
        return MainViewModel(
            appContext = context,
            userPreferencesRepository = preferences,
            customFontRepository = fontRepository,
            customFontFamilyCache = CustomFontFamilyCache(fontRepository),
        ).also { created += it }
    }

    private fun TestScope.eventsOf(viewModel: MainViewModel): MutableList<MainEvent> {
        val events = mutableListOf<MainEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { events += it } }
        return events
    }

    private data class RollbackCase(
        val name: String,
        val optimistic: MainAction,
        val rejected: MainAction.Internal,
        val read: (MainState) -> Any?,
        val expected: Any?,
    )

    private companion object {
        val FIRST_PRESET: PresetFont =
            com.lhzkml.jasmine.core.data.model.PresetFontCatalog.ALL.first()
    }
}

/** 数据层的调度器换成测试调度器：仓库里的 `withContext(io)` 因此受虚拟时钟管辖。 */
private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherManager {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
}

private class FakeUserPreferencesRepository(initial: UserPreferences) : UserPreferencesRepository {
    override val preferencesStateFlow: MutableStateFlow<UserPreferences> = MutableStateFlow(initial)

    /** 记下每一次写口（用来断言"侧栏没碰偏好"这类事）。 */
    val writes = mutableListOf<String>()

    fun emit(preferences: UserPreferences) {
        preferencesStateFlow.value = preferences
    }

    override suspend fun updateTheme(themeId: String) {
        writes += "theme"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(themeId = themeId)
    }

    override suspend fun updateTypography(typographyChoice: String) {
        writes += "typography"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(typographyChoice = typographyChoice)
    }

    override suspend fun updateColorMode(colorMode: String) {
        writes += "colorMode"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(colorMode = colorMode)
    }

    override suspend fun updateFontScale(fontScale: Float) {
        writes += "fontScale"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(fontScale = fontScale)
    }

    override suspend fun updateActiveCustomFont(fontId: String) {
        writes += "customFont"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(activeCustomFontId = fontId)
    }

    override suspend fun updateActiveModel(providerId: String, modelId: String) {
        writes += "activeModel"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(
            activeProviderId = providerId,
            activeModelId = modelId,
        )
    }

    override suspend fun updateAgentOutputLanguage(value: String) {
        writes += "agentOutputLanguage"
        preferencesStateFlow.value = preferencesStateFlow.value.copy(agentOutputLanguage = value)
    }
}

private class FakeFontDownloadApi : FontDownloadApi {
    /** 非空就让下载抛这个异常 —— 真实路径上的"连不上"。 */
    var failure: Exception? = null

    override suspend fun download(url: String): ResponseBody {
        throw failure ?: IOException("测试里不真的下载：$url")
    }
}
