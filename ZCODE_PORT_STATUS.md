# ZCode 设计系统移植状态

> 上游：`d:\ima\ZCode\packages\ui\src\styles.css`（2053 行）与其依赖的 `tailwindcss@4.3.3` 的 `theme.css`
> 落地：`core/ui/src/main/java/com/lhzkml/jasmine/core/ui/theme/`
> 记录时间：2026-10-05

本文档记录**已搬入但尚无消费点**的 token（它们是"可用但未用"的资产）、尚未搬入的部分及落地方式。
判断口径只有一条：**这份 token 现在有没有界面在用它**。有 → 归 §2；没有 → 归 §3（本文档主体）。

---

## 1. 取值方法（所有 token 共用）

上游大量用 `color-mix(in oklab, …)` / `oklch()` / `rgba()` 表达，手算必然失真。
因此**不手抄、不换算**，一律走同一套流程：

1. 把上游 CSS 的相关变量块（含 `@theme` / `.dark` / `.theme-zai-*`）+ `tailwindcss@4.3.3` 的
   `theme.css`（把它的 `@theme default {…}` 转成 `:root {…}`，否则浏览器不认 at-rule）拼成一个探针页；
2. 交给浏览器求值 —— 注意 `getComputedStyle` 会**保留** `oklab()` 原色彩空间，
   所以再画 1×1 像素用 `getImageData` **读回 RGBA**；
3. 由浏览器直接拼出 Kotlin 行 ⇒ **零转录误差**。

结果与上游**逐像素一致**，且 **alpha 原样保留**（上游的面/描边/状态层本就是半透明，不做合成）。

---

## 2. 已搬且**已被消费** ✓

| 项 | 位置 | 消费方 |
|---|---|---|
| 16 个基础槽（`background` / `foreground` / `card` / `border` / `primary` / `muted` / `accent` / `ring` / `subtleSurface` …）| `CssVariables` 构造参数 | 全库组件（`Surface` / `Card` / `TextField` / `Button` / `NavigationDrawer` …）|
| 派生槽（`surfaceContainerLow/High/Highest` / `surfaceVariant` / `primaryContainer` / `error*` / `inverse*` …）| `CssVariables` 的 `get()` | `CardDefaults` / `SearchBar` / `Badge` / `ModalBottomSheet` … |
| 12 → 16 套调色板（含本轮新增的 `zai` / `zcode`）| `ProductionPalettes` | 设置页「外观与主题」选择器 |

---

## 3. 已搬但**尚无消费点** ⚠️（本文档主体）

下面全部已就位、编译通过、有回归断言，但**当前没有任何界面在读它们**。
用起来的办法都是两类：**给新组件供色**，或**建对应组件**（见每行的"建议消费点"）。

### 3.1 `CssVariables.zcode` —— ZCode 的 144 个颜色槽（4 套值）

| 项 | 数量 | 说明 |
|---|---|---|
| `ZCodeSlots` | **144 × 4 套** | `ZaiLight` / `ZaiDark`（`.theme-zai-light` / `.theme-zai-dark`）/ `DefaultLight` / `DefaultDark`（`@theme` / `.dark`）|

槽组（按语义）：面/容器/浮层/输入 27 ✓ 状态层 12 ✓ 终端 ANSI 22 ✓ 图表/热力 18 ✓
轨迹/工作流 8 ✓ 语义 24 ✓ Git 状态 8 ✓ 图节点 18 ✓ 其它 7 ✓

**建议消费点**（按落地难度）：

| 槽组 | 建议用在哪 |
|---|---|
| `terminal*`（22）| 终端面板 / 代码块配色（jasmine 目前代码块用的是通用色）|
| `git*`（8）+ `diff*`（4）| 文件变更列表 / diff 视图 |
| `usageChart*`（6）+ `contextBreakdown*`（7）+ `usageHeatmap*`（5）| 「上下文容量」「用量统计」面板的图表配色 |
| `trajectory*`（5）| 工具调用块的类型着色（user / assistant / reasoning / toolCall / toolResult）|
| `fileNode*` / `skillNode*` / `commandNode*` / `subagentNode*` / `sessionNode*` / `pluginNode*`（18）| 会话列表 / 技能与插件列表的条目前缀色 |
| `success` / `destructive` / `warning` / `idleTask` | 状态徽标（Badge ✓ 已有组件，换色即可）|
| `brand`（Zai 纯黑白 / ZCode 海蓝 `#00BCFF`）| 强调态、聚焦环、进度指示 |
| `workflowRule` / `workflowTrace` / `workflowTraceStrong`（3）| 时间线 / 流程图的连线层级 |

