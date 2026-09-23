# Jasmine 多模块架构与核心功能开发完全指南

> 本文档详细剖析项目的多模块工程结构、MVVM 状态管理、Navigation 3 导航、Hilt 依赖注入，以及所有 UI 组件、多主题切换、推拽式侧边栏、打字机启动页的开发原理、核心实现与调参指南。
>
> **本版本已按当前源码逐项核验（核验日期：2026-09-13）**，所有参数、路径与功能描述均与当前源码一致。

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
10. [主画布工作台 (CanvasScreen)](#10-主画布工作台-canvasscreen)
11. [设置流程](#11-设置流程)
12. [共享工具与测试体系](#12-共享工具与测试体系)
13. [核心组件参数速查](#13-核心组件参数速查)
14. [已知局限与工程问题](#14-已知局限与工程问题)

---

## 1. 项目整体架构与模块划分

本应用基于 **Jetpack Compose**（无任何 XML 布局），采用 **多模块 + MVVM + UDF（单向数据流）** 架构。

构建基线：AGP 9.4.1、Kotlin 2.4.20、Compose BOM 2026.09.00（Compose 1.12.1 / Material 3 1.4.0）、Navigation 3 1.1.7、Hilt 2.60.1、Lifecycle 2.11.0、Room 2.7.0、compileSdk 37、minSdk 24 / targetSdk 37、JDK 21。

### 1.1 模块结构与职责

```
jasmine/
├── app/                          # 应用壳：单 Activity + 导航组装 + 主题应用
│   ├── JasmineApplication   # @HiltAndroidApp 入口
│   └── MainActivity              # @AndroidEntryPoint：hiltViewModel() + JasmineTheme + MainNavHost
├── core/
│   ├── data/                     # 数据层：仓库接口/实现 + Hilt 装配
│   │   ├── model/UserPreferences             # 领域模型（themeId / typographyChoice / colorMode / fontScale / activeCustomFontId）
│   │   ├── datastore/UserPreferencesDataStore  # Preferences DataStore 读写（偏好唯一存储）
│   │   ├── repository/UserPreferencesRepository  # 偏好读写（DataStore 支撑）
│   │   ├── model/ProviderConfig          # 模型提供商配置（含 DeepSeek 预置 + ProviderApiType）
│   │   ├── datastore/ProviderDataStore   # 提供商列表 JSON 整体存取（独立 DataStore 文件）
│   │   ├── repository/ProviderRepository # 提供商增删改查（StateFlow 读 + 原子写）
│   │   ├── datasource/FontRemoteDataSource       # 字体远端数据源（对接 core:network）
│   │   ├── manager/dispatcher/DispatcherManager  # 可注入协程调度器
│   │   └── di/DataModule                     # @Provides 装配
│   ├── database/                 # Room：预留给结构化本地数据（仅保留 legacy 实体，无 DAO）
│   │   ├── UserPreferencesEntity / AppDatabase
│   │   └── di/DatabaseModule                 # @Provides 数据库（含 1→4 迁移链）
│   ├── navigation/               # Navigation 3 封装
│   │   └── AppNavigator                      # NavBackStack 包装（navigate/replace/goBack）
│   ├── network/                  # Retrofit/OkHttp/kotlinx.serialization（Hilt 提供，baseUrl 占位）
│   └── ui/                       # 设计系统
│       ├── theme/CssTokens       # CssVariables 模型、12 套预设、ThemeResolver（含 families 目录）
│       ├── theme/Theme           # JasmineTheme + LocalCssVariables + M3 ColorScheme 映射
│       ├── theme/Type            # AppTypography（Material3 Typography）+ LocalContentFontFamily
│       └── base/                 # BaseViewModel（UDF 三要素）+ EventsEffect（生命周期感知事件消费）
└── feature/
    ├── main/                   # 应用外壳：启动页 + 主页 + 导航组装
    │   ├── api/                 # 导航契约：@Serializable MainNavKey（Splash / Main）
    │   └── impl/                # UI + ViewModel
    │       ├── MainViewModel           # @HiltViewModel，功能唯一状态源
    │       ├── MainNavHost             # NavDisplay + entryProvider（含设置流目的地）；全局托管 Toast 事件
    │       ├── MainScreen               # 推拽侧边栏 + 单标签（CANVAS）Scaffold
    │       ├── fonts/                   # CustomFontFamilyCache（FontFamily 内存缓存，主线程零磁盘 IO）
    │       └── screens/{Splash,Canvas}Screen   # chrome 组件已统一下沉 core/ui/components/
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
 │              │  │  ├► core:data ──► core:database ──► Room（预留，无 DAO）
 │              │  │  │            └─► core:network ──► Retrofit/OkHttp
 │              │  │  ├► core:ui
 │              │  │  ├► feature:settings:impl ──► feature:settings:api
 │              │  │  │                        └──► core:ui / core:data
 │              │  │  └► feature:provider:impl ──► feature:provider:api
 │              │  │                           └──► core:ui / core:data
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

---

## 3. Hilt 依赖注入图谱

| 模块 | 注入内容 | 作用域 |
| :--- | :--- | :--- |
| `app` | `@HiltAndroidApp JasmineApplication`、`@AndroidEntryPoint MainActivity` | — |
| `core:database` | `DatabaseModule`：`AppDatabase`（Room.databaseBuilder，`jasmine.db`，含 1→4 迁移链；当前无 DAO，为结构化数据预留） | Singleton |
| `core:network` | `NetworkModule`：`OkHttpClient`（debug BASIC 日志 / release 静默）、`Retrofit`（kotlinx.serialization 转换器）、`@BaseUrl`、`FontDownloadApi`（独立 Retrofit 实例，长超时裸流下载） | Singleton |
| `core:data` | `DataModule`：`@Provides` DispatcherManager 与 `UserPreferencesRepository`；`CustomFontRepository`（`@Singleton` 构造注入） | Singleton |
| `feature:main:impl` | `@HiltViewModel MainViewModel`、@Singleton `CustomFontRepository`、@Singleton `CustomFontFamilyCache` | ViewModel / Singleton |

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
                onOpenSettings = { /* 收回侧边栏 + */ navigator.navigate(MainNavKey.SettingsMenu) })
        }
        // 设置流目的地共用私有脚手架 SettingsPage（背景过渡 + 顶栏子页形态）：
        entry<MainNavKey.SettingsMenu>       { SettingsPage("Settings") { SettingsMenuScreen(...) } }
        entry<MainNavKey.AppearanceSettings> { SettingsPage("Appearance & Themes") { SettingsScreen(...) } }
        entry<MainNavKey.LanguageSettings>   { SettingsPage("Language") { LanguageScreen(...) } }
        entry<MainNavKey.FontSettings>       { SettingsPage("Font") { FontScreen(...) } }
        entry<MainNavKey.FontSizeSettings>   { SettingsPage("Font Size") { FontSizeScreen(...) } }
    }
)
```

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

`core/ui/components/BottomNavBar.kt`（`ProductionBottomNavBar`）：**目前只有 CANVAS 一个标签**（`NavigationTab` 枚举仅剩 `CANVAS`；原 `TYPOGRAPHY` / `TOKENS` / `SETTINGS` 及其屏幕已删除，设置功能整体在侧边栏触发的设置流程中）。激活态为微胶囊背景 + 颜色过渡，`.navigationBarsPadding()` 避让手势条。内容区仍由底栏自身托管为可滑动分页（320ms 分页动画；快滑阈值 180px/s，慢拖阈值 28% 页宽），单标签下不会产生实际翻页；侧栏展开时滑动禁用，左缘 32dp（`SidebarEdgeZone`）保留给侧栏滑出。**底部导航栏只存在于 Main 目的地**——设置菜单页与各设置子页是回退栈上的独立目的地，天然不渲染底栏。

---

## 9. 启动页打字机引擎 (SplashScreen)

`screens/SplashScreen.kt`，纯无状态组件（`currentTheme + onFinish`），由 Nav3 的 Splash 键承载。

核心时序（已核验）：
- **光标闪烁**：480ms `LinearEasing` 无限往复。
- **光环呼吸**：1800ms `FastOutSlowInEasing`；**自转渐变环**：12000ms `LinearEasing` 旋转。
- **打字节奏**：起始延迟 350ms → 逐字 `typingDelay` → 行间停顿 400ms → 末句停顿 850ms 后触发 `onFinish`（目的地切换由 NavDisplay 默认过渡处理，无额外淡出动画）。
- 阶段进度条与 Skip/Enter 双入口均可提前结束。

---

## 10. 主画布工作台 (CanvasScreen)

`screens/CanvasScreen.kt` 目前是**一张刻意留空的表面**（仅 `Box(Modifier.fillMaxSize())`），用于承接底部导航栏的 CANVAS 标签页。

原先位于此处的遥测头、预设快切条、英雄卡片（策展语录循环）、自定义问候编辑器、排版引擎选择器与 CSS Variables 速览卡**均已从主页移除**；对应的主题切换与字体切换能力**并未删除**，而是整体迁移到设置流（AppearanceSettings / FontSettings），因此 `ThemeResolver`、`AppTypographyChoice`（现位于 `feature:settings:impl`）等共享实现仍在被使用。

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
- **模型提供商页（feature:provider，ProviderScreen + ProviderViewModel）**：独立 UDF 特性模块。列表模式 = 分组卡片列出全部供应商（**DeepSeek 预置**在首位，可编辑不可删除；用户可自由添加 OpenAI 协议兼容供应商）+「添加供应商」入口行；编辑模式 = 名称 / 接口地址 / API 密钥三个主题化输入框 + API 类型二选一卡片（**Chat Completions / Responses API**）+ **模型区** + 取消/保存。
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
| `MainScreenshotTest` | Roborazzi 渲染 `CanvasScreen` | 验证首页 UI 可组合渲染 |

- Robolectric 基线 **SDK 36**（`app/src/test/resources/robolectric.properties`），**要求 JDK 21**（SDK 36 沙盒硬性要求；SDK 37 需 Robolectric 4.17-beta，暂不采用）。
- 截图基准图生成：`gradle :app:testDebugUnitTest -Proborazzi.test.record=true`。

### 12.2 构建验证命令

```
gradle :app:compileDebugKotlin    # 全模块编译 + KSP（Room/Hilt）
gradle :app:assembleDebug         # 完整打包（需根目录 debug.keystore）
gradle :app:testDebugUnitTest     # 单元测试 + 截图测试
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
| 打字机 | 起始 350ms，逐字 28ms（末句 42ms），行间 400ms，收尾 850ms | SplashScreen |
| 光标 / 光环 / 自转环 | 480ms / 1800ms / 12000ms | SplashScreen |
| 偏好存储 | Preferences DataStore（`user_preferences`） | core:data |
| 结构化数据库 | `jasmine.db`（预留，含 1→4 迁移链，无 DAO） | core:database |
| 字体下载源 | GitHub Releases 直链 + SHA-256 校验 | feature:main:impl/fonts |

---

## 14. 已知局限与工程问题

1. **网络层仅用于字体下载**：`core:network` 栈已就绪（Retrofit + kotlinx.serialization + Hilt），但当前只有 `FontDownloadApi` 一个 service，实际指向 GitHub Releases 的绝对 URL；baseUrl 仍是 `https://api.example.com/` 占位，接真实后端需新增 service 接口并替换 `NetworkModule.provideBaseUrl()`。
2. **Room 空转**：`core:database` 仅为满足 Room 至少一个实体的要求保留 legacy 表，无 DAO；该模块是项目硬性保留的预留位（曾摘除后被回退），接结构化数据时加 `@Entity` + `@Dao` 并递增版本即可。
3. **家族文案为可选本地化**：设置页族名经 `SettingsScreen.paletteNameRes` 按家族 key 查本地化资源；未配置的新家族自动回退到该家族自身的 `displayName`（英文），不会错标为其它家族；需要本地化时补一条字符串资源即可。（描述副标题已整体移除，`CssVariables` 不再携带 `description` 字段。）
4. **截图基准已入库**：`app/src/test/screenshots/canvas.png` 已提交。默认 `testDebugUnitTest` 下 Roborazzi 未激活任何模式（record/verify/compare 均未开），`captureRoboImage` 空转通过、不做校验；重新生成基准用 `gradle :app:testDebugUnitTest -Proborazzi.test.record=true`，CI 校验用 `-Proborazzi.test.verify=true`。
5. **debug 密钥库**：`debug.keystore` 被 gitignore，新环境需按 README 用 keytool 生成。
6. **release 签名依赖环境**：`KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` 三个环境变量（或根目录 `my-upload-key.jks`）必须存在，否则 `assembleRelease` 失败；CI/新机器需先注入。

---

> 本文档已按当前源码逐项核验（核验日期：2026-09-13），覆盖多模块化重构后的全部演进：偏好存储 Room→DataStore 迁移、序列化 Moshi→kotlinx.serialization、自定义字体系统、设置流程从 ViewModel 状态机迁移至 Navigation 3 回退栈、主题家族目录单源化（`ThemeResolver.families`）、侧栏/底栏手势体系、**设置流独立为 `feature:settings:{api,impl}` 模块（共用组件 `Button` / `Slider` 下沉到 `core:ui`）**。后续修改组件参数时，请同步更新第 13 节速查表。
