package com.lhzkml.jasmine.feature.provider.impl

import com.lhzkml.jasmine.core.agent.ProbeResult
import com.lhzkml.jasmine.core.agent.ProviderProbe
import com.lhzkml.jasmine.core.data.model.CatalogModel
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 供应商页的失败回滚（P0 修复方案 D4）。
 *
 * 乐观写 + 失败回滚是这次新加的一整套契约：先写状态、再落盘；落盘失败必须把界面**纠正回**
 * 真实状态（而不是像以前那样只弹一个 toast，让界面永远停在没落盘的假象上）。这里的三个用例
 * 分别覆盖：删除回滚、保存回滚、以及两者共用的**身份守卫**（迟到的失败不能盖掉用户的新动作）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelRollbackTest {

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

    private fun viewModel(): ProviderViewModel =
        ProviderViewModel(repository, FakeProviderProbe)

    @Test
    fun `a failed delete puts the provider back where it was`() = runTest(testDispatcher) {
        repository.providers.value = listOf(FIRST, SECOND)
        repository.deleteFailure = IllegalStateException("disk on fire")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.DeleteClicked(SECOND.id))
        advanceUntilIdle()

        // 乐观移除被回滚：两家都在，且顺序没变（按原位插回，不是追加到末尾）。
        assertEquals(listOf(FIRST, SECOND), viewModel.stateFlow.value.providers)
        assertTrue(viewModel.stateFlow.value.editor == null)
    }

    @Test
    fun `a failed save restores the list and reopens the draft`() = runTest(testDispatcher) {
        repository.providers.value = listOf(FIRST)
        repository.upsertFailure = IllegalStateException("disk on fire")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.AddClicked)
        viewModel.trySendAction(ProviderAction.NameChanged("Acme"))
        viewModel.trySendAction(ProviderAction.BaseUrlChanged("https://api.acme.test"))
        viewModel.trySendAction(ProviderAction.ApiKeyChanged("sk-acme"))
        viewModel.trySendAction(ProviderAction.SaveClicked)
        advanceUntilIdle()

        // 列表回到落盘前那份；没存上的草稿重新打开（用户填的东西不能丢）。
        assertEquals(listOf(FIRST), viewModel.stateFlow.value.providers)
        val editor = viewModel.stateFlow.value.editor
        assertNotNull("保存失败后草稿必须重新打开", editor)
        assertEquals("Acme", editor?.name)
        assertEquals("https://api.acme.test", editor?.baseUrl)
        assertEquals("sk-acme", editor?.apiKey)
    }

    @Test
    fun `a late save failure does not clobber a newer draft`() = runTest(testDispatcher) {
        repository.providers.value = listOf(FIRST)
        // 让落盘挂住：失败回流得以"迟到"，期间用户可以开始新的编辑。
        val gate = CompletableDeferred<Unit>()
        repository.upsertGate = gate
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.trySendAction(ProviderAction.AddClicked)
        viewModel.trySendAction(ProviderAction.NameChanged("Acme"))
        viewModel.trySendAction(ProviderAction.BaseUrlChanged("https://api.acme.test"))
        viewModel.trySendAction(ProviderAction.ApiKeyChanged("sk-acme"))
        viewModel.trySendAction(ProviderAction.SaveClicked)

        // 落盘还挂在 gate 上：用户已经转身去编辑另一家了。
        viewModel.trySendAction(ProviderAction.AddClicked)
        viewModel.trySendAction(ProviderAction.NameChanged("Beta"))

        repository.upsertFailure = IllegalStateException("disk on fire")
        gate.complete(Unit)
        advanceUntilIdle()

        // 身份守卫：已经有新草稿在编辑，迟到的失败不得把它顶掉。
        assertEquals("Beta", viewModel.stateFlow.value.editor?.name)
        assertEquals(listOf(FIRST), viewModel.stateFlow.value.providers)
    }

    private companion object {
        val FIRST = ProviderConfig(
            id = "first",
            name = "First",
            baseUrl = "https://first.test",
            apiKey = "sk-first",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            isBuiltIn = false,
            models = emptyList(),
        )

        val SECOND = ProviderConfig(
            id = "second",
            name = "Second",
            baseUrl = "https://second.test",
            apiKey = "sk-second",
            apiType = ProviderApiType.CHAT_COMPLETIONS,
            isBuiltIn = false,
            models = emptyList(),
        )
    }
}

/** 只做该用例关心的事：发一份列表、按需让落盘失败或挂起。 */
internal class FakeProviderRepository : ProviderRepository {
    /** 测试直接往里塞初始列表；成功落盘也写这里（回灌同真实仓库）。 */
    val providers = MutableStateFlow<List<ProviderConfig>>(emptyList())

    /** 用例里没有读失败。 */
    override val readFailures: Flow<String> = emptyFlow()

    var deleteFailure: Exception? = null
    var upsertFailure: Exception? = null
    var upsertGate: CompletableDeferred<Unit>? = null

    override val providersStateFlow: StateFlow<List<ProviderConfig>> = providers.asStateFlow()

    override suspend fun upsertProvider(provider: ProviderConfig) {
        upsertGate?.await()
        upsertFailure?.let { throw it }
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun deleteProvider(id: String) {
        deleteFailure?.let { throw it }
        providers.value = providers.value.filterNot { it.id == id }
    }

    override suspend fun fetchModels(provider: ProviderConfig): List<String> = emptyList()

    override suspend fun catalog(providerId: String): List<CatalogModel> = emptyList()
}

internal object FakeProviderProbe : ProviderProbe {
    override suspend fun probe(provider: ProviderConfig, modelId: String): ProbeResult =
        ProbeResult.Failure("not used")
}