### 3.2 `CssVariables.zcodeText` —— 排版槽（11）

| 项 | 值 |
|---|---|
| `uiFontSize` | `14`（基准）|
| `textUiXl` / `Lg` / `Base` / `Caption` / `Sm` / `Xs` / `2xs` | `18 / 16 / 14 / 13 / 12 / 10 / 9`（基准 ±4 / +2 / = / −1 / −2 / −4 / −5）|
| `trackingWfLabel` | `0.09em` |
| `textMobileInputSafe` | `16`（iOS 聚焦输入下限）|
| `fontMono` | 等宽字体栈（含 CJK 兜底）|

**说明**：本库排版的**唯一生效来源**是 `AppTypography`（见 `core/ui/.../theme/Type.kt`），
所以这批**故意不接入排版链路** —— 直接接会有两个真相源。它们的用途是：将来若要按上游口径
重定义排版梯度、或给等宽场景（终端 / 代码）取字号时，有据可依。

**建议消费点**：终端面板的字号梯度 ✓ 等宽字体（`fontMono`）可用在代码块与终端 ✓。

### 3.3 `CssVariables.zcodeWorkflow` —— 工作流图动效与小人脸（16）

| 项 | 值 |
|---|---|
| 时长 `tFastMs` / `tBaseMs` / `tEnterMs` / `tInkMs` | `120 / 160 / 200 / 320` ms |
| `ease` | `cubic-bezier(0.22, 0.61, 0.36, 1)`（已转成 `CubicBezierEasing` ✓ 同一组控制点）|
| `beatSeconds` | `1.6` |
| `faceBody` / `faceEye` | `#54B9A6` / `#FFFFFF` |
| `faceX` / `faceY` | `0` / `0` |
| `faceBlinkTimeMs` / `faceBlinks` / `faceHops` / `faceFloats` / `faceShakes` | `230` / `2` / `2` / `1` / `3` |
| `avatar` | `Color.Unspecified` —— 上游兜底是 `var(--color-border-hover)`（引用、随主题变），故不以固定色表达 |

**建议消费点**：这些时长与缓动曲线**不限于工作流图** —— `tFast/tBase/tEnter/tInk` 可以直接
当作全库统一的手势/展开动效梯度（目前各组件各写各的 `spring` / `tween` 参数）。

### 3.4 `CssVariables.zcodeTailwind` —— tailwind v4 色板（55）

`amber300`…`yellow600`（`ZCodeTailwindPalette.kt`）。

**说明**：上游默认主题整套建在它上面，这些值**已经折算进** 144 个颜色槽的最终取值；
留一份"原料"是为了新组件按上游口径取色时不必反推。

**建议消费点**：新组件的临时取色 / 与上游对照时的查表。

### 3.5 `CssVariables.zcodeMention` —— @提及图标槽（3）

| 项 | 兜底值 |
|---|---|
| `iconColor` | `Color.Unspecified`（上游 `transparent`）|
| `image` | `null`（上游 `none`）|
| `mask` | `null`（上游 `none`）|

**说明**：上游默认什么都不画，图标由引用方按"被提及的对象"运行时注入。
**建议消费点**：输入框的 `@` 提及功能（skill / 文件 / 会话的图标与遮罩）—— 属**新功能**，需单独立项。

---

## 4. 尚未搬入

### 4.1 滚动条样式（4 条全局规则）

上游（`styles.css` 尾部）：

```css
*                       { scrollbar-width: auto; scrollbar-color: var(--color-border) transparent; }
*::-webkit-scrollbar    { width: 14px; height: 14px; }
*::-webkit-scrollbar-track { background: transparent; }
*::-webkit-scrollbar-thumb {
  min-height: 32px; min-width: 32px;
  border: 3px solid transparent; border-radius: 9999px;
  background: var(--color-border); background-clip: padding-box;
}
```

**对应物**：`core:widgets` 里加一个遵循同口径的 Compose 滚动条
（细 thumb ✓ 圆角 ✓ 透明轨道 ✓ 14dp 宽 ✓ 32dp 最小长度 ✓ 用该套的 `border` 色 ✓）。
**这是最容易立刻见效的一项** —— jasmine 的会话列表 / 设置列表都能直接接上。

