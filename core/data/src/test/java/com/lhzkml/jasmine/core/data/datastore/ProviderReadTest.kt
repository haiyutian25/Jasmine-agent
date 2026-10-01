package com.lhzkml.jasmine.core.data.datastore

import com.lhzkml.jasmine.core.data.model.ModelConfig
import com.lhzkml.jasmine.core.data.model.ProviderApiType
import com.lhzkml.jasmine.core.data.model.ProviderConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 供应商列表**读路径**的判定（G6，D1/G1 的不变量）。
 *
 * 这段判断的用户可见后果很具体：磁盘上存着一份解不出来的 JSON 时，界面会显示出厂清单 ——
 * 那时**必须**说一声（`readFailures` 让 ProviderViewModel 弹提示）。不说的话，用户会以为
 * 自己那份配置（含 API key）丢了，然后照着出厂清单重填一遍，把还有救的原文盖掉。
 *
 * 之所以测这个纯函数而不是 `ProviderDataStore`：真身要 `Context` 与真 DataStore（Robolectric
 * 起得来但重），而"没存过 / 存过能解 / 存过解不出"这三岔路口的判定不依赖它们。
 */
class ProviderReadTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 没存过：给出厂种子，**不上报** —— 首次启动不是坏数据。 */
    @Test
    fun `a missing entry reads as the seed without reporting anything`() {
        listOf(null, "", "   ").forEach { raw ->
            var asked = false
            val read = readStoredProviders(raw, json) {
                asked = true
                SEED
            }

            assertEquals("raw=${raw?.length} 时应当给出厂种子", SEED, read.providers)
            assertNull("首次启动不该报「读失败」", read.failure)
            assertTrue("出厂种子只在需要时才问（它来自 Rust 那边）", asked)
        }
    }

    /** 存过且能解码：用用户自己那份，**不回落到种子**。 */
    @Test
    fun `a readable entry is used as it is`() {
        val saved = listOf(
            ProviderConfig(
                id = "mine",
                name = "我的供应商",
                baseUrl = "https://example.com/v1",
                apiKey = "sk-mine",
                apiType = ProviderApiType.RESPONSES,
                models = listOf(ModelConfig(id = "m1", modelId = "my-model")),
            )
        )

        val read = readStoredProviders(json.encodeToString(saved), json) { SEED }

        assertEquals(saved, read.providers)
        assertNull(read.failure)
    }

    /** 存过但解不出来：给出厂种子让界面有东西显示，同时**把原因交出去**。 */
    @Test
    fun `an entry that cannot be decoded reports the reason and still shows the seed`() {
        val read = readStoredProviders("""[{"id":"mine","name":}""", json) { SEED }

        assertEquals("界面还是要有东西显示", SEED, read.providers)
        assertTrue(
            "读不出来必须说一声，实际 failure=${read.failure}",
            !read.failure.isNullOrBlank(),
        )
    }

    /** 存的是一份**合法** JSON、但不是供应商列表（老版本换过形状）时，同样算"读不出来"。 */
    @Test
    fun `a valid json of the wrong shape counts as undecodable`() {
        val read = readStoredProviders("""{"providers":[]}""", json) { SEED }

        assertEquals(SEED, read.providers)
        assertTrue("形状不对也要上报，实际 failure=${read.failure}", !read.failure.isNullOrBlank())
    }

    private companion object {
        val SEED = listOf(
            ProviderConfig(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                apiKey = "",
                apiType = ProviderApiType.CHAT_COMPLETIONS,
                isBuiltIn = true,
            )
        )
    }
}
