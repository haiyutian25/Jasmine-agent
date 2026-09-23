# Jasmine 多模块架构与核心功能开发完全指南

> 本文档详细剖析项目的多模块工程结构、MVVM 状态管理、Navigation 3 导航、Hilt 依赖注入，以及所有 UI 组件、多主题切换、推拽式侧边栏、打字机启动页的开发原理、核心实现与调参指南。
>
> **本版本已按当前源码逐项核验（核验日期：2026-09-23）**，所有参数、路径与功能描述均与当前源码一致。

---

## 目录
1. [项目整体架构与模块划分](#1-项目整体架构与模块划分)
2. [MVVM 状态管理与数据流](#2-mvvm-状态管理与数据流)
3. [Hilt 依赖注入图谱](#3-hilt-依赖注入图谱)
4. [Navigation 3 导航系统](#4-navigation-3-导航系统)
5. [设计令牌系统（core:ui）](#5-设计令牌系统coreui)
6. [顶部导航栏 (TopNavBar) 与高度调控](#6-顶部导航栏-topnavbar-与高度调控)
7. [推拽式侧边栏 (Push Canvas Sidebar) 动画架构](#7-推拽式侧边栏-push-canvas-sidebar-动画架构)
8. [底部导航栏 (BottomNavBar)](#8-底部导航栏-bottomnavbar)
9. [启动页打字机引擎 (SplashScreen)](#9-启动页打字机引擎-splashscreen)
10. [对话界面 (ChatScreen)](#10-对话界面-chatscreen)
11. [设置流程](#11-设置流程)
12. [共享工具与测试体系](#12-共享工具与测试体系)
13. [核心组件参数速查](#13-核心组件参数速查)
14. [已知局限与工程问题](#14-已知局限与工程问题)

---

## 1. 项目整体架构与模块划分

本应用基于 **Jetpack Compose**（无任何 XML 布局），采用 **多模块 + MVVM + UDF（单向数据流）** 架构。

构建基线：AGP 9.4.1、Kotlin 2.4.20、Compose BOM 2026.09.00（Compose 1.12.1 / Material 3 1.4.0）、Navigation 3 1.1.7、Hilt 2.60.1、Lifecycle 2.11.0、Room 2.7.0、Google ADK for Kotlin 1.1.0、compileSdk 37、minSdk 26 / targetSdk 37、JDK 21。

> `minSdk 26` 由 ADK 强制：`com.google.adk:google-adk-kotlin-core` 在 Android 上经 Gradle Module Metadata 解析为 `google-adk-kotlin-core-android`，其 AAR 声明 `minSdkVersion=26`、`minCompileSdk=37`。只要 ADK 在依赖里，API 24–25 就不可达。

### 1.1 模块结构与职责

```
jasmine/
├── app/                          # 应用壳：单 Activity + 导航组装 + 主题应用
│   ├── JasmineApplication   # @HiltAndroidApp 入口
│   └── MainActivity              # @AndroidEntryPoint：hiltViewModel() + JasmineTheme + MainNavHost
├── core/
│   ├── agent/                    # Google ADK 接入层（Hilt 提供 ProviderProbe / AgentChat 两个门面）
│   │   ├── OpenAiWire                        # 两种 OpenAI 协议共用：JSON 配置 / SSE 传输 / 角色映射 / Schema 展平
│   │   ├── OpenAiChatWire                    # Chat Completions 线上格式（请求 / 响应 / 增量分片）
│   │   ├── OpenAiResponsesWire               # Responses API 线上格式（请求 / 响应 / 事件流）
│   │   ├── OpenAiChatCompletionsModel        # CHAT_COMPLETIONS 的 ADK Model 实现
│   │   ├── OpenAiResponsesModel              # RESPONSES 的 ADK Model 实现
│   │   ├── OpenAiModelFactory                # 按 apiType 选择 Model（唯一构造点）
│   │   ├── AdkProviderProbe                  # ProviderProbe 实现（连通性探测，非流式）
│   │   ├── AgentChat                         # 对话门面（LlmAgent + InMemoryRunner + InMemorySessionService + 历史重放）
│   │   ├── AdkAgentChat                      # AgentChat 实现（模型由工厂注入，便于测试）
│   │   └── di/AgentModule                    # @Provides 装配（模型工厂的组合根）
│   ├── data/                     # 数据层：仓库接口/实现 + Hilt 装配
│   │   ├── model/UserPreferences             # 领域模型（themeId / typographyChoice / colorMode / fontScale / activeCustomFontId / activeProviderId / activeModelId）
│   │   ├── datastore/UserPreferencesDataStore  # Preferences DataStore 读写（偏好唯一存储）
│   │   ├── repository/UserPreferencesRepository  # 偏好读写（DataStore 支撑）
│   │   ├── model/ProviderConfig          # 模型提供商配置（含 DeepSeek 预置 + ProviderApiType）
│   │   ├── datastore/ProviderDataStore   # 提供商列表 JSON 整体存取（独立 DataStore 文件）
│   │   ├── repository/ProviderRepository # 提供商增删改查（StateFlow 读 + 原子写）
│   │   ├── model/ChatRole                # 消息作者枚举（持久化于 transcript，也用于历史重放）
│   │   ├── model/Conversation            # 会话元数据 + TranscriptMessage 领域模型
│   │   ├── repository/ChatHistoryRepository  # 对话记录读写（Room 支撑，实体不外泄）
│   │   ├── datasource/FontRemoteDataSource       # 字体远端数据源（对接 core:network）
│   │   ├── manager/dispatcher/DispatcherManager  # 可注入协程调度器
│   │   └── di/DataModule                     # @Provides 装配
│   ├── database/                 # Room：对话记录持久化（conversations + messages）
│   │   ├── ConversationEntity / MessageEntity / ChatHistoryDao
│   │   ├── ChatTranscriptSchema              # v5 建表语句单一来源（被测试钉在 Room 导出 schema 上）
│   │   ├── AppDatabase                       # v5，exportSchema = true（schema 落在 core/database/schemas/）
│   │   └── di/DatabaseModule                 # @Provides 数据库（含 1→5 迁移链）
│   ├── navigation/               # Navigation 3 封装
│   │   └── AppNavigator                      # NavBackStack 包装（navigate/replace/goBack）
│   ├── network/                  # Retrofit/OkHttp/kotlinx.serialization（Hilt 提供，baseUrl 占位）
│   └── ui/                       # 设计系统
│       ├── theme/CssTokens       # CssVariables 模型、12 套预设、ThemeResolver（含 families 目录）
│       ├── theme/Theme           # JasmineTheme + LocalCssVariables + M3 ColorScheme 映射
│       ├── theme/Type            # AppTypography（Material3 Typography）+ LocalContentFontFamily
│       └── base/                 # BaseViewModel（UDF 三要素）+ EventsEffect（生命周期感知事件消费）
└── feature/
    ├── main/                   # 应用外壳：启动页 + 对话主页 + 导航组装
    │   ├── api/                 # 导航契约：@Serializable MainNavKey（Splash / Main）
    │   └── impl/                # UI + ViewModel
    │       ├── MainViewModel           # @HiltViewModel，外壳状态唯一源（主题 / 字体 / 标签 / 侧栏）
    │       ├── MainNavHost             # NavDisplay + entryProvider（含设置流目的地）；全局托管 Toast 事件
    │       ├── MainScreen              # 推拽侧边栏 + 单标签（CHAT）Scaffold
    │       ├── chat/ChatViewModel      # @HiltViewModel，对话状态机（消息 / 输入 / 流式追加 / 模型选择）
    │       ├── chat/ChatScreen         # 对话界面（消息列表 + 输入框 + 模型选择 BottomSheet）
    │       ├── fonts/                  # CustomFontFamilyCache（FontFamily 内存缓存，主线程零磁盘 IO）
    │       └── screens/SplashScreen    # chrome 组件已统一下沉 core/ui/components/
    ├── settings/                # 设置流
    │   ├── api/                 # 导航契约：@Serializable SettingsNavKey（5 个设置目的地）
    │   └── impl/                # 设置屏幕（无状态：只接收基元与回调）
    │       └── screens/{SettingsMenu,Settings,Language,Font,FontSize}Screen
    └── provider/                # 模型提供商流
        ├── api/                 # 导航契约：@Serializable ProviderNavKey（ProviderList）
        └── impl/                # ProviderViewModel（独立 UDF 三件套）+ ProviderScreen（列表 + 页内增改表单）
```

### 1.2 模块依赖方向（只能向下依赖）

```
app ──► feature:main:impl ──► feature:main:api
 │              │  │  │                    │
 │              │  │  ├► core:navigation ──┴► Navigation3 runtime/ui
 │              │  │  ├► core:agent ──► core:data / Google ADK（adk-kotlin-core）
 │              │  │  ├► core:data ──► core:database ──► Room（对话记录 conversations / messages）
 │              │  │  │            └─► core:network ──► Retrofit/OkHttp
 │              │  │  ├► core:ui
 │              │  │  ├► feature:settings:impl ──► feature:settings:api
 │              │  │  │                        └──► core:ui / core:data
 │              │  │  └► feature:provider:impl ──► feature:provider:api
 │              │  │                           └──► core:ui / core:data / core:agent
 │              │  └► feature:settings:api / feature:provider:api
 └► core:ui
```

> 规则：feature 之间不互相依赖；跨 feature 导航只允许依赖对方的 `:api` 模块；主题令牌放在 `core:ui` 保证所有模块可用。
>
> 例外：外壳 `feature:main:impl` 要在同一个 `NavDisplay` 里**组装**设置流与提供商流的目的地，所以额外依赖 `feature:settings:impl` 与 `feature:provider:impl`；反向不成立（两个特性模块都不认识外壳）。设置屏幕全部是**无状态**的（只接收基元与回调，不引用 `MainState` / `MainAction`），不会形成循环依赖。设置页标题等被两边共用的文案放在 `feature:settings:impl`，外壳通过 `SettingsR`（R 别名）引用；提供商页标题同理走 `ProviderR` 别名。**`feature:provider` 是唯一自带 ViewModel 的非外壳特性**（独立 UDF 三件套，`MainNavHost` 内经 `hiltViewModel()` 获取，activity 作用域）。

---

## 2. MVVM 状态管理与数据流

### 2.1 MainViewModel（feature:main:impl）

`@HiltViewModel` 注入 `UserPreferencesRepository`、`CustomFontRepository`、`CustomFontFamilyCache` 与 `@ApplicationContext`，继承 `core:ui` 的
`BaseViewModel<MainState, MainEvent, MainAction>`，以 UDF 三要素对外：

- **State**：单一不可变 `MainState`（`stateFlow`），聚合主题/排版/字体（含自定义字体列表与下载进度）/标签页/侧栏等全部状态；
  `theme` 与 `activeContentFont` 为派生字段，每次更新自动重算。设置流的页面位置**不在**此状态内——它由 Navigation 3 回退栈承载（见第 4 节）。
- **Action**：所有用户意图收敛为 `MainAction` sealed interface（如 `TabSelected`、
  `ThemeSelected`、`FontScaleSaved`、`FontDownloadClicked`），UI 一律 `trySendAction(...)` 发送。页面间导航不走 action——由 `AppNavigator` 直接操作回退栈。
- **Event**：一次性反馈（Toast）走 `MainEvent`（`eventFlow`），UI 经 `EventsEffect` 消费。

异步结果（偏好读取、字体下载/导入）通过 `MainAction.Internal.*` 回流 action 管道，
保证状态变更全部同步发生在 `handleAction` 内。

### 2.2 持久化回路（修复了旧版"重启即失忆"问题）

```
用户选择主题/排版 → trySendAction(MainAction.ThemeSelected/TypographySelected)
    → handleAction 同步更新 MainState
    → viewModelScope.launch { repository.updateTheme()/updateTypography() }
    → Preferences DataStore 原子写入（user_preferences 偏好文件）
    → preferencesStateFlow 发射 → Internal.PreferencesReceived action → 同步回填 MainState
```

- 主题解析集中在 `core:ui` 的 `ThemeResolver`（`familyOf` / `resolveFamily`，未知 ID 兜底 EditorialLight）。
- 导航界面状态（currentTab / isSidebarOpen）**不持久化**：它属于会话瞬态 UI 位置，持久化会导致恢复/写入反馈环（侧栏闪烁），并会在进程死亡后把用户带回旧页面而非启动页。设置流的页面位置由 Navigation 3 回退栈序列化、进程死亡后由导航库恢复，不属于偏好存储。

### 2.3 View 层观察方式

所有屏幕通过 `collectAsStateWithLifecycle()` 观察单一 `stateFlow`，回调发送 Action：

```kotlin
val state by viewModel.stateFlow.collectAsStateWithLifecycle()
// ...
onTabSelected = { viewModel.trySendAction(MainAction.TabSelected(it)) }
```

一次性事件用 `EventsEffect(viewModel) { ... }` 消费（生命周期感知，防重复导航类问题）。

纯瞬态动画状态（按压缩放、入场触发、复制成功标记、渲染延迟模拟值）仍留在 Composable 本地 `remember`，符合 MVVM"UI 瞬态不下沉"原则。

### 2.4 ChatViewModel（feature:main:impl/chat）

对话是**独立于外壳的第二套 UDF 三要素**，与 `MainViewModel` 互不依赖（外壳不需要知道对话状态，反之亦然）：

- **State**：`ChatState`（消息列表 / 输入 / 是否发送中 / 供应商列表 / 当前供应商与模型 / 模型选择是否展开）；`activeProvider`、`activeModel`、`isReady` 为派生属性（`isReady` 要求供应商已有 API 密钥且已选模型）。
- **Action**：`ChatAction`（`InputChanged` / `SendClicked` / `NewConversationClicked` / `ModelPickerOpened` / `ModelPickerDismissed` / `ModelSelected`）+ `Internal.*`（供应商与偏好的回灌、流式分片、失败、回合结束）。
- **Event**：无。失败直接渲染进消息气泡（比一闪而过的 toast 更可读、可回溯），因此 `BaseViewModel` 的事件类型参数取 `Nothing`。

依赖 `ProviderRepository`（模型目录）、`UserPreferencesRepository`（当前选择）、`ChatHistoryRepository`（对话记录）与 `AgentChat`（实时会话）。当前选择经 `updateActiveModel` 持久化，所以重启后仍指向同一个模型；transcript 由 Room 持久化，恢复时连同上文一起重放进 ADK 会话（见 §10.3）。

---

## 3. Hilt 依赖注入图谱

| 模块 | 注入内容 | 作用域 |
| :--- | :--- | :--- |
| `app` | `@HiltAndroidApp JasmineApplication`、`@AndroidEntryPoint MainActivity` | — |
| `core:database` | `DatabaseModule`：`AppDatabase`（Room.databaseBuilder，`jasmine.db`，v5，含 1→5 迁移链；`ChatHistoryDao` 暴露对话记录读写） | Singleton |
| `core:network` | `NetworkModule`：`OkHttpClient`（debug BASIC 日志 / release 静默）、`Retrofit`（kotlinx.serialization 转换器）、`@BaseUrl`、`FontDownloadApi`（独立 Retrofit 实例，长超时裸流下载） | Singleton |
| `core:data` | `DataModule`：`@Provides` `DispatcherManager`、`UserPreferencesRepository`、`ProviderRepository`、`ChatHistoryRepository`；`CustomFontRepository`（`@Singleton` 构造注入） | Singleton |
| `core:agent` | `AgentModule`：`@Provides` `ProviderProbe`（注入共享 `OkHttpClient` + `DispatcherManager`）与 `AgentChat` | Singleton |
| `feature:main:impl` | `@HiltViewModel MainViewModel` / `ChatViewModel`、@Singleton `CustomFontRepository`、@Singleton `CustomFontFamilyCache` | ViewModel / Singleton |
| `feature:provider:impl` | `@HiltViewModel ProviderViewModel`（注入 `ProviderRepository` + `ProviderProbe`） | ViewModel |

`MainActivity` 中通过 `hiltViewModel()`（`androidx.hilt.lifecycle.viewmodel.compose` 包）获取 VM，`JasmineTheme(cssVars = currentTheme)` 包裹 `MainNavHost`。

> `core:network` 当前只承载字体下载：`FontDownloadApi` 以 `@Streaming` 裸流拉取 GitHub Releases 上的预设字体（SHA-256 校验后落盘），`core:data` 的 `FontRemoteDataSource` 负责流式写盘与校验。baseUrl 仍是占位（`https://api.example.com/`），接真实后端只需替换 `NetworkModule.provideBaseUrl()` 并新增 service 接口。

---

## 4. Navigation 3 导航系统

### 4.1 导航契约（feature:main:api + feature:settings:api）

契约按 feature 拆成两份，由外壳的同一个 `NavDisplay` 统一组装：

```kotlin
// feature:main:api —— 外壳自己的目的地
@Serializable
sealed interface MainNavKey : NavKey {
    @Serializable data object Splash : MainNavKey              // 打字机启动页
    @Serializable data object Main : MainNavKey                // 主界面（侧栏 + CANVAS 单标签）
}

// feature:settings:api —— 设置流目的地
@Serializable
sealed interface SettingsNavKey : NavKey {
    @Serializable data object SettingsMenu : SettingsNavKey        // 设置菜单列表（设置流入口）
    @Serializable data object AppearanceSettings : SettingsNavKey  // 外观（明暗模式 + 调色板）
    @Serializable data object LanguageSettings : SettingsNavKey    // 语言
    @Serializable data object FontSettings : SettingsNavKey        // 字体（排版引擎 + 自定义字体）
    @Serializable data object FontSizeSettings : SettingsNavKey    // 字号（全局缩放）
}

// feature:provider:api —— 模型提供商流目的地
@Serializable
sealed interface ProviderNavKey : NavKey {
    @Serializable data object ProviderList : ProviderNavKey   // 提供商管理（列表 + 页内增改表单）
}
```

键实现 `androidx.navigation3.runtime.NavKey` 并标注 `@Serializable`，支持进程死亡后的状态恢复。**整个设置流都是回退栈上的平级目的地**——不再是 ViewModel 内的状态机，系统返回键/手势、预测返回与进程死亡恢复全部由 Navigation 3 处理。

### 4.2 AppNavigator（core:navigation）

对 `NavBackStack<NavKey>`（Navigation 3 的 NavigationState）的 MVVM 包装：`navigate(key)` 压栈、`replace(key)` 清栈替换、`goBack()` 弹栈。`rememberAppNavigator(vararg startKeys)` 内部使用 `rememberNavBackStack`，回退栈在配置变更/进程死亡后自动恢复。

### 4.3 MainNavHost（feature:main:impl）

```kotlin
val navigator = rememberAppNavigator(MainNavKey.Splash)

NavDisplay(
    backStack = navigator.navigationState,
    onBack = { navigator.goBack() },
    entryProvider = entryProvider {
        entry<MainNavKey.Splash> {
            SplashScreen(currentTheme = state.theme,
                onFinish = { navigator.replace(MainNavKey.Main) })
        }
        entry<MainNavKey.Main> {
            MainScreen(state = state, onAction = viewModel::trySendAction,
                onOpenSettings = { /* 收回侧边栏 + */ navigator.navigate(SettingsNavKey.SettingsMenu) })
        }
        // 设置流目的地共用私有脚手架 SettingsPage（背景过渡 + 顶栏子页形态），
        // 标题走各模块的字符串资源（SettingsR / ProviderR 别名）：
        entry<SettingsNavKey.SettingsMenu>       { SettingsPage(R.string.settings_page_title) { SettingsMenuScreen(...) } }
        entry<SettingsNavKey.AppearanceSettings> { SettingsPage(SettingsR.string.settings_menu_appearance_title) { SettingsScreen(...) } }
        entry<SettingsNavKey.LanguageSettings>   { SettingsPage(SettingsR.string.language_title) { LanguageScreen(...) } }
        entry<SettingsNavKey.FontSettings>       { SettingsPage(SettingsR.string.settings_menu_font_title) { FontScreen(...) } }
        entry<SettingsNavKey.FontSizeSettings>   { SettingsPage(SettingsR.string.settings_font_size_label) { FontSizeScreen(...) } }
        entry<ProviderNavKey.ProviderList>       { SettingsPage(ProviderR.string.provider_page_title) { ProviderScreen(...) } }
    }
)
```

> 注：`Main` 目的地的内容区是对话界面（`ChatScreen`），其 `ChatViewModel` 由条目级
> `rememberViewModelStoreNavEntryDecorator()` 限定作用域——离开主界面即销毁会话。

- Splash 完成/跳过 → `replace(Main)`（栈内只剩 Main，系统返回键直接退出应用）。
- 侧边栏打开时的返回键由 `MainScreen` 内的 `BackHandler(enabled = isSidebarOpen)` 优先拦截（先收侧边栏，再交给导航）。
- 设置目的地的返回统一走 `navigator.goBack()` 逐级弹栈（顶栏返回键与系统返回键/手势同一通道），由 NavDisplay 的 `onBack` 接管，天然支持预测返回。
- `MainNavHost` 全局托管 Toast 一次性事件（`EventsEffect` 消费 `MainEvent.ShowToast`，集中在此弹 Toast），因此在设置页上同样可用。

---

## 5. 设计令牌系统（core:ui）

### 5.1 CssVariables 数据模型

`core/ui/theme/CssTokens.kt` 定义 **13 个颜色令牌 + 3 个圆角令牌**，一一对应 CSS Custom Properties：`background(--bg) / foreground(--text) / card(--surface) / cardForeground / border / primary(--accent) / primaryForeground / muted(--muted) / mutedForeground(--muted-foreground) / accent / accentForeground / ring / subtleSurface` + `radiusSm=10dp / radiusMd=16dp / radiusLg=24dp`。

> ℹ️ 令牌目前只在 Compose 内消费（`CssVariables` → Material 3 `ColorScheme`）；原先的 `:root` CSS 文本导出已随 CSS 变量审查器一并移除。

### 5.2 12 套预设（ProductionPalettes）

6 大家族 × 明暗双版：

| 家族 | 标志色 | 浅色底 / 深色底 |
| :--- | :--- | :--- |
| **Editorial**（经典报刊） | 浅黑 `#000000` / 深白 `#FFFFFF` | `#FAFAFA` / `#0A0A0A` |
| **Geist**（Vercel 极简） | 电光蓝 `#0070F3` | `#FFFFFF` / `#000000` |
| **Linear**（黑曜石） | Linear 紫 `#5E6AD2` | `#F7F8F9` / `#08090A` |
| **Shadcn Zinc**（冷灰工业） | 浅 `#18181B` / 深 `#FAFAFA` | `#FFFFFF` / `#09090B` |
| **Notion Warm**（暖调侘寂） | 赤陶红 `#EB5757` | `#FBFBFA` / `#191919` |
| **Braun Dieter Rams**（包豪斯） | 布劳恩信号橙 `#FF5500` | `#E8E8E3` / `#111111` |

### 5.3 主题注入与切换动画

`JasmineTheme(cssVars)` 经 `CompositionLocalProvider(LocalCssVariables provides cssVars)` 注入令牌，同时映射为 Material 3 `ColorScheme`。`MainScreen` 对背景色做 280ms `FastOutSlowInEasing` 全局插值过渡：

```kotlin
val animatedBg by animateColorAsState(
    targetValue = currentTheme.background,
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    label = "bg_color"
)
```

主题家族识别与目录集中在 `ThemeResolver`（core:ui）：`familyOf(themeId)`（先按 `families` 精确匹配，再回退去除 `-light`/`-dark` 后缀）、`families`（`PaletteFamily(key, light, dark)` 有序目录，设置页调色板列表使用）。**新增家族只需在 `families` 加一条**（设置页如需本地化名称/副标题再加一对字符串资源），选择器会自动出现新家族，无需再改屏幕代码。

---

## 6. 顶部导航栏 (TopNavBar) 与高度调控

位于 `core/ui/components/TopNavBar.kt`（`ProductionTopNavBar`）。

### 6.1 结构与高度

```kotlin
Column(modifier = modifier.fillMaxWidth().background(currentTheme.background).statusBarsPadding()) {
    Row(modifier = Modifier.fillMaxWidth().height(TopNavBarHeight).padding(horizontal = 10.dp)) {
        // 仅侧边栏开关(28dp 点击区域 + 28dp 纯 Menu 图标，无底色/边框/按压水波纹，testTag = "top_nav_sidebar_btn")
    }
    Box(Modifier.fillMaxWidth().height(TopNavDividerHeight).background(currentTheme.border)) // 1px 分割线
}
```

顶栏为**双形态**设计（`pageTitle` 参数驱动）：

- **主界面形态**（`pageTitle == null`，由 `MainScreen` 使用）：仅左侧侧边栏开关按钮（28dp 纯 Menu 图标，无底色/边框/水波纹，`testTag = "top_nav_sidebar_btn"`）。
- **子页面形态**（`pageTitle != null`，由设置目的地的 `SettingsPage` 脚手架使用）：左侧按钮自动变为**返回键**（ArrowBack，`testTag = "top_nav_back_btn"`，点击 → `navigator.goBack()`），**居中显示当前页面标题**——菜单页显示 "Settings"，外观/字体/字号/语言页显示对应菜单名。

两种形态均保留底部 1px 分割线。原 Monogram 徽标、品牌标题、呼吸脉冲药丸、右侧主题家族徽标均已移除；设置入口在侧边栏底部，主题切换仅在设置页完成。

**粗细调整**：
- **整条栏高度**：改常量 `TopNavBarHeight`（当前 47.dp，两种形态共用一处定义）——细：36~40dp；粗：56/64dp（Material 3 标准），同步微调内部按钮（28dp）。
- **分割线粗细**：改常量 `TopNavDividerHeight`（当前 1.dp）——发丝线 0.5dp、加粗 2dp、整块删除则无分割线。

`.statusBarsPadding()` 保证状态栏避让。设置页 `SettingsScreen` 已移除自带的页面级顶栏（含返回按钮），全应用顶部区域统一由 `ProductionTopNavBar` 管理。

---

## 7. 推拽式侧边栏 (Push Canvas Sidebar) 动画架构

侧边栏展开时**主画布被同步推开**（非浮动蒙层），实现位于 `core/ui/components/SidebarDrawer.kt`（`MainScreen` 负责组装并喂入状态）。

### 7.1 双层平移动画

```kotlin
val SidebarWidth = 295.dp
val SidebarDrawerEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // 豪华减速曲线

// 以进度 p（0→1，320ms）驱动：主画布位移 0→295dp；侧边栏位移 -295dp→0
val pushOffset = SidebarWidth * p
val panelOffset = -SidebarWidth * (1f - p)
// 主画布圆角 0→18dp、投影同步动画
```

### 7.2 层级结构

1. 底层：295dp 位移槽内的侧边栏内容（内容宽度 = `SidebarWidth`，无溢出裁剪——调整槽宽只需改这一处常量）。
2. 表层：被推开的主画布（`offset(pushOffset)` + 动态圆角/阴影/1dp 描边）。
3. 打开方式：顶栏菜单按钮，或**屏幕左缘 32dp 边缘滑出**（`SidebarEdgeZone`；底栏分页滑动会避开该区域）。
4. 收回机制（3 种）：① **系统返回键/手势**（`BackHandler(enabled = isSidebarOpen)` 优先拦截，只收侧边栏不退出页面）；② **点击侧边栏以外的区域**（全透明拦截层，`testTag = "sidebar_outside_dismiss"`，无视觉遮罩；拦截层通过 `padding(start = SidebarWidth)` 在几何上**只覆盖侧边栏右侧区域**——若做成全屏，点在侧边栏非交互区域的事件未被消费时会穿透到拦截层导致误收回）；③ **点击底部设置入口**（打开设置菜单页后自动收回）。层级顺序：主画布（底）→ 外部点击拦截层（中，仅展开时存在）→ 侧边栏（顶）。

### 7.3 侧边栏内容（AppSidebarContent）

极简两段式结构：**顶部为空**（原品牌工作区头部——"J" 头像、"Jasmine Studio"、PRO 徽标与 "Design Systems Lab" 副标题——已整体移除，无关闭按钮），中间弹性留白，**底部固定设置入口**（右对齐的 32dp 纯齿轮图标按钮，点击 → `MainAction.SidebarClosed` + `navigator.navigate(MainNavKey.SettingsMenu)` 打开设置菜单目的地——该入口从顶栏迁移而来）。原导航组、调色板快切、快捷工具与引擎页脚均已移除；标签切换由底部导航栏承担，主题切换仅在设置页完成。

---

## 8. 底部导航栏 (BottomNavBar)

`core/ui/components/BottomNavBar.kt`（`ProductionBottomNavBar`）：**目前只有 CHAT 一个标签**（`NavigationTab` 枚举仅剩 `CHAT`，文案 `nav_tab_chat` = Chat / 对话；原 `TYPOGRAPHY` / `TOKENS` / `SETTINGS` 及其屏幕已删除，设置功能整体在侧边栏触发的设置流程中）。激活态为微胶囊背景 + 颜色过渡，`.navigationBarsPadding()` 避让手势条。内容区仍由底栏自身托管为可滑动分页（320ms 分页动画；快滑阈值 180px/s，慢拖阈值 28% 页宽），单标签下不会产生实际翻页；侧栏展开时滑动禁用，左缘 32dp（`SidebarEdgeZone`）保留给侧栏滑出。**底部导航栏只存在于 Main 目的地**——设置菜单页与各设置子页是回退栈上的独立目的地，天然不渲染底栏。

---

## 9. 启动页打字机引擎 (SplashScreen)

`screens/SplashScreen.kt`，纯无状态组件（`currentTheme + onFinish`），由 Nav3 的 Splash 键承载。

核心时序（已核验）：
- **光标闪烁**：480ms `LinearEasing` 无限往复。
- **光环呼吸**：1800ms `FastOutSlowInEasing`；**自转渐变环**：12000ms `LinearEasing` 旋转。
- **打字节奏**：起始延迟 350ms → 逐字 `typingDelay` → 行间停顿 400ms → 末句停顿 850ms 后触发 `onFinish`（目的地切换由 NavDisplay 默认过渡处理，无额外淡出动画）。
- 阶段进度条与 Skip/Enter 双入口均可提前结束。

---

## 10. 对话界面 (ChatScreen)

`chat/ChatScreen.kt` 承接底部导航栏的 CHAT 标签页：**消息列表 + 输入框 + 模型选择**，由 `chat/ChatViewModel` 驱动（UDF，状态为 `ChatState`）。

### 10.1 界面结构

- **未就绪态**（`ChatState.isReady == false`）：整屏替换为引导页 —— 标题 + 说明（区分"未选模型"与"所选供应商缺 API 密钥"，后者带上供应商名）+「选择模型」按钮 +「管理模型提供商」入口（复用 `onOpenSettings`，零新增导航管道）。没有可用端点时聊天是死路，所以这里不做成可输入的界面。
- **就绪态**：`ChatHeader`（48dp：左侧当前模型名，点开模型选择；右侧"新对话"，仅在已有消息时出现）→ `MessageList`（`LazyColumn`，随消息数与流式文本增长自动贴底）→ `Composer`（`BasicTextField` 多行 + 圆形发送键；发送中显示转圈）。
- **气泡**：用户侧右对齐、`primary` 底 + `primaryForeground` 字；助手侧左对齐、`card` 底 + 1dp 描边；**失败消息**用 `subtleSurface` 底 + `mutedForeground` 字，正文里带着原始原因。宽度上限 `0.85`（`fillMaxWidth(fraction)` + `wrapContentWidth`），靠不对称读出"对话"感。
- **模型选择**：复用 `core:ui` 的 `BottomSheet`，按供应商分组列出**已配置的模型**（无模型的供应商不出现，空态指向「模型提供商」）；当前项打勾。

### 10.2 会话与流式

- 会话由 `core:agent` 的 `AgentChat` 承载（ADK `LlmAgent` + `InMemoryRunner` + `InMemorySessionService`）；**ADK 类型不外泄**，UI 只消费 `ChatEvent`（`Text` / `Failed` / `Completed`）。
- 每个分片经 `ChatAction.Internal.ReplyChunk` 回灌 action 管道，**状态变更仍全部同步发生在 `handleAction` 内**（与字体下载进度同一模式）。
- 流式追加只更新"当前正在流式的那条助手消息"（按 id 定位），因此 `LazyColumn` 的 key 唯一、历史消息不重组。
- **切换模型 / 切换历史会话 / 新对话都会重建 ADK 会话**（一个 ADK session 绑定一个模型）；前两者保留 transcript 并重放，新对话清空界面（旧记录仍在 Room 里，可从历史面板取回）。
- `runTurn` 在流结束后**额外补发一次 `TurnCompleted`**：ADK 不保证把最后一个事件标记为 `turnComplete`（`AdkAgentChatTest` 里已确认），不补发就会把输入框永久卡在"发送中"。
- ADK 会话是**内存态**（`InMemorySessionService`），离开 `Main` 目的地即随 `ChatViewModel` 销毁；durable 记录在 Room 中，由 §10.3 描述。
- `ChatViewModel` 由 `Main` 条目作用域持有（`rememberViewModelStoreNavEntryDecorator()`），且**在 `MainScreen` 的内容区收集状态**——流式分片只重组对话界面，不触发 NavHost 全树重组。

### 10.3 持久化与历史对话

**两份存储，各司其职**：Room 是持久记录（`conversations` + `messages`），ADK 的 `InMemorySessionService` 只是模型的**工作上下文**。

- **写入时机**：用户消息在发送时落库；助手回复**在回合结束时落库一次**，不是每个分片一次——内存里的消息是实时视图，数据库行是持久记录。会话行在**首次发送**时创建（因此不会留下空会话），`title` 取首条用户消息（截断 60 字符）。
- **恢复**：启动时读取最近更新的会话，把 transcript 填回界面。**模型选择不由会话决定**——它属于偏好（`activeProviderId` / `activeModelId`），是"我现在用哪个模型"的唯一来源；会话只记录"它由哪个模型产生"（在历史列表里显示），这样也避免了两份真源互相覆盖的竞态。
- **上下文连贯（关键）**：恢复后若直接发消息，模型会以为对话是全新的——所以 `AgentChat.startConversation(..., history)` 把 transcript **重放进新会话**（ADK `SessionService.appendEvent`）：runner 的请求内容是从会话事件构建的，不重放就没有历史。重放**排除失败回复**（错误文本不是模型输出），并**排除本轮新消息**（它以 `newMessage` 进入，一并重放会重复）。
- **会话重建时机**：切换模型、切换历史会话、新建对话都会重建 ADK 会话（键为 `会话id|供应商|模型`），并把当前 transcript 重放进去。
- **历史面板**：头部时钟图标打开 `BottomSheet`，按 `updatedAt` 倒序列出会话（标题 + 产生它的模型），可切换或删除；删除走外键级联，消息一并消失。
- **持久化是 best-effort**：写库失败会被 `runCatching` 吞掉而不中断对话。本地 SQLite 加 schema 编译期校验，实际近乎不可能触发；代价是这种情况下历史静默丢失。

---

## 11. 设置流程

> 本章内容全部位于 **`feature:settings:impl`**（导航契约 `SettingsNavKey` 在 `feature:settings:api`）；
> 目的地由外壳 `MainNavHost` 的 `NavDisplay` 组装。

> 原先的「排印工作室（TypeStudioScreen）」与「令牌面板（TokensScreen）」两个标签页，
> **已连同 `NavigationTab.TYPOGRAPHY` / `NavigationTab.TOKENS` / `NavigationTab.SETTINGS` 一并删除**，
> 底部导航栏现在只剩 CANVAS 一个标签。主题与字体能力并未删除，仍完整保留在下面的设置流中。

### 11.1 目的地与入口

设置流是 Navigation 3 回退栈上的**平级目的地序列**（早期版本的 `settingsLevel` 状态机已整体移除）。每个目的地共享 `MainNavHost` 内的私有脚手架 `SettingsPage`：与主壳相同的 280ms 背景色过渡 + `ProductionTopNavBar` 子页形态（返回键 + 居中标题），仅内容区随目的地切换。

```
Main → SettingsMenu（设置菜单列表）→ AppearanceSettings（外观设置页）
                                  ├→ FontSettings（字体页）→ FontSizeSettings（字号页）
                                  └→ LanguageSettings（语言页）
```

- **入口**：侧边栏底部齿轮按钮 → `MainAction.SidebarClosed` + `navigator.navigate(MainNavKey.SettingsMenu)`。
- **菜单页（SettingsMenuScreen）**：分组卡片列表样式（iOS 式 list section），**两张独立卡片**：第一张 = Appearance & Themes / Font / Language 三行（行间 1dp `border` 发丝分割线）；第二张 = **Model Providers 单独成卡**（SmartToy 图标 → `ProviderNavKey.ProviderList`，模型提供商是独立特性，不与外观/字体/语言同卡）。每行 = 前置单色图标（`foreground`，20dp）+ 标题（14sp Medium），**无副标题、无右侧箭头**，整行即点击目标。
- **模型提供商页（feature:provider，ProviderScreen + ProviderViewModel）**：独立 UDF 特性模块。列表模式 = 分组卡片列出全部供应商（**DeepSeek 预置**在首位，可编辑不可删除；用户可自由添加 OpenAI 协议兼容供应商）+「添加供应商」入口行；编辑模式 = 名称 / 接口地址 / API 密钥三个主题化输入框 + API 类型二选一卡片（**Chat Completions / Responses API**，两种协议均已实现）+ **模型区** + **「测试连接」按钮** + 取消/保存。
- **测试连接**：用**尚未保存的草稿凭据**与已配置的第一个模型发起一次真实请求（走 `core:agent` 的 `ProviderProbe`，非流式、最小提示），成功时 toast 附带模型回复（截断 60 字符），失败时附带原始原因（HTTP 状态 / 供应商错误消息）。这样用户可以在保存前验证端点与密钥，而不必切到对话页试错。
- 此处配置的模型会出现在**对话页的「选择模型」列表**中；"用哪个模型对话"由对话页决定并持久化在 `activeProviderId` / `activeModelId`，提供商页不持有该状态。
- **模型目录（无硬编码）**：模型区提供「获取模型列表」与「自定义模型 ID」两个入口。获取走 `core:data` 的 `ProviderModelDataSource`（注入共享 `OkHttpClient`，`GET {baseUrl}/v1/models` + Bearer 鉴权，宽容解析 OpenAI `data[]` 与 DeepSeek `models[]` 两种响应形状；baseUrl 以 `/v1` 结尾不重复拼接），在 IO 调度器执行，结果经 `Internal.ModelsFetched/ModelsFetchFailed` 回注。目录用 **`core:ui` 的 `BottomSheet` 组件**展示（Fetching 转圈 / 列表点选 / 失败态含重试与自定义入口；列表顶部恒有「自定义模型 ID」行，保证 /models 不可用的供应商也能配置）。
- **模型参数**：点选或自定义后进入第二个 `BottomSheet` 参数表单——模型 ID（目录点选预填、自定义可自由输入）+ **上下文长度 / 输出长度**（tokens，数字键盘，留空 = 0 未设置）；保存进 `ModelConfig(id, modelId, contextLength, maxOutputLength)` 挂在供应商草稿上，随供应商一起持久化（模型行支持再编辑/删除）。
- 持久化走 `core:data` 的 `ProviderRepository`（`ProviderDataStore`，整表 JSON 存于独立 DataStore 文件 `model_providers`）；保存前校验三字段非空（Toast 提示），删除/保存均为乐观更新 + 仓库 StateFlow 回显对账。
- **外观设置页（SettingsScreen）**：明暗/跟随系统三卡选择器 + 12 调色板列表（家族目录来自 `ThemeResolver.families`，当前选中高亮）。调色板行只显示**双色样点 + 本地化族名**，无描述副标题（描述文案与 `CssVariables.description` 字段已整链删除）。
- **字体页（FontScreen）**：3 排版引擎（Serif/Sans/Mono，行内只有 "Aa" 样例 + 引擎名，无风格描述副标题）、字号入口、自定义字体管理（见 11.2）。
- **字号页（FontSizeScreen）**：全局字体缩放滑块 + 实时预览，保存 → `MainAction.FontScaleSaved`（持久化；`MainActivity` 通过 `LocalDensity` 的 `fontScale` 全局生效）。
- **语言页（LanguageScreen）**：跟随系统 / English / 中文，经 `AppCompatDelegate.setApplicationLocales` 持久化并即时重建 Activity（`locales_config.xml` 声明 en、zh-CN，支持 Android 13+ 系统级应用语言列表）。
- **返回**：系统返回键/手势与顶栏返回键统一走 `navigator.goBack()` 逐级弹栈（FontSize→Font→Menu→Main），由 NavDisplay 的 `onBack` 接管，天然支持预测返回与进程死亡恢复。设置目的地不经过 `MainScreen`，因此**天然不渲染底部导航栏与侧栏**，页面视觉只保留全局顶栏 + 内容区。

### 11.2 自定义字体系统（core:data + impl/fonts/）

- **预设库（PresetFontCatalog，core:data/model）**：3 款字体（Source Han Serif SC / LXGW WenKai / JetBrains Mono）托管于 GitHub Releases（`releases/latest/download/<file>` HTTPS 直链），每条记录含 `sha256` 校验和。
- **下载（CustomFontRepository.downloadPreset，core:data）**：IO 调度器流式写入 `.part` 临时文件，完成后做 **SHA-256 校验**，不匹配即删除拒绝安装；校验通过原子改名入库。下载进度经 `downloadProgress: StateFlow` 实时回流 UI。
- **导入（importFont）**：支持用户选择本地 .ttf/.otf，文件名规范化（非法字符→`_`）并以 `upload_` 前缀落盘，自动去重加序号。
- **热路径零磁盘 IO**：`installedIds` 内存快照（AtomicReference）+ `fontFamilyCache`（ConcurrentHashMap，impl/fonts/ 的 CustomFontFamilyCache）回答成员与 FontFamily 查询；字体目录经一次性 `ensureFontsDir`（AtomicBoolean）创建，`installedVersion` 变更触发重新扫描自愈。
- 选中自定义字体会清空系统排版引擎选择（互斥），反之亦然；选择持久化于 `activeCustomFontId`。

---

## 12. 共享工具与测试体系

### 12.1 测试栈

| 测试 | 内容 | 说明 |
| :--- | :--- | :--- |
| `ExampleUnitTest` | 2+2 | 模板级 |
| `ExampleRobolectricTest` | 读取 `app_name` 资源 | Robolectric |
| `MainScreenshotTest` | Roborazzi 渲染首页 | 验证首页 UI 可组合渲染 |
| `OpenAiWireTest`（`core:agent`） | 断言两种 OpenAI 协议发出的 JSON 与解析回的 ADK 类型 | 纯 JVM 单测，无需网络与 API Key |
| `AdkAgentChatTest`（`core:agent`） | 用录制型 `Model` 驱动**真实 ADK runner**，验证历史重放确实进入模型请求、流式文本与错误映射、未开会话即发送会被拒 | 无需网络与 API Key |
| `MigrationDdlTest`（`core:database`） | 把 v5 建表语句钉在 Room 导出的 schema 上（双向比对，忽略空白） | 纯 JVM 单测，比对 SQL 文本 |
| `ChatViewModelTest`（`feature:main:impl`） | 对话状态机：发送 / 流式追加 / 失败 / 回合结束 / 首条消息建会话并落库 / 恢复并重放 / 切换与删除会话 / 失败回复不重放 | 用假仓库与假 `AgentChat` 替换，不触网 |

- Robolectric 基线 **SDK 36**（`app/src/test/resources/robolectric.properties`），**要求 JDK 21**（SDK 36 沙盒硬性要求；SDK 37 需 Robolectric 4.17-beta，暂不采用）。
- 截图基准图生成：`gradle :app:testDebugUnitTest -Proborazzi.test.record=true`。

### 12.2 构建验证命令

```
gradle :app:compileDebugKotlin                # 全模块编译 + KSP（Room/Hilt）
gradle :app:assembleDebug                     # 完整打包（需根目录 debug.keystore）
gradle :app:testDebugUnitTest                 # app 单元测试 + 截图测试
gradle :core:agent:testDebugUnitTest          # OpenAI 协议线上格式 + ADK 会话/重放
gradle :core:database:testDebugUnitTest       # 迁移 DDL 与 Room 导出 schema 的一致性
gradle :feature:main:impl:testDebugUnitTest   # 对话状态机
```

---

## 13. 核心组件参数速查

| 参数 | 值 | 位置 |
| :--- | :--- | :--- |
| 顶栏内容行高 | 47dp（`TopNavBarHeight`） | TopNavBar |
| 侧边栏槽宽 / 内容宽 | 295dp / 295dp（同一常量 `SidebarWidth`） | SidebarDrawer |
| 侧边栏边缘滑出区 | 32dp（`SidebarEdgeZone`） | SidebarDrawer |
| 推拽动画 | 320ms，CubicBezier(0.16,1,0.3,1) | SidebarDrawer |
| 底栏分页滑动 | 320ms；快滑 180px/s，慢拖 28% 页宽 | BottomNavBar |
| 背景色过渡 | 280ms FastOutSlowIn | MainScreen |
| 对话头部行高 | 48dp（`ChatHeaderHeight`，容纳 48dp 触摸目标） | ChatScreen |
| 对话气泡最大宽度 | 0.85 页宽（`ChatBubbleMaxWidthFraction`） | ChatScreen |
| 对话发送键 | 44dp 圆形，图标 18dp / 转圈 16dp | ChatScreen |
| 对话输入框 | 最多 5 行，`ImeAction.Send` 即发送 | ChatScreen |
| 模型选择 / 历史列表最大高 | 380dp（`ChatPickerListMaxHeight`，两处共用） | ChatScreen |
| 会话标题长度 | 首条用户消息截断 60 字符（`TITLE_MAX_LENGTH`） | ChatViewModel |
| 打字机 | 起始 350ms，逐字 28ms（末句 42ms），行间 400ms，收尾 850ms | SplashScreen |
| 光标 / 光环 / 自转环 | 480ms / 1800ms / 12000ms | SplashScreen |
| 偏好存储 | Preferences DataStore（`user_preferences`：主题 / 排版 / 明暗 / 字号 / 自定义字体 / **activeProviderId / activeModelId**） | core:data |
| 结构化数据库 | `jasmine.db` v5（`conversations` / `messages`，含 1→5 迁移链） | core:database |
| 字体下载源 | GitHub Releases 直链 + SHA-256 校验 | feature:main:impl/fonts |

---

## 14. 已知局限与工程问题

1. **网络层分工**：`core:network` 的 Retrofit 栈仍只服务字体下载（`FontDownloadApi` 指向 GitHub Releases 绝对 URL，baseUrl 仍是 `https://api.example.com/` 占位）；ADK 适配层**不走 Retrofit**——供应商 baseUrl 是运行期动态的，因此复用共享 `OkHttpClient` 直接发请求（与 `ProviderModelDataSource` 同一策略）。
2. **ADK 依赖的是一个框架内部 API**：流式聚合用的是 `StreamingResponseAggregator`，它带 `@FrameworkInternalApi`（全库仅 5 个类引用该注解）。因此 `core:agent` 需要 `@OptIn`，且 **ADK 小版本升级可能破坏流式路径**；`Model` 接口本身是公开稳定契约，非流式路径不受影响。
3. **Responses API 的两点取舍**：① 工具调用参数**不做逐字流式**——Responses 只给原始 JSON 片段，而 ADK 的 `PartialArg` 机制需要 `jsonPath`，从片段反推路径不可靠，故在 `response.output_item.done` 拿到完整 `arguments` 后一次性喂入聚合器；② 该协议**没有 `stop` 参数**，ADK 的 `stopSequences` 在此协议下被主动丢弃（有单测断言）。
4. **`tools[].type` 的坑（已修，有回归测试）**：`openAiJson` 关闭了 `encodeDefaults`，因此**带默认值的必填字段不会被序列化**。原先 `ChatTool.type` 的默认值恰是 `"function"`，导致带工具的请求会漏掉 `type` 而被供应商拒绝；现改为必填无默认值（Responses 的 `parameters` / `strict` 同理）。
5. **APK 打包：ADK 带来两个连带阻断（已修，但要记住原因）**：① `google-auth-library-*` 与 `api-common` 三个 jar 各自携带同名 `META-INF` 元数据（`INDEX.LIST` / `DEPENDENCIES` / `LICENSE` / `NOTICE`），重复条目让 `mergeReleaseJavaResource` 直接失败 → 在 `app` 的 `packaging { resources { excludes += ... } }` 中排除；② kxml2 自带一份 `org.xmlpull.v1`，与 Android 平台类冲突，R8 报 `Library class android.content.res.XmlResourceParser implements program class org.xmlpull.v1.XmlPullParser` → 在 ADK 依赖上 `exclude(group = "net.sf.kxml", module = "kxml2")`。
    **kxml2 的取舍**：ADK 只有一个类真的用它——`FunctionToolExtensionsKt` 在把 OpenAPI 规范转成工具时直接 `new` 了 `org.kxml2.io.KXmlSerializer`，而本项目不使用 OpenAPI 规范式工具，该路径不可达。将来若要支持它，需要改用能剥离 `org.xmlpull.v1` 包的 artifact transform，而不是排除整个模块。
    **教训**：只跑 `compileDebugKotlin` / `testDebugUnitTest` 既不会合并 APK 资源、也不做 R8，所以这两个问题在 ADK 接入后长期存在而未被发现——**"能编译"不等于"能打包"**。
6. **ADK 在 R8 下的风险评估**：ADK 的 `classes.jar` 里 `Class.forName` 与 `ServiceLoader` 均为 **0 处**（无基于类名的发现机制），且**不携带 consumer proguard 规则**；项目已有的 `-keep @kotlinx.serialization.Serializable class * { *; }` 覆盖了 ADK 的 `@Serializable` 类型。因此 release 构建的主要风险不在类加载，而在**运行时行为**（首次真实调用 agent 的流式路径）。
7. **API Key 存在设备上**：`ProviderConfig.apiKey` 存于 Preferences DataStore 并由设备直连供应商。官方 Android 指南明确不建议在客户端内嵌密钥（建议自建后端或 Firebase AI Logic）。当前定位是"用户自备密钥的个人工具"，若要上架发布需改为代理方案。
8. **Room 已投入使用，但迁移从未被真正执行过**：v5 引入 `conversations` / `messages`，并删掉了无任何读者的 legacy `user_preferences` 表（其 `UserPreferencesEntity` 一并移除）。`core:data` 对 `core:database` 的依赖也从 `api` 收紧为 `implementation`——Room 实体与 DAO 不再外泄（`core:data` 因此需要直接依赖 `room-runtime`）。**缺口**：迁移 DDL 只被 `MigrationDdlTest` 在文本层面钉在 Room 导出 schema 上，尚未接入 Room 官方的 `MigrationTestHelper`（需 Robolectric + `room-testing`），所以"迁移跑在真实 SQLite 上并让 Room 校验通过"这一步没有自动化覆盖。schema 已导出到 `core/database/schemas/`，具备接入条件。
9. **家族文案为可选本地化**：设置页族名经 `SettingsScreen.paletteNameRes` 按家族 key 查本地化资源；未配置的新家族自动回退到该家族自身的 `displayName`（英文），不会错标为其它家族；需要本地化时补一条字符串资源即可。（描述副标题已整体移除，`CssVariables` 不再携带 `description` 字段。）
10. **截图基准默认不校验**：`app/src/test/screenshots/chat.png` 已提交（渲染的是带样例对话的对话界面，而非空表面）。默认 `testDebugUnitTest` 下 Roborazzi 未激活任何模式（record/verify/compare 均未开），`captureRoboImage` 空转通过、不做校验——CI 绿灯对 UI 回归没有保护。重新生成基准用 `gradle :app:testDebugUnitTest -Proborazzi.test.record=true`（**PowerShell 下 `-P` 参数会被吞掉，需加引号：`'-Proborazzi.test.record=true'`**），CI 校验用 `-Proborazzi.test.verify=true`。真正有断言价值的是 `core:agent` / `core:database` / `feature:main:impl` 的纯逻辑单测。
11. **工具调用不在 UI 呈现**：`core:agent` 会把模型的工具请求搬进 `Part.functionCall` 交给 ADK 执行，但对话界面目前**只渲染文本**——工具调用与结果对用户不可见（ADK 侧执行正常）。多模态输入（图片/音频）尚未支持，`input` 只发文本消息。
12. **长对话不做压缩**：恢复或重建会话时会把整份 transcript 重放进 ADK 会话，**没有做历史压缩/摘要**（ADK 提供了 compaction 能力，尚未接入），超长会话最终会撞上模型的上下文上限。历史面板也只支持切换与删除，没有搜索或导出。
13. **签名与打包环境**：release 的密钥库解析顺序为 `KEYSTORE_PATH`（环境变量，CI / 显式覆盖优先）→ `${rootDir}/my-upload-key.jks`；别名固定为 `upload`，密码来自 `STORE_PASSWORD` / `KEY_PASSWORD`。工作区已放置一枚**测试用**密钥库 `my-upload-key.jks`（`CN=Jasmine Test Release`，git-ignored），可用于安装测试但**不能用于发布**（Play 拒绝 debug 级/测试身份，且换密钥必须先卸载）。
    **排查要点**：构建**不会回显**实际使用的密钥库，签名对不对只能用 `apksigner verify --print-certs` 验证。本机就曾因为持久化的 `KEYSTORE_PATH` 指向另一目录的 debug 密钥库，导致 release 包被签成 `CN=Android Debug` —— 而 `assembleDebug` 固定读 `${rootDir}/debug.keystore`（与 release 的解析链不同），所以"release 能签、debug 反而不能"完全可能。

---

> 本文档已按当前源码逐项核验（核验日期：2026-09-23），覆盖多模块化重构后的全部演进：偏好存储 Room→DataStore 迁移、序列化 Moshi→kotlinx.serialization、自定义字体系统、设置流程从 ViewModel 状态机迁移至 Navigation 3 回退栈、主题家族目录单源化（`ThemeResolver.families`）、侧栏/底栏手势体系、设置流独立为 `feature:settings:{api,impl}` 模块（共用组件 `Button` / `Slider` 下沉到 `core:ui`），以及**接入 Google ADK for Kotlin：`core:agent` 以 `Model` 适配器同时支持 Chat Completions 与 Responses 两种 OpenAI 协议，并通过 `ProviderProbe` / `AgentChat` 两个不泄漏 ADK 类型的门面对外**。后续修改组件参数时，请同步更新第 13 节速查表。
