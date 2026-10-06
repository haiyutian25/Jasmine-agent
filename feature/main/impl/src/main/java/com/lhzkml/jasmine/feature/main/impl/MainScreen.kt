package com.lhzkml.jasmine.feature.main.impl

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import com.lhzkml.jasmine.core.widgets.dialog.AlertDialog
import com.lhzkml.jasmine.core.widgets.scaffold.Scaffold
import com.lhzkml.jasmine.core.widgets.text.Text
import com.lhzkml.jasmine.feature.main.impl.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.lhzkml.jasmine.core.ui.base.util.EventsEffect
import com.lhzkml.jasmine.core.widgets.navigation.DismissibleDrawerSheet
import com.lhzkml.jasmine.core.widgets.navigation.DismissibleNavigationDrawer
import com.lhzkml.jasmine.core.widgets.navigation.DrawerValue
import com.lhzkml.jasmine.core.widgets.navigation.rememberDrawerState
import com.lhzkml.jasmine.feature.main.impl.chat.ChatAction
import com.lhzkml.jasmine.feature.main.impl.chat.ChatScreen
import com.lhzkml.jasmine.feature.main.impl.chat.ChatUiEvent
import com.lhzkml.jasmine.feature.main.impl.chat.ChatViewModel

/**
 * Main destination: push-canvas sidebar + the chat surface. Stateless renderer of
 * [MainState]: every user intent leaves through [onAction] (wired to
 * [MainViewModel.trySendAction] by the host), keeping the single stateFlow
 * subscription at the activity root.
 *
 * The settings flow is NOT hosted here — it lives on the Navigation 3 back
 * stack as sibling destinations (see [MainNavHost]), so system back,
 * predictive back and process-death restore come from the navigation library.
 * The chat has its own [ChatViewModel], scoped to this navigation entry.
 *
 * There is no bottom navigation bar: the app has a single top-level surface, so a
 * tab bar would only cost vertical space and drag a whole layer of IME/inset
 * choreography along with it (the bar had to step aside for the keyboard, which
 * made a focused composer jump).
 */
