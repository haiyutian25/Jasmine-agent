# core:widgets 对 Material3 的使用面审查（逐引用点）

> **核验日期：2026-10-02**。核验方式是"逐个 import、逐个使用点回源码对一遍"：符号是否存在、行号是否对得上、正文有没有真的用到。
> 本文档描述的是当前源码。**如果发现哪一条与代码不符，请按代码改正本文档**（而不是反过来）。

审查范围：`core/widgets/src/main/java/com/lhzkml/jasmine/core/widgets/` 下全部 `.kt`（共 110 个文件）。

> **⚠️ 改造已开始（2026-10-02，本文档写完之后）**：已按"**在现有主题（`JasmineTheme` / `CssVariables`）之下做、不新增第二套主题**"的方式，把 `core:widgets` 的取值从 M3 主题切到自有主题。
> 因此 **§5 的逐点清单是改造前的快照**（行号仍可用于对照原文），其中"主题类"引用已经清零 —— 进度与残留见文末 [§10 改造记录](#10-改造记录)。

---

## 目录

1. [一句话结论](#1-一句话结论)
2. [三种"性质"的定义](#2-三种性质的定义)
3. [总览：五类统计与逐符号表](#3-总览五类统计与逐符号表)
4. [为什么"主题类"换不掉](#4-为什么主题类换不掉)
5. [逐文件逐引用点清单（31 个文件，改造前快照）](#5-逐文件逐引用点清单31-个文件改造前快照)
6. [真·零件清单（不是主题，是拿 M3 当积木）](#6-真零件清单不是主题是拿-m3-当积木)
7. [死 import 清单（import 了但正文没用到）](#7-死-import-清单import-了但正文没用到)
8. [行动清单](#8-行动清单)
9. [复现方法](#9-复现方法)
10. [改造记录](#10-改造记录)

---

## 1. 一句话结论

`core:widgets` 有 **110 个文件**，其中**只有 31 个**碰到 `androidx.compose.material3`，合计 **168 行 import**。按用途分，其构成是：

| 类别 | import 数 | 占比 | 含义 |
|---|---|---|---|
| **主题（THEME）** | **99** | **59%** | 读 M3 主题取色/取字/取形 —— 这是"取值管道"，结构性依赖 |
| **触控（TOUCH）** | 23 | 14% | 涟漪与最小触控区 —— M3 的交互规格 |
| **注解（ANNOTATION）** | 21 | 12% | `@ExperimentalMaterial3Api` / `@OptIn` —— **零行为** |
| **零件（M3-COMPONENT）** | **23** | **14%** | **真把 M3 的组件当积木用** —— 只有这部分是"我们还没自有" |
| 类型（TYPE） | 2 | 1% | `Shapes` / `Typography` 类型引用 |

**两个要点：**

1. **六成是"读主题"**，不是"用 M3 组件"。也就是说这个库**并不是**"半依赖 M3、半自己写"，而是"自己写组件 + 从 M3 主题取默认值"。
2. **真正借用 M3 零件的只有 23 处**，且高度集中在 **7 个文件**：`bottomsheet/SheetDefaults.kt`（8 个符号）、`bottomsheet/ModalBottomSheet.kt`、`bottomsheet/ModalBottomSheetAndroid.kt`、`searchbar/SearchBar.kt`、`tabs/Tab.kt`、`tabs/TabRow.kt`、`menu/Menu.kt`、`menu/ExposedDropdownMenu.kt`。其中 `Surface` 与 `HorizontalDivider` 两类**我们其实已经有（或很容易有）自己的实现**。

---

## 2. 三种"性质"的定义

审查中给每个引用点标一个性质，含义如下：

| 性质 | 判据 | 能不能换成自有 |
|---|---|---|
| **主题** | 读 `MaterialTheme.colorScheme/shapes/typography`，或读写 `LocalContentColor` / `LocalTextStyle` / `LocalAbsoluteTonalElevation` / `LocalTonalElevationEnabled` / `LocalMinimumInteractiveComponentSize` / `LocalTextSelectionColors`；调用 `contentColorFor` / `surfaceColorAtElevation` / `ProvideTextStyle` | **不能**（见 §4：M3 侧是 `internal` CompositionLocal，外部无法提供）。但**取值来源可以换成我们自己的主题** —— 见 §10，已做完 |
| **零件** | 直接调用 M3 的**组件**：`Surface` / `HorizontalDivider` / `SnackbarHost` / `SnackbarHostState` / `Text` / `Icon` / `TooltipBox` / `PlainTooltip` / `TooltipDefaults` / `TooltipAnchorPosition` / `rememberTooltipState` | **能**，逐个换成自有（见 §6、§8） |
| **触控** | `ripple(...)` / `Modifier.minimumInteractiveComponentSize()` | 能，但没必要（M3 的交互规格，一行调用） |
| **注解** | `@ExperimentalMaterial3Api` / `@OptIn(ExperimentalMaterial3Api::class)` | 无行为，可保留 |
| **类型** | `ColorScheme` / `Shapes` / `Typography` 作类型用 | 无行为，可保留 |

---

## 3. 总览：五类统计与逐符号表

### 3.1 逐符号（按涉及文件数排序）

| 符号 | 性质 | 文件数 | import 行 | 正文出现 |
|---|---|---|---|---|
| `MaterialTheme` | 主题 | 24 | 24 | 57 |
| `LocalContentColor` | 主题 | 22 | 22 | 23 |
| `ExperimentalMaterial3Api` | 注解 | 21 | 21 | 91 |
| `ColorScheme` | 主题 | 16 | 16 | 16 |
| `contentColorFor` | 主题 | 15 | 15 | 27 |
| `LocalTextStyle` | 主题 | 13 | 13 | 12 |
| `minimumInteractiveComponentSize` | 触控 | 12 | 12 | 10 |
| `ripple` | 触控 | 11 | 11 | 12 |
| `Surface` | **零件** | 6 | 6 | 5 |
| `ProvideTextStyle` | 主题 | 4 | 4 | 3 |
| `HorizontalDivider` | **零件** | 3 | 3 | 6 |
| `SnackbarHost` | **零件** | 3 | 3 | 0（全为死 import） |
| `SnackbarHostState` | **零件** | 3 | 3 | 0（全为死 import） |
| `Icon` | **零件** | 2 | 2 | 1 |
| `LocalMinimumInteractiveComponentSize` | 主题 | 2 | 2 | 2 |
| `LocalAbsoluteTonalElevation` | 主题 | 1 | 1 | 7 |
| `LocalTonalElevationEnabled` | 主题 | 1 | 1 | 1 |
| `surfaceColorAtElevation` | 主题 | 1 | 1 | 1 |
| `PlainTooltip` / `TooltipBox` / `TooltipDefaults` / `TooltipAnchorPosition` / `rememberTooltipState` | **零件** | 各 1 | 各 1 | 各 1 |
| `Text` | **零件** | 1 | 1 | 1 |
| `Shapes` / `Typography` | 类型 | 各 1 | 各 1 | 各 1 |

> 注：`MaterialTheme.colorScheme` 这种写法会同时命中 `MaterialTheme` 与 `ColorScheme` 两个符号，所以两列数字有重叠，不必相加。

### 3.2 五类合计

| 性质 | import | 符号数 | 文件命中 |
|---|---|---|---|
| 主题 | 99 | 10 | 99 |
| 触控 | 23 | 2 | 23 |
| 注解 | 21 | 1 | 21 |
| 零件 | 23 | 11 | 23 |
| 类型 | 2 | 2 | 2 |
| **合计** | **168** | 26 | 168 |

---

## 4. 为什么"主题类"换不掉

M3 的 `MaterialTheme` 内部是这样提供的（`material3/commonMain/androidx/compose/material3/MaterialTheme.kt:99-107`）：

```kotlin
CompositionLocalProvider(
    LocalColorScheme provides colorScheme,          // material3 internal
    _localMotionScheme provides motionScheme,       // material3 internal
    LocalIndication provides rippleIndication,
    LocalShapes provides shapes,                    // material3 internal（Shapes.kt:397）
    LocalTextSelectionColors provides selectionColors,
    LocalTypography provides typography,            // material3 internal（Typography.kt:788）
) { ProvideTextStyle(value = typography.bodyLarge, content = content) }
```

其中 `LocalColorScheme`、`LocalShapes`、`LocalTypography`、`_localMotionScheme` **都是 M3 的 `internal`**（1.4.0 与 1.5.0-alpha29 均如此）。外部代码**无法 provide** 它们。

因此：

* 只要项目里还在调用**任何一个 M3 组件**（本项目里还有 `Text` / `Icon` / `IconButton` / `HorizontalDivider` / `Scaffold` / `SnackbarHost` / `Tooltip*` / `ModalBottomSheet` / `CircularProgressIndicator` 等），就**必须**保留 M3 的 `MaterialTheme`，否则会退化成 M3 默认配色；
* `core:widgets` 能做的极限是"**取值不再问 M3 主题**"（§10 已完成），而 `MaterialTheme` 本体仍由 `core/ui/.../theme/Theme.kt:122` 这一处接缝提供 —— 那是给"我们直接调用的 M3 组件"兜底的。

---

## 5. 逐文件逐引用点清单（31 个文件，改造前快照）

格式：`行号 | 符号 | 用来干什么 | 性质`。`@OptIn/@ExperimentalMaterial3Api` 这类注解点成组列出。
**行号是改造前的位置**，用于对照原文；改造后的归属见 §10。

### 5.1 appbar/TopAppBar.kt（1584 行 / 4 符号 / 21 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L150, L217, L257, L446, L472, L509, L549, L814, L852, L962, L995, L1013, L1020, L1339, L1388, L1462, L1529 | `ExperimentalMaterial3Api` | 全是 `@OptIn` / `@ExperimentalMaterial3Api` 标记（17 处） | 注解 |
| L324 | `MaterialTheme.colorScheme` | `topAppBarColors()`：以 `defaultTopAppBarColors` 为底覆盖 —— 顶栏默认色 | 主题 |
| L427 | `MaterialTheme.colorScheme` | `centerAlignedTopAppBarColors()`：同上（居中版） | 主题 |
| L1084 | `LocalContentColor` | `TopAppBarLayout` 给**导航图标**下发内容色 | 主题（下发） |
| L1131 | `LocalContentColor` | 给**动作图标**下发内容色 | 主题（下发） |

### 5.2 badge/Badge.kt（216 行 / 1 符号 / 1 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L159 | `contentColorFor` | `Badge()` 的默认 `contentColor`：由容器色推导 | 主题 |

### 5.3 bottomsheet/BasicEdgeToEdgeDialog.kt（346 行 / 1 符号 / 2 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L92, L93 | `LocalContentColor` | 用"当前内容色的亮度"判断状态栏/导航栏该用亮图标还是暗图标 | 主题（读） |

### 5.4 bottomsheet/ModalBottomSheet.kt（524 行 / 9 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L133, L233, L267, L476 | `ExperimentalMaterial3Api` | 注解（4 处） | 注解 |
| L142, L241 | `contentColorFor` | 两个 `ModalBottomSheet` 重载的默认 `contentColor` | 主题 |
| L279 | `contentColorFor` | `ModalBottomSheetContent` 的默认 `contentColor` | 主题 |
| **L287** | **`Surface`** | 弹层内容的承载面 —— 用的是 M3 的 `Surface`，应换成本库 `surface/Surface.kt` | **零件** |

### 5.5 bottomsheet/ModalBottomSheetAndroid.kt（667 行 / 9 符号 / 6 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L106, L246, L319, L375, L459 | `ExperimentalMaterial3Api` | 注解（5 处） | 注解 |
| L349 | `contentColorFor` | `ModalBottomSheet()` 的默认 `contentColor` | 主题 |

> 该文件 import 了 `Surface` / `SnackbarHost` / `SnackbarHostState` / `LocalContentColor` / `LocalTextStyle` / `MaterialTheme` / `minimumInteractiveComponentSize`，但**正文一处都没用** —— 见 §7。

### 5.6 bottomsheet/SheetDefaults.kt（558 行 / 15 符号 / 12 点）★ 借用零件最集中

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L107, L376, L390, L442, L458, L518 | `ExperimentalMaterial3Api` | 注解（6 处） | 注解 |
| L425 | `MaterialTheme.shapes` | `DragHandle()` 的默认形状：`extraLarge` | 主题（形） |
| **L429** | **`Surface`** | 拖拽把手的容器面 —— 应换本库 `Surface` | **零件** |
| **L448** | **`TooltipBox`** | 把手的无障碍提示浮层 | **零件** |
| **L450** | **`TooltipDefaults` / `TooltipAnchorPosition`** | 提示的定位策略（`Above`） | **零件** |
| **L451** | **`PlainTooltip` / `Text`** | 提示正文 | **零件** |
| **L452** | **`rememberTooltipState`** | 提示状态 | **零件** |

> 这个文件是"未自有零件"的最大集中地：`Surface` + `SnackbarHost` + `SnackbarHostState` + `Text` + `Tooltip` 全家（8 个符号）。其中 `SnackbarHost`/`State`/`LocalContentColor`/`LocalTextStyle`/`contentColorFor`/`minimumInteractiveComponentSize` 是死 import（§7）。

### 5.7 bottomsheet/SheetScaleModifiers.kt（63 行 / 1 符号 / 2 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L38, L56 | `ExperimentalMaterial3Api` | 注解（2 处） | 注解 |

### 5.8 button/Button.kt（646 行 / 4 符号 / 4 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L141 | `LocalContentColor` | 给按钮内容下发内容色 | 主题（下发） |
| L142 | `LocalTextStyle` | 给按钮内容下发合并后的文字样式 | 主题（下发） |
| L350 | `MaterialTheme.colorScheme` | `buttonColors()`：以 `defaultButtonColors` 为底覆盖 | 主题 |
| L395 | `MaterialTheme.colorScheme` | `textButtonColors()`：同上（文本按钮） | 主题 |

### 5.9 card/Card.kt（858 行 / 6 符号 / 13 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L499, L545, L594 | `contentColorFor` | 三种卡片 `xxxColors()` 的默认 `contentColor` 参数 | 主题 |
| L503 | `MaterialTheme.colorScheme` | `cardColors()`：以 `defaultCardColors` 为底覆盖 | 主题 |
| L549 | `MaterialTheme.colorScheme` | `elevatedCardColors()`：同上 | 主题 |
| L598 | `MaterialTheme.colorScheme` | `outlinedCardColors()`：同上 | 主题 |
| L516, L522 | `contentColorFor` | `ColorScheme.defaultCardColors`：用 `FilledCardTokens.ContainerColor` 推导内容色（正常态 + 禁用态） | 主题 |
| L563, L571 | `contentColorFor` | 同上（Elevated 卡片） | 主题 |
| L612, L615 | `contentColorFor` | 同上（Outlined 卡片） | 主题 |

> 13 点全是主题；`LocalContentColor` / `ripple` / `ExperimentalMaterial3Api` 为死 import（§7）。

### 5.10 checkbox/Checkbox.kt（743 行 / 4 符号 / 4 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L290 | `ripple` | 状态层涟漪（`bounded = false`，半径 = `CheckboxTokens.StateLayerSize / 2`） | 触控 |
| L302 | `minimumInteractiveComponentSize` | 保证最小触控区 | 触控 |
| L347 | `MaterialTheme.colorScheme` | `CheckboxDefaults.colors()`：以 `defaultCheckboxColors` 为底覆盖 | 主题 |

### 5.11 dialog/AlertDialog.kt（512 行 / 2 符号 / 4 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L174, L198, L243 | `ExperimentalMaterial3Api` | 注解（3 处） | 注解 |
| L317 | `LocalContentColor` | `AlertDialogContent` 给图标下发内容色 | 主题（下发） |

### 5.12 fabmenu/FloatingActionButtonMenu.kt（757 行 / 5 符号 / 9 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L339 | `MaterialTheme.colorScheme` | 菜单项容器默认色（`primaryContainer`） | 主题 |
| L340 | `contentColorFor` | 由容器色推导内容色 | 主题 |
| L352 | `LocalMinimumInteractiveComponentSize` | **故意**置 0dp：展开动画与自算尺寸冲突，注释里写明 | 主题（覆盖） |
| L411 | `LocalTextStyle` + `MaterialTheme.typography` | 给菜单项标题下发 `titleMedium` | 主题（下发） |
| L551 | `ripple` | 菜单项涟漪 | 触控 |
| L585, L586 | `MaterialTheme.colorScheme` | 容器色动画的起止色（`primaryContainer` → `primary`） | 主题 |
| L616, L617 | `MaterialTheme.colorScheme` | 图标色动画的起止色（`onPrimaryContainer` → `onPrimary`） | 主题 |

### 5.13 menu/ExposedDropdownMenu.kt（1566 行 / 7 符号 / 7 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L161, L277, L401, L450, L458, L795 | `ExperimentalMaterial3Api` | 注解（6 处） | 注解 |
| **L461** | **`Icon`** | `TrailingIcon`：展开箭头（`Icons.Filled.ArrowDropDown` + 旋转动画） | **零件** |

### 5.14 menu/Menu.kt（601 行 / 8 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L292 | `MaterialTheme.colorScheme` | `MenuItemDefaults.itemColors()`：以 `defaultMenuItemColors` 为底覆盖 | 主题 |
| **L454** | **`Surface`** | 菜单容器面 —— 应换本库 `Surface` | **零件** |
| L504 | `ripple` | 菜单项涟漪（`ripple(true)`） | 触控 |
| L517 | `ProvideTextStyle` + `MaterialTheme.typography` | 菜单项文字用 `labelLarge` | 主题（下发） |
| L520 | `LocalContentColor` | 给前导图标下发颜色 | 主题（下发） |
| L527 | `LocalContentColor` | 给文本下发颜色 | 主题（下发） |
| L550 | `LocalContentColor` | 给尾随图标下发颜色 | 主题（下发） |

### 5.15 navigation/NavigationBar.kt（825 行 / 6 符号 / 5 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L126 | `MaterialTheme.colorScheme` + `contentColorFor` | `NavigationBar()` 的默认 `contentColor` | 主题 |
| L232 | `LocalContentColor` | 给导航项图标下发内容色 | 主题（下发） |
| L304 | `ripple` | 选中指示条的涟漪 | 触控 |
| L381 | `MaterialTheme.colorScheme` | `NavigationBarItemDefaults.colors()`：以默认色为底覆盖 | 主题 |

### 5.16 navigation/NavigationDrawer.kt（1090 行 / 5 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L479, L523 | `contentColorFor` | `DismissibleDrawerSheet`（两个重载）的默认 `drawerContentColor` | 主题 |
| L564 | `contentColorFor` | `PermanentDrawerSheet` 的默认 `drawerContentColor` | 主题 |
| L589 | `contentColorFor` | `DrawerSheet` 的默认 `drawerContentColor` | 主题 |
| L893 | `LocalContentColor` | 抽屉项图标下发色 | 主题（下发） |
| L898 | `LocalContentColor` | 抽屉项文本下发色 | 主题（下发） |
| L903 | `LocalContentColor` | 抽屉项徽标下发色 | 主题（下发） |

### 5.17 progress/ProgressIndicator.kt（803 行 / 1 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L90, L158, L322, L366, L426, L582, L586, L590 | `ExperimentalMaterial3Api` | 注解（8 处） | 注解 |

> 该文件**没有任何**实质借用：波形与形状变换那些 expressive 代码自带全部实现。

### 5.18 pulltorefresh/PullToRefresh.kt（871 行 / 1 符号 / 2 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L420 | `MaterialTheme.colorScheme` | 指示器默认容器色：`surfaceContainerHigh` | 主题 |
| L432 | `MaterialTheme.colorScheme` | 指示器默认前景色：`onSurfaceVariant` | 主题 |

### 5.19 radio/RadioButton.kt（271 行 / 4 符号 / 4 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L102 | `ripple` | 状态层涟漪（`bounded = false`） | 触控 |
| L111 | `minimumInteractiveComponentSize` | 最小触控区 | 触控 |
| L161 | `MaterialTheme.colorScheme` | `RadioButtonDefaults.colors()`：以默认色为底覆盖 | 主题 |

### 5.20 searchbar/SearchBar.kt（1276 行 / 4 符号 / 21 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L205, L254, L336, L407, L417, L530, L555, L560, L760, L933, L960, L1033, L1230, L1260 | `ExperimentalMaterial3Api` | 注解（14 处） | 注解 |
| L220, L978, L1119 | `contentColorFor` | 搜索框 / 展开态 / 停靠态的内容色推导 | 主题 |
| L769 | `LocalTextStyle` | `InputField` 的默认 `textStyle` | 主题 |
| **L993, L1128** | **`HorizontalDivider`** | 输入框与结果列表之间的分隔线（两处绘制） | **零件** |

### 5.21 slider/Slider.kt（1977 行 / 7 符号 / 16 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L190, L282, L365, L395, L770, L836, L869, L924, L979, L1015, L1464, L1528, L1698, L1874 | `ExperimentalMaterial3Api` | 注解（14 处） | 注解 |
| L442 | `minimumInteractiveComponentSize` | 滑块拖动件的触控区 | 触控 |
| L663 | `MaterialTheme.colorScheme` | `SliderDefaults.colors()`：以 `defaultSliderColors` 为底覆盖 | 主题 |

### 5.22 surface/Surface.kt（348 行 / 6 符号 / 16 点）★ 主题管道的中枢

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L103, L202, L274 | `MaterialTheme.colorScheme` | 三个 `Surface` 重载的默认底色：`colorScheme.surface` | 主题 |
| L104, L203, L275 | `contentColorFor` | 默认 `contentColor`：由底色推导 | 主题 |
| L112, L113 | `LocalContentColor` + `LocalAbsoluteTonalElevation` | 向下下发内容色与**绝对海拔**（tonal elevation 叠加的基础） | 主题（下发） |
| L214, L215 | 同上 | 同上（可点击重载） | 主题（下发） |
| L286, L287 | 同上 | 同上（可选重载） | 主题（下发） |
| L220, L292 | `minimumInteractiveComponentSize` | 可点击时的最小触控区 | 触控 |
| L303 | `ripple` | 可点击时的涟漪 | 触控 |
| L341 | `MaterialTheme.colorScheme` | `surfaceColorAtElevation(color, elevation)`：海拔染色 | 主题 |

### 5.23 switch/Switch.kt（597 行 / 5 符号 / 10 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L115 | `minimumInteractiveComponentSize` | 最小触控区 | 触控 |
| L179 | `LocalContentColor` | 给内部图标下发颜色 | 主题（下发） |
| L313, L317, L322, L326, L330, L334, L338 | `MaterialTheme.colorScheme` | 7 处 `compositeOver(colorScheme.surface)`：禁用态与轨道色需要压在 surface 上混合 | 主题 |

### 5.24 tabs/Tab.kt（473 行 / 8 符号 / 9 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L134, L198, L269 | `LocalContentColor` | 三个 `Tab` 重载的默认 `selectedContentColor` | 主题（读） |
| L145, L229 | `ProvideTextStyle` | 给选项卡文本下发样式 | 主题（下发） |
| L218, L289 | `ripple` | 选项卡涟漪（`bounded = true`、带颜色） | 触控 |
| L327 | `LocalContentColor` | 给内容下发颜色 | 主题（下发） |

> `MaterialTheme` / `LocalTextStyle` / `Surface` / `HorizontalDivider` / `ExperimentalMaterial3Api` 为死 import（§7）。

### 5.25 tabs/TabRow.kt（962 行 / 8 符号 / 9 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| **L189, L239, L294, L364** | **`HorizontalDivider`** | 四种 `TabRow` 的**默认分隔线**（`divider = { HorizontalDivider() }`） | **零件** |
| L416, L535 | `ExperimentalMaterial3Api` | 注解（2 处） | 注解 |
| **L426, L549** | **`Surface`** | `TabRowImpl` / `ScrollableTabRowImpl` 的行容器 —— 应换本库 `Surface` | **零件** |

### 5.26 textfield/OutlinedTextField.kt（1453 行 / 7 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L214, L382, L550 | `ExperimentalMaterial3Api` | 注解（3 处） | 注解 |
| L221, L390, L558 | `LocalTextStyle` | 三个重载的默认 `textStyle` | 主题 |
| L704, L712 | `minimumInteractiveComponentSize` | 前导 / 尾随图标的触控区 | 触控 |

### 5.27 textfield/ProvideContentColorTextStyle.kt（49 行 / 2 符号 / 2 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L45 | `LocalContentColor` | 给文本框内容下发内容色 | 主题（下发） |
| L46 | `LocalTextStyle` | 给文本框内容下发合并后的文字样式 | 主题（下发） |

### 5.28 textfield/TextField.kt（1684 行 / 7 符号 / 8 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L272, L435, L586, L816 | `ExperimentalMaterial3Api` | 注解（4 处） | 注解 |
| L279, L443, L594 | `LocalTextStyle` | 三个重载的默认 `textStyle` | 主题 |
| L721, L729 | `minimumInteractiveComponentSize` | 前导 / 尾随图标触控区 | 触控 |

### 5.29 textfield/TextFieldDefaults.kt（2037 行 / 7 符号 / 6 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L492 | `MaterialTheme.colorScheme` | `colors()`：`defaultTextFieldColors(LocalTextSelectionColors.current)` —— 文本框默认配色需要"文本选中色"参与 | 主题 |
| L594 | `MaterialTheme.colorScheme` | `colors(...)` 的接收者：`ColorScheme.defaultTextFieldColors(...)` | 主题 |
| L1322 | `MaterialTheme.colorScheme` | 轮廓版：以 `defaultOutlinedTextFieldColors` 为底覆盖 | 主题 |
| L723, L756, L1464 | `ExperimentalMaterial3Api` | 注解（3 处） | 注解 |

### 5.30 textfield/TextFieldImpl.kt（549 行 / 8 符号 / 1 点）

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L336 | `LocalContentColor` | `Decoration()`：给装饰内容下发内容色 | 主题（下发） |

> import 了 8 个 M3 符号，正文只用 1 个（`ColorScheme` / `ExperimentalMaterial3Api` / `LocalTextStyle` / `contentColorFor` / `minimumInteractiveComponentSize` 为死 import）—— 全库"最虚"的文件。

### 5.31 tokens/TokenResolvers.kt（171 行 / 6 符号 / 4 点）★ 令牌总闸

| 行 | 符号 | 用来干什么 | 性质 |
|---|---|---|---|
| L73 | `MaterialTheme.colorScheme` | `ColorSchemeKeyTokens.value`：色令牌 → `Color`（把枚举映射到 `ColorScheme` 的槽位） | 主题 |
| L129 | `MaterialTheme.shapes` | `ShapeKeyTokens.value`：形状令牌 → `Shape` | 主题 |
| L171 | `MaterialTheme.typography` | `TypographyKeyTokens.value`：字令牌 → `TextStyle` | 主题 |
| L147 | `surfaceColorAtElevation` | 海拔染色（供 `Surface` 的 tonal elevation 用） | 主题 |

> 这是全部 **51 张令牌表**落地的唯一出口：令牌表写的是"槽位名"，真正取到值全靠这 3 个扩展属性 + 1 个海拔函数。

---

## 6. 真·零件清单（不是主题，是拿 M3 当积木）

按"能不能换、值不值得换"排序：

| 序号 | 零件 | 调用点 | 现状 | 换自有 |
|---|---|---|---|---|
| 1 | **`Surface`** | `bottomsheet/SheetDefaults.kt:429`、`bottomsheet/ModalBottomSheet.kt:287`、`menu/Menu.kt:454`、`tabs/TabRow.kt:426`、`tabs/TabRow.kt:549` | **本库已有 `surface/Surface.kt`（348 行）** | **立刻可换，零风险**（另外 2 处 import 是死的：`ModalBottomSheetAndroid.kt`、`Tab.kt`） |
| 2 | **`HorizontalDivider`** | `searchbar/SearchBar.kt:993`、`:1128`、`tabs/TabRow.kt:189/239/294/364` | 本库**没有** Divider | 先搬 `Divider`（上游 118 行，需剔掉废弃的 `Divider`），再把 6 个调用点换掉 |
| 3 | **`TooltipBox` / `PlainTooltip` / `TooltipDefaults` / `TooltipAnchorPosition` / `rememberTooltipState` / `Text`** | `bottomsheet/SheetDefaults.kt:448/450/451/452` | 本库**没有** Tooltip / 自有 Text | 要自有意义不大（Tooltip 只在拖拽把手的无障碍提示里用）；可后置 |
| 4 | **`SnackbarHost` / `SnackbarHostState`** | 全部为死 import（3 个文件） | 本库**没有** Snackbar | **直接删 import 即可**，无需移植 |
| 5 | **`Icon`** | `menu/ExposedDropdownMenu.kt:461`（`TrailingIcon` 的箭头） | 本库**没有**自有 Icon | 不建议自造；留在 M3 即可 |

---

## 7. 死 import 清单（import 了但正文没用到）

判据：该符号在文件**正文**（排除 import、行注释与 KDoc）里一次都没出现。这类 import **可以直接删掉**，不影响行为。

| 文件 | 死 import |
|---|---|
| `bottomsheet/ModalBottomSheet.kt` | `LocalContentColor`, `LocalTextStyle`, `MaterialTheme`, `SnackbarHost`, `SnackbarHostState`, `minimumInteractiveComponentSize` |
| `bottomsheet/ModalBottomSheetAndroid.kt` | `LocalContentColor`, `LocalTextStyle`, `MaterialTheme`, `SnackbarHost`, `SnackbarHostState`, **`Surface`**, `minimumInteractiveComponentSize` |
| `bottomsheet/SheetDefaults.kt` | `LocalContentColor`, `LocalTextStyle`, `SnackbarHost`, `SnackbarHostState`, `contentColorFor`, `minimumInteractiveComponentSize` |
| `card/Card.kt` | `LocalContentColor`, `ripple`, `ExperimentalMaterial3Api` |
| `menu/ExposedDropdownMenu.kt` | `ColorScheme`, `LocalContentColor`, `MaterialTheme`, `ProvideTextStyle`, `ripple` |
| `menu/Menu.kt` | `ExperimentalMaterial3Api`, `Icon` |
| `navigation/NavigationBar.kt` | `ExperimentalMaterial3Api` |
| `navigation/NavigationDrawer.kt` | `ColorScheme`, `ExperimentalMaterial3Api`, `MaterialTheme` |
| `slider/Slider.kt` | `LocalContentColor`, `contentColorFor`, `ripple` |
| `switch/Switch.kt` | `ExperimentalMaterial3Api` |
| `tabs/Tab.kt` | `MaterialTheme`, `LocalTextStyle`, **`Surface`**, **`HorizontalDivider`**, `ExperimentalMaterial3Api` |
| `tabs/TabRow.kt` | `MaterialTheme`, `LocalContentColor`, `LocalTextStyle`, `ProvideTextStyle`, `ripple` |
| `textfield/OutlinedTextField.kt` | `ColorScheme`, `LocalContentColor`, `MaterialTheme`, `contentColorFor` |
| `textfield/TextField.kt` | `ColorScheme`, `LocalContentColor`, `MaterialTheme`, `contentColorFor` |
| `textfield/TextFieldDefaults.kt` | `LocalContentColor`, `LocalTextStyle`, `contentColorFor`, `minimumInteractiveComponentSize` |
| `textfield/TextFieldImpl.kt` | `ColorScheme`, `ExperimentalMaterial3Api`, `LocalTextStyle`, `contentColorFor`, `minimumInteractiveComponentSize` |

合计 **16 个文件 / 64 行死 import**（其中 8 个文件的 `MaterialTheme` 死 import 已在 §10 阶段 2 清掉）。

---

## 8. 行动清单

| 优先级 | 动作 | 影响面 | 风险 | 状态 |
|---|---|---|---|---|
| **1** | 删掉 §7 那 64 行死 import | 16 个文件 | 零（无行为变化；删完跑 `:core:widgets:compileDebugKotlin`） | 部分完成（`MaterialTheme` 那 8 个已删） |
| **2** | 5 处 `Surface` 换成本库 `surface.Surface` | `SheetDefaults` / `ModalBottomSheet` / `Menu` / `TabRow`×2 | 低（同签名同语义，注意 import 改指本库） | 待做 |
| **3** | 搬 `Divider`（上游 118 行，剔废弃 `Divider`）→ 换掉 6 个 `HorizontalDivider` 调用点 | 新增 `core/widgets/divider/`，改 `SearchBar`、`TabRow` | 低-中（新组件，需排版对齐验证） | 待做 |
| 4 | 评估 `Tooltip` / `Snackbar` / `Text` / `Icon` | `SheetDefaults`、`ExposedDropdownMenu` | 不建议：收益低、维护成本高 | 不建议 |
| — | **主题类 99 处：取值来源改为自有主题**（不再问 M3 主题） | 全库 | 零观感变化（映射逐条对齐） | **已完成**（见 §10） |

做完 1–3 后，`core:widgets` 里"拿 M3 当积木"的只剩：

* `Icon`（1 处：`ExposedDropdownMenu.TrailingIcon` 的箭头）
* `Tooltip` 家族 + `Text`（1 个文件：`SheetDefaults` 的拖拽把手提示）

---

## 9. 复现方法

本审查的数据可用如下办法复现（PowerShell，**脚本必须只用 ASCII 字符**，否则 5.1 会按 ANSI 解析 `.ps1` 而报语法错）：

1. 取 `core/widgets` 下全部 `.kt`；
2. 逐文件收集 `^import androidx\.compose\.material3\.([A-Za-z0-9_]+)$` 的符号集合；
3. 逐行扫描正文（跳过 import 行、以 `*`/`//`/`/*` 开头的注释行），对每个符号做 `\b符号\b` 匹配，记录 `行号 + 符号 + 所在声明 + 原始代码`；
4. 把"收集到但正文零命中"的符号列为死 import。

配套的自查命令（改完代码后可直接跑）：

```powershell
# 1) 统计还有多少文件碰 material3
$w = 'core\widgets\src\main\java\com\lhzkml\jasmine\core\widgets'
Get-ChildItem $w -Recurse -Filter *.kt |
  Where-Object { (Get-Content $_.FullName -Raw) -match 'import androidx\.compose\.material3\.' } |
  Measure-Object | ForEach-Object { "files touching material3: $($_.Count)" }

# 2) 只看"真·零件"是否还在
foreach ($s in 'Surface','HorizontalDivider','SnackbarHost','PlainTooltip','TooltipBox','Icon','Text') {
  $n = (Select-String -Path "$w\*\*.kt" -Pattern "import androidx\.compose\.material3\.$s$" | Measure-Object).Count
  "$s -> $n file(s)"
}
```

> 复现脚本里**不要**把函数命名成 `Cat` / `Where` / `Select` 这类 PowerShell 别名 —— 别名优先级高于函数，会被解析成 `Get-Content` 等内置命令。
> 另外：**用 `replace_in_file` 之类工具改本文档时，若匹配失败要先 `read_file` 再改** —— 曾经有一次失败的匹配把本文件截成了 0 字节（2026-10-02 18:08），只能重写。

---

## 10. 改造记录

> **目标（用户定的规则）**：**不要新增第二套主题**；一切主题改造都在**现有** `JasmineTheme` + `CssVariables`（`core/ui/theme/`）之下做 —— 让组件**直接读我们自己的主题**，而不是"借 M3 主题取值"。

### 阶段 1（2026-10-02）令牌总闸切到自有主题

`core/widgets/.../tokens/TokenResolvers.kt` 是 51 张令牌表唯一的落地口，三条路都改了：

| 令牌 | 以前 | 现在 |
|---|---|---|
| 色 `ColorSchemeKeyTokens.value` | `MaterialTheme.colorScheme.fromToken(this)` | **`LocalCssVariables.current.fromToken(this)`** |
| 形 `ShapeKeyTokens.value` | `MaterialTheme.shapes.fromToken(this)` | **`LocalWidgetsShapes.current.fromToken(this)`** |
| 字 `TypographyKeyTokens.value` | `MaterialTheme.typography.fromToken(this)` | **`AppTypography.fromToken(this)`** |

新增 `CssVariables.fromToken(ColorSchemeKeyTokens)`：**19 条，逐条镜像 `Theme.kt` 的映射**（`PrimaryContainer -> accent`、`SurfaceContainerHighest -> subtleSurface`、`Outline -> border`、`OutlineVariant -> muted` …），所以取值与改造前**完全一致**（零观感变化），区别只是组件不再经过 M3 主题。`Error` / `Scrim` 两个 key 现有 12 套调色板没有槽位，暂用 M3 基线常量（值同前）。

### 阶段 2（2026-10-02）组件里的直写读取与默认色构造器

16 个文件批量改造（脚本 + 人工修正）：

* `internal val ColorScheme.defaultXxxColors` → `internal val CssVariables.defaultXxxColors`；
* `MaterialTheme.colorScheme.defaultXxxColors` → `LocalCssVariables.current.defaultXxxColors`；
* `MaterialTheme.colorScheme.<role>` → `LocalCssVariables.current.<slot>`（角色→槽位字典 32 条，镜像 `Theme.kt`）；
* `MaterialTheme.typography.<x>` → `AppTypography.<x>`；`MaterialTheme.shapes.<x>` → `LocalWidgetsShapes.current.<x>`；
* 裸角色名：`compositeOver(surface)` → `compositeOver(card)`（两者取到同一个值）；
* 清掉 12 个文件里已经失效的 `MaterialTheme` import（其中 8 个本来就是死 import）。

新增**纯函数** `CssVariables.contentColorFor(backgroundColor: Color)`（放在 `core/ui/theme/Theme.kt`）：与 M3 同名函数语义一致（`accent`↔`accentForeground`、`card`↔`cardForeground`、`primary`↔`primaryForeground`、`background`↔`foreground`、`subtleSurface`↔`mutedForeground`、`foreground`↔`background`，匹配不到返回 `Color.Unspecified`），但**不问 M3 主题**，因此在 `remember {}` 这类非组合上下文里也能调用。`Card` 与 `NavigationBar` 已切换过去。

### 当前残留（刻意保留）

| 文件 | 用途 | 为什么先留着 |
|---|---|---|
| `surface/Surface.kt:343` | `MaterialTheme.colorScheme.applyTonalElevation(...)` | 海拔染色用的是 M3 的 `surfaceColorAtElevation` 算法（按 `surfaceTint` 混色）；换掉等于重写算法，风险大于收益 |
| `textfield/TextFieldDefaults.kt:495/597` | `MaterialTheme.colorScheme.defaultTextFieldColors(...)` | 那两个 `ColorScheme.defaultTextFieldColors` / `defaultOutlinedTextFieldColors` 是**函数式**构造器（各百余行、内部用裸角色名），暂留 |

### 结果

* `core:widgets` 里 import `MaterialTheme` 的文件：**24 → 2**；
* 真实读 M3 主题的语句：**57 处 → 3 处**（上表两处共 3 行）；
* 门禁：`:core:widgets --rerun-tasks` 与 `:app` **0 错 0 警**、lint **0 条**、单测 **64 用例 / 0 失败**。
