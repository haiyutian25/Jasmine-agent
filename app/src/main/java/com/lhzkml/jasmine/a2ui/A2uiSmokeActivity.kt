package com.lhzkml.jasmine.a2ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.processor.processInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "A2uiSmoke"

/**
 * 页面上那句 `surfaceId` 必须和这里的两条 JSON 对上。
 *
 * 用 `su -c "am start -n com.lhzkml.jasmine/.a2ui.A2uiSmokeActivity"` 拉起（没有导出，
 * 所以 shell 身份拉不起来，得走 su）。
 */
class A2uiSmokeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    A2uiSmoke()
                }
            }
        }
    }
}

/**
 * 一段**硬编码**的 A2UI JSON → 原生 Compose 卡片。
 *
 * 只验证"依赖能不能加、渲染器能不能出图"：没有模型、没有传输、不碰聊天链路。
 * 消息格式照 `specification/v0_9_1/json/server_to_client.json`（一条一条喂，不是数组）。
 */
@Composable
private fun A2uiSmoke() {
    val catalog = remember {
        materialA2uiBasicCatalogV1(
            // 基础目录把图片 / 视频 / 音频三件事留给宿主接（它有意不捆 Coil、Media3）。
            // 冒烟页不出这三种内容，给空实现即可。
            image = MaterialA2uiBasicCatalogV1Defaults.image { _, _, _, _, _ -> },
            video = MaterialA2uiBasicCatalogV1Defaults.video { _, _, _ -> },
            audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, _, _ -> },
            urlOpener = { url -> Log.d(TAG, "openUrl: $url") },
            messageFormatter = { pattern, _, _ -> pattern },
            localeProvider = A2uiLocaleProvider.Default,
        )
    }
    val processor = remember(catalog) { A2uiMessageProcessor(catalogs = listOf(catalog)) }

    LaunchedEffect(processor) {
        launch(Dispatchers.Default) { processor.collectMessages() }
        launch { processor.outboundEvents.collect { event -> Log.d(TAG, "outbound: $event") } }
        val parser = A2uiMessageParser()
        SMOKE_MESSAGES.forEach { json -> processor.processInput(parser, json) }
    }

    val surfaces by processor.activeSurfaces.collectAsState()
    LaunchedEffect(surfaces) {
        Log.d(TAG, "activeSurfaces=${surfaces.size} ids=${surfaces.map { it.id }}")
    }

    val surface = surfaces.firstOrNull()
    Box(modifier = Modifier.padding(16.dp)) {
        if (surface == null) {
            Text(text = "等待 surface…（看 logcat 的 $TAG）")
        } else {
            A2uiSurface(surfaceModel = surface, modifier = Modifier.fillMaxSize())
        }
    }
}

/** `createSurface` + `updateComponents`，两条就够渲一张卡片（规范里 `root` 是必须的）。 */
private val SMOKE_MESSAGES = listOf(
    """{"version":"v0.9","createSurface":{"surfaceId":"smoke-card","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"}}""",
    """
    {"version":"v0.9","updateComponents":{"surfaceId":"smoke-card","components":[
      {"id":"root","component":"Card","child":"column"},
      {"id":"column","component":"Column","children":["title","body","divider","action-row"]},
      {"id":"title","component":"Text","text":"A2UI 渲染器冒烟","variant":"h3"},
      {"id":"body","component":"Text","text":"这段界面来自硬编码的 A2UI JSON，经 A2uiSurface 渲染成原生 Compose 组件。","variant":"body"},
      {"id":"divider","component":"Divider"},
      {"id":"action-row","component":"Row","children":["btn"],"justify":"start"},
      {"id":"btn-text","component":"Text","text":"点我会回一个 action"},
      {"id":"btn","component":"Button","child":"btn-text","variant":"primary",
       "action":{"event":{"name":"smoke_clicked","context":{"from":"a2ui-smoke"}}}}
    ]}}
    """.trimIndent(),
)