### 4.2 渐变工具类（4 个）

`@layer utilities` 里：`.button-gradient` ✓ `.animated-gradient-text` ✓ `.animated-gradient-text-subtle` ✓
`.cua-group-gradient-text` ✓（另有 `.dark .button-gradient` 的暗色覆盖 ✓）。

配合的 token：`--animated-gradient-text-strong` / `--animated-gradient-text-soft`（**已搬** ✓ 在 144 槽里 ✓）
与 `@keyframes gradient-flow` / `cua-group-gradient-flow`（见 4.3 ✓）。

**对应物**：Compose 的 `Brush.linearGradient` + `infiniteRepeatable` 动画修饰符。
**建议消费点**：主按钮的渐变底 ✓ 「思考中」文字的流动渐变 ✓（jasmine 现有对应位置能直接换上）。

### 4.3 `@keyframes`（38 个）

| 分组 | 名字 |
|---|---|
| 渐变 | `gradient-flow` `cua-group-gradient-flow` |
| 流式输出 | `zcode-stream-text-in` `zcode-stream-marker-in` `zcode-draft-prompt-waterfall` |
| 折叠 / 淡出 | `zcode-collapsible-up` `zcode-collapsible-fade-out` |
| 交互反馈 | `zcode-alarm-ring` `zcode-reaction-pop` `zcode-reaction-particles` `zcode-task-interaction-countdown` `zcode-update-charge-sweep` `task-search-result-highlight` `fork-highlight-pulse` |
| 加载 | `markdown-image-loading-shimmer` `workspace-remote-connecting-breathe` `browser-use-operation-breathe` |
| 工作流小人脸 | `wf-face-blink` `wf-face-closed-blink` `wf-face-morph` `wf-face-closed-morph` `wf-face-float` `wf-face-shake` `wf-face-dot-float` `wf-face-hop` |
| 工作流图 | `wf-land` `wf-ws-land` `wf-ws-shine` `wf-arrive` `wf-mark` `wf-unfold` `wf-swap` `wf-draw` `wf-caret` `wf-rail-grow` `wf-beat` |

**对应物**：Compose 的 `animate*AsState` / `Animatable` / `rememberInfiniteTransition` +
`keyframes {}` spec。**建议只挑有对应部件的做**（多数服务于 jasmine 还没有的
工作流图 / browser-use / CUA 面板）。

其中 `zcode-stream-text-in` / `zcode-stream-marker-in` / `markdown-image-loading-shimmer`
**与 jasmine 现有部件直接对应** ⇒ 优先级最高。

### 4.4 明确**不搬**（附理由）

| 项 | 数量 | 理由 |
|---|---|---|
| `@custom-variant platform-*` | 4 | Electron 平台适配（标题栏避让）✗ Android/Compose 无对应物 |
| `@import` / `@plugin` / `@source` | 8 | 构建指令（引入 tailwind / KaTeX / xterm）✗ 不是设计 token |
| `data-slot` | 143 | DOM 属性（shadcn 的样式钩子）✗ Compose 无对应物 |
| `--radix-*` / `--zcode-interaction-*` / `--zcode-stream-animation-delay` | 5 | **运行时注入**（Radix 与业务代码在运行时写）✗ 不是静态 token |

---

## 5. 验收口径

改动 token 或新增消费点后，按项目既定标准验证：

```powershell
cd d:\ima\minimal-hello
.\gradlew testDebugUnitTest          # 全仓单测（当前基线 157 用例 / 0 失败）
powershell -File d:\ima\.codebuddy\rel-install.ps1   # 出包 + 装机 + MD5 核对
```

相关回归测试（`core/widgets/src/test/.../theme/`）：

| 文件 | 守什么 |
|---|---|
| `ZCodeSlotsTest` | 144 槽的**数量**、**非空**、**alpha 保留**、oklab 槽有确定值、tailwind 55 支、排版 11 项、工作流 16 项、mention 3 项 |
| `PaletteConsistencyTest` | 每套调色板的 themeId 唯一、容器面不撞色、关键前景/背景对比度 ≥ 3.0（半透明按合成后判定）|
| `ContentColorForTest` | `contentColorFor` 的映射表命中与"派生色保持 Unspecified"的契约 |
| `TokenSlotsContractTest` | `surfaceBright/Dim` 的亮度方向、错误色可覆盖、`SliderState` 入参校验 |
