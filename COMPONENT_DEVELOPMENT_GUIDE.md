# Jasmine 多模块架构与核心功能开发完全指南

> 本文档详细剖析项目的多模块工程结构、MVVM 状态管理、Navigation 3 导航、Hilt 依赖注入，以及所有 UI 组件、多主题切换、推拽式侧边栏、打字机启动页的开发原理、核心实现与调参指南。
>
> **本版本已按当前源码逐项核验（核验日期：2026-10-01）**，所有参数、路径与功能描述均与当前源码一致。
>
> 核验方式是"每条技术断言回源码对一遍"：符号是否存在、常量值、界面结构、测试文件清单。
> **本文档描述的是当前系统**；如果发现哪一条与代码不符，请按代码改正本文档（而不是反过来）。

---

## 目录
1. [项目整体架构与模块划分](#1-项目整体架构与模块划分)
2. [MVVM 状态管理与数据流](#2-mvvm-状态管理与数据流)
3. [Hilt 依赖注入图谱](#3-hilt-依赖注入图谱)
4. [Navigation 3 导航系统](#4-navigation-3-导航系统)
5. [设计令牌系统（core:ui）](#5-设计令牌系统coreui)
6. [顶部导航栏 (TopNavBar) 与高度调控](#6-顶部导航栏-topnavbar-与高度调控)
7. [推拽式侧边栏 (Push Canvas Sidebar) 动画架构](#7-推拽式侧边栏-push-canvas-sidebar-动画架构)
8. [启动页打字机引擎 (SplashScreen)](#8-启动页打字机引擎-splashscreen)
9. [对话界面 (ChatScreen)](#9-对话界面-chatscreen)
10. [设置流程](#10-设置流程)
11. [共享工具与测试体系](#11-共享工具与测试体系)
12. [核心组件参数速查](#12-核心组件参数速查)
13. [已知局限与工程问题](#13-已知局限与工程问题)

---

## 1. 项目整体架构与模块划分

本应用基于 **Jetpack Compose**（无任何 XML 布局），采用 **多模块 + MVVM + UDF（单向数据流）** 架构。



### 1.1 模块结构与职责

```
jasmine/
├── app/                          # 应用壳：单 Activity + 导航组装 + 主题应用
│   ├── JasmineApplication   # @HiltAndroidApp 入口
│   └── MainActivity              # @AndroidEntryPoint：hiltViewModel() + JasmineTheme + MainNavHost
├── core/
│   ├── agent/                    # Rust 核心的 UniFFI 门面（Hilt 提供 AgentHandle / ProviderProbe / ConversationStore / AgentChat）
│   │   ├── AgentChat / ProviderProbe / ConversationStore   # 三个不泄漏核心类型的门面接口
│   │   ├── RustAgentChat / RustProviderProbe / RustConversationStore / RustHosts  # 核心绑定实现
│   │   ├── ConversationStore 订阅核心的「存储变更」推送 → 防抖后重读会话列表（不再轮询）
│   │   └── di/AgentModule        # @Provides 装配（AgentHandle 单例、AgentChat 非单例）
│   ├── data/                     # 数据层：仓库接口/实现 + Hilt 装配
│   │   ├── model/UserPreferences             # 领域模型（themeId / typographyChoice / colorMode / fontScale / activeCustomFontId / activeProviderId / activeModelId）
│   │   ├── datastore/UserPreferencesDataStore  # Preferences DataStore 读写（偏好唯一存储）
│   │   ├── repository/UserPreferencesRepository  # 偏好读写（DataStore 支撑）
│   │   ├── model/ProviderConfig          # 模型提供商配置（含 DeepSeek 预置 + ProviderApiType）
│   │   ├── datastore/ProviderDataStore   # 提供商列表 JSON 整体存取（独立 DataStore 文件）
│   │   ├── repository/ProviderRepository # 提供商增删改查（StateFlow 读 + 原子写）
│   │   ├── repository/CustomFontRepository       # 自定义字体：扫描/导入/下载（Mutex 串行 + 内存快照）
│   │   ├── repository/AppLanguageRepository      # 界面语言（AppCompat per-app locales 的唯一出口）
│   │   ├── model/ChatRole                # 消息作者枚举（用于历史重放与渲染）
│   │   ├── model/Conversation            # 会话元数据 + TranscriptMessage 领域模型（读自核心的 rollout）
│   │   ├── model/AgentSettings / AgentOutputLanguage  # Agent 行为设置（回复语言的取值与规则结构）
│   │   ├── datasource/FontRemoteDataSource       # 字体远端数据源（对接 core:network）
│   │   ├── manager/dispatcher/DispatcherManager  # 可注入协程调度器
│   │   └── di/DataModule                     # @Provides 装配
│   ├── markdown/                 # 增量 Markdown：native 引擎（ima incremark）+ Compose 块渲染
│   ├── navigation/               # Navigation 3 封装
│   │   └── AppNavigator                      # NavBackStack 包装（navigate/replace/goBack）
│   ├── network/                  # Retrofit/OkHttp/kotlinx.serialization（Hilt 提供，baseUrl 占位）
│   └── ui/                       # 设计系统
│       ├── theme/CssTokens       # CssVariables 模型、12 套预设、ThemeResolver（含 families 目录）
│       ├── theme/Theme           # JasmineTheme + LocalCssVariables + M3 ColorScheme 映射
│       ├── theme/Type            # AppTypography（Material3 Typography）+ LocalContentFontFamily
│       └── base/                 # BaseViewModel（UDF 三要素）+ EffectRunner（出站命令单消费者）+ EventsEffect（生命周期感知事件消费）
└── feature/
    ├── main/                   # 应用外壳：启动页 + 对话主页 + 导航组装
    │   ├── api/                 # 导航契约：@Serializable MainNavKey（Splash / Main）
    │   └── impl/                # UI + ViewModel
    │       ├── MainViewModel           # @HiltViewModel，外壳状态唯一源（主题 / 字体 / 侧栏）
    │       ├── MainNavHost             # NavDisplay + entryProvider（含设置流目的地）；全局托管 Toast 事件
    │       ├── MainScreen              # 推拽侧边栏 + 对话界面（没有底部导航栏）
    │       ├── chat/ChatViewModel      # @HiltViewModel，对话状态机（消息 / 输入 / 流式追加 / 模型选择）
    │       ├── chat/ChatScreen         # 对话界面外壳（消息列表 + 输入区 + 两个面板）
    │       ├── chat/ChatTranscript / ChatComposer / ChatModelSheet / ConversationChats
    │       │                           # 分别是转写渲染、输入区、模型与上下文面板、每会话状态表
    │       ├── chat/ChatModels / ChatContract / ChatRestore   # 状态与契约类型、启动恢复
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
 │              │  │  ├► core:data ──► core:network ──► Retrofit/OkHttp
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

- **State**：单一不可变 `MainState`（`stateFlow`），聚合主题/排版/字体（含自定义字体列表与下载进度）/侧栏等全部状态；
  `theme` 与 `activeContentFont` 为派生字段，每次更新自动重算。**没有"标签页"**（单顶层界面，见第 7.3 节），
  设置流的页面位置也**不在**此状态内——它由 Navigation 3 回退栈承载（见第 4 节）。
- **Action**：所有用户意图收敛为 `MainAction` sealed interface（侧栏 `SidebarOpened/Closed/Toggled`、
  `ThemeSelected`、`ColorModeChanged`、`TypographySelected`、`FontScaleSaved`、`FontDownloadClicked` 等），
  UI 一律 `trySendAction(...)` 发送。页面间导航不走 action——由 `AppNavigator` 直接操作回退栈。
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
- 导航界面状态（侧栏 `isSidebarOpen`）**不持久化**：它属于会话瞬态 UI 位置，持久化会导致恢复/写入反馈环（侧栏闪烁），并会在进程死亡后把用户带回旧页面而非启动页。设置流的页面位置由 Navigation 3 回退栈序列化、进程死亡后由导航库恢复，不属于偏好存储。

### 2.3 View 层观察方式

所有屏幕通过 `collectAsStateWithLifecycle()` 观察单一 `stateFlow`，回调发送 Action：

```kotlin
val state by viewModel.stateFlow.collectAsStateWithLifecycle()
// ...
onSidebarToggle = { viewModel.trySendAction(MainAction.SidebarToggled) }
```

一次性事件用 `EventsEffect(viewModel) { ... }` 消费（生命周期感知，防重复导航类问题）。

纯瞬态动画状态（按压缩放、入场触发、复制成功标记、渲染延迟模拟值）仍留在 Composable 本地 `remember`，符合 MVVM"UI 瞬态不下沉"原则。

### 2.4 ChatViewModel（feature:main:impl/chat）

对话是**独立于外壳的第二套 UDF 三要素**，与 `MainViewModel` 互不依赖（外壳不需要知道对话状态，反之亦然）：

- **State**：`ChatState`（消息列表 / 输入 / 是否发送中 / 供应商列表 / 当前供应商与模型 / 模型选择是否展开）；`activeProvider`、`activeModel`、`isReady` 为派生属性（`isReady` 要求供应商已有 API 密钥且已选模型）。
- **Action**：`ChatAction`（`InputChanged` / `SendClicked` / `NewConversationClicked` / `ModelPickerOpened` / `ModelPickerDismissed` / `ModelSelected`）+ `Internal.*`（供应商与偏好的回灌、流式分片、失败、回合结束）。
- **Event**：`ChatUiEvent`（`ShowToast` / `ShowError`）。**回合失败**直接渲染进消息气泡（比一闪而过的
  toast 更可读、可回溯），所以只有"动作失败、界面无处可放"这类才走事件通道（例如删除会话失败）。
- **派生**：`activeProvider` / `activeModel` / `isReady` 是 `ChatState` 的派生属性；每会话的状态另存一份
  （`ConversationChats`），界面那一片由 combine 派生，不手工投影（门禁 R9 守护）。


---

## 3. Hilt 依赖注入图谱

| 模块 | 注入内容 | 作用域 |
| :--- | :--- | :--- |
| `app` | `@HiltAndroidApp JasmineApplication`、`@AndroidEntryPoint MainActivity` | — |
| `core:network` | `NetworkModule`：`OkHttpClient`（debug BASIC 日志 / release 静默）、`Retrofit`（kotlinx.serialization 转换器）、`@BaseUrl`、`FontDownloadApi`（独立 Retrofit 实例，长超时裸流下载） | Singleton |
| `core:data` | `DataModule`：`@Provides` `DispatcherManager`、`UserPreferencesRepository`、`AppLanguageRepository`、`ProviderRepository`；`CustomFontRepository`（`@Singleton` 构造注入） | Singleton |
| `core:agent` | `AgentModule`：`@Singleton` `AgentHandle`（进程内唯一的核心句柄，同时承载会话存储变更推送）、`ProviderProbe`（`RustProviderProbe`）、`ConversationStore`（`RustConversationStore`）；`AgentChat`（`RustAgentChat`，**刻意非单例**：每条会话一个薄门面，隔离靠核心的按会话槽） | Singleton / 非单例 |
| `feature:main:impl` | `@HiltViewModel MainViewModel` / `ChatViewModel` / `UsageStatsViewModel`、`@Singleton CustomFontFamilyCache` | ViewModel / Singleton |
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
    @Serializable data object Main : MainNavKey                // 主界面（侧栏 + 对话界面）
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

主题家族识别与目录集中在 `ThemeResolver`（core:ui）：`familyOf(themeId)`（**只在 `families` 里按 `themeId` 精确匹配**，匹配不上返回 `null` —— 刻意**不做**"去掉 `-light`/`-dark` 后缀"的回退：不是每个 themeId 都长成 `{key}-light/-dark`，例如 `shadcn-zinc-dark` 属于 `shadcn`、`notion-warm-light` 属于 `notion`）、`families`（`PaletteFamily(key, displayName, light, dark)` 有序目录，设置页调色板列表使用）。**新增家族只需在 `families` 加一条**（设置页如需本地化名称/副标题再加一对字符串资源），选择器会自动出现新家族，无需再改屏幕代码。

---

## 6. 顶部导航栏 (TopNavBar) 与高度调控

位于 `core/ui/components/TopNavBar.kt`（`ProductionTopNavBar`）。

### 6.1 结构与高度

```kotlin
Column(modifier = modifier.fillMaxWidth().background(currentTheme.background).statusBarsPadding()) {
    Row(modifier = Modifier.fillMaxWidth().height(TopNavBarHeight).padding(horizontal = 10.dp)) {
        // 仅侧边栏开关(共用的 Button 撑起 48dp 最小触摸区 + 28dp 纯 Menu 图标，无底色/边框/按压水波纹，testTag = "top_nav_sidebar_btn")
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
3. 打开方式：顶栏菜单按钮，或**从屏幕左侧 2/3 区域内起手横拖**（`SidebarEdgeZoneFraction = 2f/3f`）。注意这个"边缘区"是**按比例**算的、不是固定 dp：收起状态下只有起手点落在这块里的拖动才开抽屉（内容区自己的横向滚动因此不受影响），展开或动画中则从任意位置起手都能收回。
4. 收回机制（3 种）：① **系统返回键/手势**（`BackHandler(enabled = isSidebarOpen)` 优先拦截，只收侧边栏不退出页面）；② **点击侧边栏以外的区域**（全透明拦截层，`testTag = "sidebar_outside_dismiss"`，无视觉遮罩；拦截层通过 `padding(start = SidebarWidth)` 在几何上**只覆盖侧边栏右侧区域**——若做成全屏，点在侧边栏非交互区域的事件未被消费时会穿透到拦截层导致误收回）；③ **点击底部设置入口**（打开设置菜单页后自动收回）。层级顺序：主画布（底）→ 外部点击拦截层（中，仅展开时存在）→ 侧边栏（顶）。

### 7.3 侧边栏内容（AppSidebarContent）

极简两段式结构：**顶部为空**（原品牌工作区头部——"J" 头像、"Jasmine Studio"、PRO 徽标与 "Design Systems Lab" 副标题——已整体移除，无关闭按钮），中间弹性留白，**底部固定设置入口**（右对齐的 32dp 纯齿轮图标按钮，点击 → `MainAction.SidebarClosed` + `navigator.navigate(MainNavKey.SettingsMenu)` 打开设置菜单目的地——该入口从顶栏迁移而来）。原导航组、调色板快切、快捷工具与引擎页脚均已移除；**应用只有一个顶层界面，没有底部导航栏**（类注释见 `MainScreen.kt`：单顶层界面再加一条 tab 栏只会吃掉竖向空间，还牵进一整套 IME/inset 协调——那条栏得为键盘让位，结果正在输入的输入区跟着跳），主题切换仅在设置页完成。

---

## 8. 启动页打字机引擎 (SplashScreen)

`screens/SplashScreen.kt`，纯无状态组件（`currentTheme + onFinish`），由 Nav3 的 Splash 键承载。

核心时序（已核验）：
- **光标闪烁**：480ms `LinearEasing` 无限往复。
- **光环呼吸**：1800ms `FastOutSlowInEasing`；**自转渐变环**：12000ms `LinearEasing` 旋转。
- **打字节奏**：起始延迟 350ms → 逐字 `typingDelay` → 行间停顿 400ms → 末句停顿 850ms 后触发 `onFinish`（目的地切换由 NavDisplay 默认过渡处理，无额外淡出动画）。
- 阶段进度条与 Skip/Enter 双入口均可提前结束。

---

## 9. 对话界面 (ChatScreen)

`chat/ChatScreen.kt` 是 Main 目的地唯一的内容区：**消息列表 + 输入区（+ 按需出现的面板）**，由 `chat/ChatViewModel` 驱动（UDF，状态为 `ChatState`）。文件已按职责拆开（E3）：`ChatScreen.kt` 只留外壳与消息列表，转写渲染在 `ChatTranscript.kt`、输入区在 `ChatComposer.kt`、模型与上下文面板在 `ChatModelSheet.kt`。

### 9.1 界面结构

- **外壳**（`ChatScreen`）：`MessageList(weight(1f))` + 底部那一条 —— 有未答提问时是 `PromptPanel`（输入区让位：这一轮正停着等答案，再发一条消息也无处可去），否则是 `Composer`；`ModelSheet` / `ContextUsageSheet` 叠在 `Box` 上按开关出现。
- **没有顶栏了**：原来那行"当前模型 / 历史对话 / 新建对话"（48dp）已整体删除 —— 历史对话与新建对话搬进侧边栏，当前模型在输入区左下角有入口，这 48dp 还给了聊天区。
- **MessageList**（`LazyColumn`）：随消息数与流式文本增长自动贴底。跟随尾部的判据是"用户没自己拖过，或此刻确实在底部"，拖动由手势（`PointerEventPass.Initial`）判定 —— 只看滚动位置的话，"回复在变长"和"用户滑走了"分不开。
- **气泡**：用户侧右对齐、`primary` 底 + `primaryForeground` 字；助手侧左对齐、`card` 底 + 1dp 描边；**失败消息**用 `subtleSurface` 底 + `mutedForeground` 字，正文里带着原始原因。宽度上限 `0.85`（`fillMaxWidth(fraction)` + `wrapContentWidth`），靠不对称读出"对话"感。一轮里的工具调用是一张可展开的工具卡，正文 ↔ 工具卡之间用更小的行距。
- **输入区**（`ChatComposer`）：`BasicTextField` 多行（回车换行，`ImeAction.Default`）+ 圆形发送键（发送中变转圈）+ 左下角的模型 / 上下文用量 / 推理档位入口。
- **模型选择**：复用 `core:ui` 的 `BottomSheet`，按供应商分组列出**已配置的模型**（无模型的供应商不出现，空态指向「模型提供商」）；当前项打勾。
- **未就绪**（`ChatState.isReady == false`，即"没选模型"或"所选供应商没有 API 密钥"）：**不再有整屏引导页**，界面照常渲染，只是发送被静默忽略 —— `handleSendClicked` 在缺供应商 / 缺模型 / 密钥为空时直接返回（见 `ChatViewModel`）。`isReady` 现在只是状态上的一个派生属性。

### 9.2 会话与流式

- 每个分片经 `ChatAction.Internal.ReplyChunk` 回灌 action 管道，**状态变更仍全部同步发生在 `handleAction` 内**（与字体下载进度同一模式）。
- 流式追加只更新"当前正在流式的那条助手消息"（按 id 定位），因此 `LazyColumn` 的 key 唯一、历史消息不重组。
- `ChatViewModel` 由 `Main` 条目作用域持有（`rememberViewModelStoreNavEntryDecorator()`），且**在 `MainScreen` 的内容区收集状态**——流式分片只重组对话界面，不触发 NavHost 全树重组。

### 9.3 持久化与历史对话

**没有数据库**：会话整体落盘在 Rust 核心的 rollout 里 —— 每会话一个**只追加 JSONL**（`files/sessions/<年>/<月>/<日>/rollout-*.jsonl`，首行是会话元信息与标题/provider/model），一行一条事件。（Room / `core:database` 与 schema 导出都已删除，设备上 `databases/` 目录不存在；见第 13 节第 8 条。）

- **写入时机**：整份转写由**核心**在回合内写（`send` / `respond_to_prompts` / `persist_interrupted_reply` 三处落盘）。平台只负责**首次发送时让核心开会话**（`create_conversation`，因此不会留下空会话），`title` 取首条用户消息（截断 60 字符，`TITLE_MAX_LENGTH`）。内存里的消息始终是实时视图。
- **恢复**：启动时读最近更新的会话，把 transcript 读回来填进界面（`latestConversation` + `messagesOf`，正文解析走 `parseDispatcher`，主线程不跑 JNI）。
- **历史列表在侧边栏**：不是顶栏时钟图标那个 `BottomSheet`。侧边栏列"新建对话 + 全部会话"，每行是标题 + **最后一条消息的时间**（会话记录里的 `updatedAt`；以前显示模型名，但列表里的模型名绝大多数都一样，看不出哪条是刚聊的），可切换或删除。列表更新由**核心的存储变更推送**驱动（防抖后重读），不再靠"回合结束手动 refresh"。
- **删除**：走核心的 `delete_conversation`（一条会话一个文件，删掉即整条消失，不需要外键级联）。
- **失败可观察**：开会话失败会让这一轮以**失败提示**收尾（`ensureConversation` 失败 → 这一轮报 `TurnFailed`），不是静默吞掉。

---

## 10. 设置流程

> 本章内容全部位于 **`feature:settings:impl`**（导航契约 `SettingsNavKey` 在 `feature:settings:api`）；
> 目的地由外壳 `MainNavHost` 的 `NavDisplay` 组装。

> 原先的「排印工作室（TypeStudioScreen）」与「令牌面板（TokensScreen）」两个页面，
> **已连同 `NavigationTab` 这套标签枚举（`TYPOGRAPHY` / `TOKENS` / `SETTINGS` / `CHAT`）以及底部导航栏整体删除**
> —— 应用现在只有一个顶层界面。主题与字体能力并未删除，仍完整保留在下面的设置流中。

### 10.1 目的地与入口

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
- **字体页（FontScreen）**：3 排版引擎（Serif/Sans/Mono，行内只有 "Aa" 样例 + 引擎名，无风格描述副标题）、字号入口、自定义字体管理（见 10.2）。
- **字号页（FontSizeScreen）**：全局字体缩放滑块 + 实时预览，保存 → `MainAction.FontScaleSaved`（持久化；`MainActivity` 通过 `LocalDensity` 的 `fontScale` 全局生效）。这个滑块是**应用内**的额外缩放，生效值是 **系统无障碍字号 × 应用内滑块**（`MainActivity`：`baseDensity.fontScale * state.fontScale`，所以 **100% = 跟随系统**，系统调大字号应用也跟着大）；本页自己的预览要显示真实效果，因此用 `LocalConfiguration.current.fontScale` 重新取系统值再乘 entry/draft。
- **语言页（LanguageScreen）**：跟随系统 / English / 中文，经 `AppCompatDelegate.setApplicationLocales` 持久化并即时重建 Activity（`locales_config.xml` 声明 en、zh-CN，支持 Android 13+ 系统级应用语言列表）。
- **返回**：系统返回键/手势与顶栏返回键统一走 `navigator.goBack()` 逐级弹栈（FontSize→Font→Menu→Main），由 NavDisplay 的 `onBack` 接管，天然支持预测返回与进程死亡恢复。设置目的地不经过 `MainScreen`，因此**天然不渲染侧栏**，页面视觉只保留全局顶栏 + 内容区。

### 10.2 自定义字体系统（core:data + impl/fonts/）

- **预设库（PresetFontCatalog，core:data/model）**：3 款字体（Source Han Serif SC / LXGW WenKai / JetBrains Mono）托管于 GitHub Releases（`releases/latest/download/<file>` HTTPS 直链），每条记录含 `sha256` 校验和。
- **下载（CustomFontRepository.downloadPreset，core:data）**：IO 调度器流式写入 `.part` 临时文件，完成后做 **SHA-256 校验**，不匹配即删除拒绝安装；校验通过原子改名入库。下载进度经 `downloadProgress: StateFlow` 实时回流 UI。
- **导入（importFont）**：支持用户选择本地 .ttf/.otf，文件名规范化（非法字符→`_`）并以 `upload_` 前缀落盘，自动去重加序号。
- **热路径零磁盘 IO**：`installedIds` 内存快照（AtomicReference）+ `fontFamilyCache`（ConcurrentHashMap，impl/fonts/ 的 CustomFontFamilyCache）回答成员与 FontFamily 查询；字体目录经一次性 `ensureFontsDir`（AtomicBoolean）创建，`installedVersion` 变更触发重新扫描自愈。
- 选中自定义字体会清空系统排版引擎选择（互斥），反之亦然；选择持久化于 `activeCustomFontId`。

---

## 11. 共享工具与测试体系

### 11.1 测试栈（JVM 单测共 133 例）

| 测试 | 内容 | 说明 |
| :--- | :--- | :--- |
| `ExampleUnitTest` / `ExampleRobolectricTest` / `MainScreenshotTest`（`app`，1 / 1 / 1 例） | 模板级 2+2、读 `app_name` 资源、Roborazzi 渲染首页 | Robolectric + Roborazzi |
| `MvvmUdfGateTest`（`feature:main:impl`，10 例） | 源码级 UDF 门禁：单一状态写入点、异步结果经 `Internal` action 回流、影子状态只由同步 handler 写、会话投影必须派生、View 层不碰平台 | 纯文本比对，非运行期断言 |
| `ChatViewModelTest`（`feature:main:impl`，49 例） | 对话状态机：发送 / 流式追加 / 贴块握手 / 失败 / 中断与继续 / 开会话 / 恢复并重放 / 切换与回收（keep-warm）/ 乐观写的身份守卫回滚 | 假仓库 + 假 `AgentChat`，不触网 |
| `UsageStatsViewModelTest`（`feature:main:impl`，2 例） | 用量页：按天聚合、空态 | 假仓库 |
| `MainViewModelTest`（`app`，13 例） | 外壳：偏好回灌（侧栏是瞬态、不跟着回灌）、侧栏 toggle 不碰偏好、**六条乐观写的身份守卫回滚**与迟到失败不顶掉新选择、字体删除与下载的提示、主题派生 | Robolectric（要真 `Context`）+ 真字体仓库配假下载接口 |
| `ProviderViewModelRollbackTest`（`feature:provider:impl`，3 例） | 乐观写的失败回滚：删除按原位插回、保存恢复快照并重开草稿、迟到的失败不顶掉新草稿 | 假仓库注入失败 |
| `LanguageViewModelTest`（`feature:settings:impl`，3 例） | 语言页：首屏值经 action 落地、选择即应用、配置换掉回到平台权威值 | 假 `AppLanguageRepository` |
| `BaseViewModelTest` / `EffectRunnerTest` / `EventsEffectTest` / `ReasoningEffortTest`（`core:ui`，3 / 4 / 1 / 6 例） | 动作队列 FIFO、销毁时关通道；**出站命令单消费者 / 成败都回流 / 取消穿透不被当成失败 / 作用域取消后 send 是 no-op**；事件在 RESUMED 之后才投递；推理档位只列目录声明的档 | 假协程调度器 / 假生命周期 |
| `EventSinkTest`（`core:agent`，4 例） | 事件汇：有界通道 + 文本合并、终态事件绝不丢、通道关闭后不抛 | |
| `CoreEventFlowTest`（`core:agent`，3 例） | `coreEventFlow`：任何异常都收成一个终态失败，`finish()` 一定执行（含 5s 超时护栏） | |
| `RustAgentChatMappingTest`（`core:agent`，8 例） | 跨边界两张表：九个核心事件逐个映射、回合终态判定（用量不是终态）、四型失败分型与"猜不出来就不猜"、用量与构成 | 不需要真的核心句柄 |
| `RustConversationReadTest`（`core:agent`，4 例） | 会话列表**读失败保留上一次快照**、只把原因交出去；没有 message 的异常也要有原因 | 同上（判定已提成纯函数） |
| `ProviderReadTest`（`core:data`，4 例） | 供应商配置读路径：没存过 / 存过能解 / 存过解不出来（要上报）/ 形状不对，四条分岔 | 纯 JVM |
| `IncrementalBlocksTest`（`core:markdown`，6 例） | 增量解析：`IncrementalMarkdownParser.apply` 就地改（恢复重放用）、`IncrementalMarkdownDocument.applied` 不改入参（流式追加用）、三分支更新、两条路径结果一致 | 该模块唯一的测试源集，纯 JVM |
| `ProviderBackRuleTest`（`feature:provider:impl`，5 例） | `canNavigateBack`（= 编辑态已关）对三个编辑入口都成立、一层一层关、取消/保存后复位 | 假仓库 + 假探针 |
| `ProviderScreenBackTest`（`app`，2 例） | 编辑态按系统返回 → `CancelClicked`；列表态不拦 | Robolectric + Compose 测试规则 |

- Robolectric 基线 **SDK 36**（`app/src/test/resources/robolectric.properties`），**要求 JDK 21**（SDK 36 沙盒硬性要求；SDK 37 需 Robolectric 4.17-beta，暂不采用）。
- 截图基准图生成：`gradle :app:testDebugUnitTest -Proborazzi.test.record=true`。
- Rust 侧：`cargo test --workspace`（当前 **188 passed**）、`cargo clippy --workspace --all-targets` 无告警、`cargo fmt --check` 一致。

### 11.2 构建验证命令

```
gradle :app:compileDebugKotlin                # 全模块编译 + KSP（Hilt）
gradle :app:assembleDebug                     # 完整打包（需根目录 debug.keystore）
gradle :app:testDebugUnitTest                 # 全部模块的 JVM 单测 + 截图测试
gradle :feature:main:impl:testDebugUnitTest   # 对话状态机 + UDF 门禁
gradle :core:ui:testDebugUnitTest             # BaseViewModel / EffectRunner / EventsEffect
gradle :core:agent:testDebugUnitTest          # 事件汇、coreEventFlow、跨边界映射
gradle :core:data:testDebugUnitTest           # 供应商配置读路径的判定
gradle :feature:provider:impl:testDebugUnitTest  # 供应商 CRUD + 回滚
gradle :feature:settings:impl:testDebugUnitTest  # 语言页
```

---

## 12. 核心组件参数速查

| 参数 | 值 | 位置 |
| :--- | :--- | :--- |
| 顶栏内容行高 | 47dp（`TopNavBarHeight`） | TopNavBar |
| 侧边栏槽宽 / 内容宽 | 295dp / 295dp（同一常量 `SidebarWidth`） | SidebarDrawer |
| 侧边栏边缘滑出区 | 屏宽的 2/3（`SidebarEdgeZoneFraction = 2f/3f`，收紧起手判定用） | SidebarDrawer |
| 推拽 / 展开动画 | 320ms（`SidebarDrawerAnimMillis`），CubicBezier(0.16,1,0.3,1) | SidebarDrawer |
| 背景色过渡 | 280ms FastOutSlowIn | MainScreen |
| 对话气泡最大宽度 | 0.85 页宽（`ChatBubbleMaxWidthFraction`） | ChatTranscript |
| 对话发送键 | 26dp 方形圆角（`ChatSendButtonSize`），图标 14dp（`ChatSendIconSize`）/ 转圈 16dp（`ChatSendSpinnerSize`） | ChatComposer |
| 对话输入框 | `maxLines = 5`（实测只看得全 4 行），回车换行（`ImeAction.Default`，不发送） | ChatComposer |
| 模型 / 上下文面板列表最大高 | 380dp（`ChatPickerListMaxHeight`） | ChatModelSheet |
| 会话标题长度 | 首条用户消息截断 60 字符（`TITLE_MAX_LENGTH`） | ChatViewModel |
| 会话在内存里保热时长 | 30s（`KEEP_WARM_MS`；离开会话或回合收尾起算，到期后条目被回收） | ConversationChats |
| 打字机 | 起始 350ms，逐字 28ms（末句 42ms），行间 400ms，收尾 850ms | SplashScreen |
| 光标 / 光环 / 自转环 | 480ms / 1800ms / 12000ms | SplashScreen |
| 偏好存储 | Preferences DataStore（`user_preferences`：主题 / 排版 / 明暗 / 字号 / 自定义字体 / **activeProviderId / activeModelId**） | core:data |
| 会话落盘 | 每会话一个**只追加 JSONL**（`files/sessions/<年>/<月>/<日>/rollout-*.jsonl`，首行是会话元信息与标题/provider/model） | rust/rollout（经 `core:agent` 的 `ConversationStore`） |
| 字体下载源 | GitHub Releases 直链 + SHA-256 校验 | feature:main:impl/fonts |

---

## 13. 已知局限与工程问题

4. **`tools[].type` 的坑（已修，有回归测试）**：`openAiJson` 关闭了 `encodeDefaults`，因此**带默认值的必填字段不会被序列化**。原先 `ChatTool.type` 的默认值恰是 `"function"`，导致带工具的请求会漏掉 `type` 而被供应商拒绝；现改为必填无默认值（Responses 的 `parameters` / `strict` 同理）。
7. **API Key 存在设备上**：`ProviderConfig.apiKey` 存于 Preferences DataStore 并由设备直连供应商。官方 Android 指南明确不建议在客户端内嵌密钥（建议自建后端或 Firebase AI Logic）。当前定位是"用户自备密钥的个人工具"，若要上架发布需改为代理方案。
8. **会话落盘已整体迁到 Rust 核心的 rollout（Room 下线）**：演进路径是「偏好表 Room→DataStore」→「显式聊天记录 Room v5（`conversations` / `messages`）」→「**会话整体迁到 `rust/rollout`**，每会话一个只追加 JSONL，首行是会话元信息」。`core:database` 模块、Room 依赖与 schema 导出均已删除（设备上 `databases/` 目录不存在）。**取舍**：JSONL 只追加、无索引、无查询语言 —— 列表靠扫目录 + 读每个文件「最后一行时间戳」得到 `updatedAt`（见 `rust/rollout/src/list.rs`），转写靠整文件顺序读。会话数量级上去以后需要引入索引或分片，这是当前实现已知的伸缩边界。
9. **家族文案为可选本地化**：设置页族名经 `SettingsScreen.paletteNameRes` 按家族 key 查本地化资源；未配置的新家族自动回退到该家族自身的 `displayName`（英文），不会错标为其它家族；需要本地化时补一条字符串资源即可。（描述副标题已整体移除，`CssVariables` 不再携带 `description` 字段。）
10. **截图基准默认不校验**：`app/src/test/screenshots/chat.png` 已提交（渲染的是带样例对话的对话界面，而非空表面）。默认 `testDebugUnitTest` 下 Roborazzi 未激活任何模式（record/verify/compare 均未开），`captureRoboImage` 空转通过、不做校验——CI 绿灯对 UI 回归没有保护。重新生成基准用 `gradle :app:testDebugUnitTest -Proborazzi.test.record=true`（**PowerShell 下 `-P` 参数会被吞掉，需加引号：`'-Proborazzi.test.record=true'`**），CI 校验用 `-Proborazzi.test.verify=true`。真正有断言价值的是 `core:agent` / `feature:main:impl` / `feature:provider:impl` 的纯逻辑单测。
13. **签名与打包环境**：release 的密钥库解析顺序为 `KEYSTORE_PATH`（环境变量，CI / 显式覆盖优先）→ `${rootDir}/my-upload-key.jks`；别名固定为 `upload`，密码来自 `STORE_PASSWORD` / `KEY_PASSWORD`。工作区已放置一枚**测试用**密钥库 `my-upload-key.jks`（`CN=Jasmine Test Release`，git-ignored），可用于安装测试但**不能用于发布**（Play 拒绝 debug 级/测试身份，且换密钥必须先卸载）。
    **排查要点**：构建**不会回显**实际使用的密钥库，签名对不对只能用 `apksigner verify --print-certs` 验证。本机就曾因为持久化的 `KEYSTORE_PATH` 指向另一目录的 debug 密钥库，导致 release 包被签成 `CN=Android Debug` —— 而 `assembleDebug` 固定读 `${rootDir}/debug.keystore`（与 release 的解析链不同），所以"release 能签、debug 反而不能"完全可能。

---

> 本文档已按当前源码逐项核验（核验日期：2026-10-01），覆盖多模块化重构后的全部演进：偏好存储 Room→DataStore 迁移、**会话落盘 Room→`rust/rollout` 迁移（`core:database` 整体下线）**、序列化 Moshi→kotlinx.serialization、自定义字体系统、设置流程从 ViewModel 状态机迁移至 Navigation 3 回退栈、主题家族目录单源化（`ThemeResolver.families`）、侧栏推拽手势体系与**底部导航栏整体移除（单顶层界面）**、设置流独立为 `feature:settings:{api,impl}` 模块（共用组件 `Button` / `Slider` 下沉到 `core:ui`）、**`core:agent` 改为 Rust 核心的 UniFFI 门面（会话、模型调用、工具都在核心侧）**，以及 UDF 加固（出站命令统一走 `EffectRunner`、会话投影改为派生、源码级门禁 `MvvmUdfGateTest`）。后续修改组件参数时，请同步更新第 12 节速查表。