@Composable
fun MainScreen(
    state: MainState,
    onAction: (MainAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedBg by animateColorAsState(
        targetValue = state.theme.background,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bg_color"
    )

    // Only the drawer still needs an explicit back intercept; every other back
    // navigation is the NavDisplay back stack's job.
    BackHandler(enabled = state.isSidebarOpen) {
        onAction(MainAction.SidebarClosed)
    }

    // 抽屉是覆盖式的：开着键盘打开它，键盘会压在上面、下面那个输入框还继续吃焦点。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.isSidebarOpen) {
        if (state.isSidebarOpen) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // 侧边栏要展示「新建对话 + 历史对话」，这两份数据都在 ChatViewModel 里。
    //
    // 只订阅用得到的切片：流式回复每秒会改很多次 ChatState，整份订阅会让抽屉跟着
    // 重组 —— 抽屉虽然收着，但它仍在组合树里。映射成一个 data class 后
    // distinctUntilChanged 才按值比较，只在真正变化时重组。
    //
    // 每行底部显示这条对话**最后一条消息的时间**（会话记录里的 updatedAt 就是 session 的
    // lastUpdateTime，每写入一条事件都会推进）。以前这里显示模型名，但列表里的模型名
    // 绝大多数都一样，看不出哪条是新聊的、哪条很久没动。
    val chatViewModel: ChatViewModel = hiltViewModel()

    // 一次性效果（错误提示）：与主屏其他地方同一个做法 —— Toast + 资源文案，原始异常文本只进日志。
    val chatEventContext = LocalContext.current
    EventsEffect(viewModel = chatViewModel) { event ->
        when (event) {
            is ChatUiEvent.ShowToast -> Toast.makeText(
                chatEventContext,
                chatEventContext.getString(event.messageRes),
                Toast.LENGTH_SHORT,
            ).show()

            is ChatUiEvent.ShowError -> {
                if (event.detail.isNotEmpty()) {
                    Log.w(
                        "ChatUiEvent",
                        chatEventContext.getString(event.messageRes) + " :: " + event.detail,
                    )
                }
                Toast.makeText(
                    chatEventContext,
                    chatEventContext.getString(event.messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 抽屉要的那一片由 ChatViewModel 派生（P1-7）：界面只收集，不再自己投影会话列表。
    val sidebar by chatViewModel.sidebarState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        // Push-canvas sidebar drawer; the main scaffold is its pushed content.
        val drawerState = rememberDrawerState(
            if (state.isSidebarOpen) DrawerValue.Open else DrawerValue.Closed
        )
        val drawerScope = rememberCoroutineScope()

        // 宿主状态 -> 抽屉（顶部按钮 / 返回键触发）
        LaunchedEffect(state.isSidebarOpen) {
            if (state.isSidebarOpen && !drawerState.isOpen) {
                drawerState.open()
            } else if (!state.isSidebarOpen && !drawerState.isClosed) {
                drawerState.close()
            }
        }
        // 抽屉 -> 宿主状态（手势开合 / 内容里的关闭动作触发）
        LaunchedEffect(drawerState.currentValue) {
            val isOpen = drawerState.currentValue == DrawerValue.Open
            if (isOpen != state.isSidebarOpen) {
                onAction(if (isOpen) MainAction.SidebarOpened else MainAction.SidebarClosed)
            }
        }

        DismissibleNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DismissibleDrawerSheet(modifier = Modifier.width(295.dp)) {
                    AppSidebarContent(
                        currentTheme = state.theme,
                        onOpenSettings = onOpenSettings,
                        onCloseDrawer = { drawerScope.launch { drawerState.close() } },
                        modifier = Modifier.fillMaxWidth(),
                        conversations = sidebar.conversations,
                        activeConversationId = sidebar.activeConversationId,
                        onNewConversation = {
                            chatViewModel.trySendAction(ChatAction.NewConversationClicked)
                        },
                        onConversationSelected = {
                            chatViewModel.trySendAction(ChatAction.ConversationSelected(it))
                        },
                        onConversationDeleted = {
                            chatViewModel.trySendAction(ChatAction.ConversationDeleted(it))
                        }
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = animatedBg,
                contentColor = state.theme.foreground,
                topBar = {
                    ProductionTopNavBar(
                        currentTheme = state.theme,
                        onOpenSidebar = { onAction(MainAction.SidebarToggled) },
                    )
                },
                // safeDrawing = systemBars ∪ displayCutout ∪ ime ∪ tappableElement
                // — it already includes the keyboard. Scaffold consumes all of it
                // and hands the result back as innerPadding, so the content only
                // needs Modifier.padding(innerPadding).
                //
                // Do NOT also apply imePadding() here: safeDrawing already covers
                // the IME, and applying both double-counts the bottom inset (the
                // official insets guide calls this out explicitly — "导航栏 inset
                // 与 IME inset 被同时应用 → 底部出现两个条形空白"). Pick one owner:
                // either Scaffold via contentWindowInsets, or the content via
                // imePadding() — never both.
                contentWindowInsets = WindowInsets.safeDrawing,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Collect the chat state here rather than at the root: a
                    // streaming reply then recomposes only the chat surface,
                    // not the whole NavHost tree. （ViewModel 实例在上面已取过一次，
                    // 同一个 ViewModelStoreOwner 拿到的是同一个对象。）
                    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
                    ChatScreen(
                        state = chatState,
                        onAction = chatViewModel::trySendAction,
                        currentTheme = state.theme,
                    )

                    // 「正在压缩」：历史超过窗口阈值时，核心要**额外打一次模型请求**把旧对话做成摘要，
                    // 界面会实打实停一下 —— 用这个对话框说清它在干什么，别让人以为卡死了。
                    //
                    // 它挂在**那条会话**的状态上（跟着 `chatState` 走），压完自动消失：见
                    // `ChatViewModel.handleAction` 入口那一段。
                    chatState.compactionNotice?.let { notice ->
                        AlertDialog(
                            // 压缩不可取消：关掉提示并不会让这一轮停下，所以不给任何可点的出口
                            //（`onDismissRequest` 空着 = 点遮罩/返回键也关不掉，提示会一直留到压完）。
                            onDismissRequest = {},
                            confirmButton = {},
                            title = { Text(stringResource(R.string.chat_compacting_title)) },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.chat_compacting_body,
                                        notice.tokens,
                                        notice.contextWindow,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
