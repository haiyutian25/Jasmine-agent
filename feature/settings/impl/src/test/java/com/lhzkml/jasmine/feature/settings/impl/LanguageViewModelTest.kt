package com.lhzkml.jasmine.feature.settings.impl

import com.lhzkml.jasmine.core.data.repository.AppLanguage
import com.lhzkml.jasmine.core.data.repository.AppLanguageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 语言页的 UDF 行为：选择经 action 落地到仓库（平台调用只在 data 层），
 * 组合配置换掉时状态回到平台的权威值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeAppLanguageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAppLanguageRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the current value lands through the action channel`() = runTest(testDispatcher) {
        repository.current = AppLanguage.ENGLISH
        val viewModel = LanguageViewModel(repository)
        advanceUntilIdle()

        assertEquals(AppLanguage.ENGLISH, viewModel.stateFlow.value.selected)
    }

    @Test
    fun `selecting an option applies it and moves the checkmark`() = runTest(testDispatcher) {
        val viewModel = LanguageViewModel(repository)
        advanceUntilIdle()

        viewModel.trySendAction(LanguageAction.Selected(AppLanguage.CHINESE))

        // 乐观落笔（即使平台不重建，勾也要跟手），并真的写进平台。
        assertEquals(AppLanguage.CHINESE, viewModel.stateFlow.value.selected)
        assertEquals(AppLanguage.CHINESE, repository.applied)
    }

    @Test
    fun `a settled configuration returns to the platform value`() = runTest(testDispatcher) {
        val viewModel = LanguageViewModel(repository)
        advanceUntilIdle()
        viewModel.trySendAction(LanguageAction.Selected(AppLanguage.ENGLISH))

        // 平台侧"跟随系统"（例如用户改回系统语言）：配置换掉后状态回到权威值。
        repository.current = AppLanguage.SYSTEM
        viewModel.trySendAction(LanguageAction.SystemLocaleSettled)

        assertEquals(AppLanguage.SYSTEM, viewModel.stateFlow.value.selected)
    }
}

private class FakeAppLanguageRepository : AppLanguageRepository {
    var current: AppLanguage = AppLanguage.SYSTEM
    var applied: AppLanguage? = null

    override fun current(): AppLanguage = current
    override fun apply(language: AppLanguage) {
        applied = language
    }
}
