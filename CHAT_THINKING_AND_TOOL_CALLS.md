# 聊天里的「思考」与「工具调用」：照 ZCode 重做的记录

本文记录 **2026-09-29** 那次「把思考部分与工具调用部分完全按 ZCode 桌面端重做」的落地情况：数据形状、
状态机、渲染分流、我们与 ZCode 的对应关系、怎么验证、以及踩过的坑。

参照实现：`d:\ima\ZCode`
- 行模型：`packages/shared/src/zcode-protocol-v4/rows.ts`（`conversationRowSchema`，9 种行）
- 分派：`packages/ui/src/v4/ConversationRowView.tsx`（`switch (row.kind)`）
- 思考块：`packages/ui/src/components/ai-elements/reasoning.tsx` + `ReasoningRowView`
- 工具卡：`packages/ui/src/ToolCallBlocks/*`（`ToolLayout` / `ToolSummaryRow` / `resolveRenderer.js` / `renderers/*`）
- 样式：`packages/ui/src/styles.css`（`animated-gradient-text`、颜色令牌）

我们这边：`feature/main/impl/src/main/java/com/lhzkml/jasmine/feature/main/impl/chat/`

---

## 一、总原则（照 ZCode 的两条）

1. **状态与时长是"行自己的数据"**，界面读字段 —— 不从"有没有结果""还在不在流"反推。
   - 工具：`ChatToolActivity.status: ChatToolStatus`（六态）。
   - 思考：`ChatMessage.thinking` + `thinkingMs`（时长在段收尾时结算）。
2. **间距只由容器给，行本身零 padding**；同一轮内的行用小间距，轮与轮之间用大间距
   （我们现有规则见 `ChatScreen.kt` 的 `ChatMessageSpacing=20dp` / `ChatIntraTurnSpacing=2dp`）。

---

## 二、思考部分

### 数据
| 字段 | 含义 |
|---|---|
| `ChatMessage.thinking` | 这一段思考的全部文字（增量累加） |
| `ChatMessage.thinkingMs` | 这一段思考花了多久；null = 还在想或没记到（段收尾时结算） |
| `ChatMessage.isStreaming` | 这一段还在流式输出（思考的"状态"由它给出：流式 / 完成） |

### 行为（逐条对应 ZCode）
| ZCode | 我们 |
|---|---|
| 空思考不渲染（`streaming && text.length === 0 → null`） | `message.thinking.isEmpty()` 就不画 |
| 流式中「正在思考」+ **扫光**（`animated-gradient-text`，4s） | `SweepText`（`TextStyle(brush = linearGradient(...))`，`ChatReasoningSweepPeriodMs = 4000`） |
| 收起时右侧**单行摘要**（最后一个非空行） | `ReasoningRow` 里 `lineSequence().lastOrNull { it.isNotBlank() }`，单行省略 |
| 完成态「思考 · 持续了 N 秒」/「持续了几秒」 | `thinkingMs` → `chat_reasoning_duration` / `_short` |
| 默认**收起**；结束**自动收起**，用户手动开过就不打扰（`autoCollapseKey`） | `expanded` + `touched` + `LaunchedEffect(isThinking)` |
| 展开时内容**吸底跟随**最新思考 | `LaunchedEffect(thinking, expanded) { thinkingScroll.scrollTo(maxValue) }` |
| 展开容器 `max-h-60` + `ml-2 border-l pl-3.5` + 纯文本 | 限高 240dp + 左导线 + 缩进 + `Text`（不走 Markdown） |
| 折叠箭头 `ChevronRight`，展开转 90° | 同（`Icons.Filled.ChevronRight` + `rotate(90f)`） |

### 实时与恢复的规则（一条，不分位置）
- **实时**：任何一段思考到达 → 写进当前段；当前没有段（上一段被工具调用收掉）→ **新开一段**
  （`ChatViewModel.appendReasoningChunk`）。
- **恢复**：会话文件里**每一段推理 = 转写里独立一行**，位置就是它在文件里的位置
  （`rust/core/src/session/service.rs` 的 `transcript()`：`ResponseItem::Reasoning` 直接 `lines.push`，
  不"攒着"、不挂到别的行上）。
- 因此任意多段、每段一块、位置逐段对应，实时与重启后一致。

---

## 三、工具调用部分

### 状态机（六态，取值与 ZCode 的 `chat.toolCall.status.*` 一一对应）
`pending` / `running` / `completed` / `failed` / `denied` / `stopped`

数据流（**端到端都是数据，不反推**）：

```
Rust   HistoryEntry.tool_status: String            （transcript 生成时写死）
         ├─ 成对落下的调用+结果            → "completed"
         ├─ 回合被打断 / 下一条用户消息到达 → "stopped"（没等到返回的调用也收口成一行，不再凭空消失）
         └─ 非工具行                      → ""
  ↓ ffi 镜像 HistoryEntry（同名字段）+ 映射
Kotlin TranscriptMessage.toolStatus: String
  ↓ ChatViewModel.toChatMessage：字符串 → ChatToolStatus（六态，缺省 COMPLETED）
Kotlin ChatToolActivity.status
  ↓ 界面读字段
ChatScreen.ToolActivityRow
```

实时路径由 ViewModel 推进：`ToolCalled → RUNNING`、`ToolReturned → COMPLETED`、
回合失败 → `FAILED`（`markOpenTools`）、用户停止 → `STOPPED`。

### 卡头（照 `ToolSummaryRow`）
图标 → **kindLabel**（running 时扫光「调用工具」，完成后安静「已调用」）→ **工具名**（较亮）→
**状态词**（等待中 / 执行中 / 已执行 / 执行失败 / 已拒绝 / 已停止）→ 右侧 `ChevronRight`（展开转 90°）。

### 展开内容：按工具名分流（照 `resolveToolCallRenderer`）
我们的实现：`ToolCallRenderers.kt`

| `ToolCallKind` | 渲染器 | 认领的工具名（部分） | 画法 |
|---|---|---|---|
| `DIFF` | `DiffContent` | `edit` / `write` / `apply_patch` / `str_replace*` / `create_file` / `multiedit` | `+N -M` 头部 + 补丁块（加行主色 / 减行淡色 / 等宽） |
| `TODO` | `TodoContent` | `todo*` / `update_plan` / `set_tasks` / `plan` | `完成数/总数` + 一行一项（完成的点变主色、文案转淡） |
| `TERMINAL` | `TerminalContent` | `bash` / `shell` / `exec(ute)*` / `run*` / `terminal` / `local_shell` | `$ 命令` 一行 + 输出块（等宽、浅底） |
| `PLAIN` | `PlainContent` | 其余全部 | 参数 + 结果原文 |

**新增一个工具类型**：加一个 `ToolCallKind`、把工具名写进 `toolCallKindOf`、在 `ToolCallDetail` 里
分到渲染器即可 —— 卡头、状态机、展开/收起都不用动。

载荷解析都在同一个文件里（`patchTextOf` / `todoItemsOf` / `commandOf` / `stringFieldOf` / `unescape`）：
现在从**参数 JSON 文本**与**结果文本**里取（`patch` / `diff` / `new_string` / `command` / `cmd` 字段、
`- [x] 文本` 清单行、`{"status","content"}` 对）。等以后工具真的产出结构化载荷（diff / todo 对象），
只需替换这几个解析函数，渲染器与分流一行动不用改。

---

## 四、两处**没有照搬**（环境差异，不是遗漏）

1. 折叠箭头在 ZCode 是"**收起时 hover 才显形**"（桌面端有 hover）→ 触屏没有 hover，我们保持常显。
2. ZCode 的每类渲染器都对应一种**结构化载荷**（patch / todo / 命令输出）；我们的工具现在只产出
   "参数 + 结果"文本，所以解析走文本兜底（见上一节末），渲染分流本身已按 ZCode 建好。

---

## 五、怎么验证

```powershell
cd d:\ima\minimal-hello\rust; cargo test --workspace --quiet      # 期望 147 passed / 0 failed
cd d:\ima\minimal-hello
.\gradlew.bat :feature:main:impl:compileDebugKotlin --console=plain
.\gradlew.bat :app:assembleRelease --console=plain   # 完整构建（含 Rust 交叉编译）
adb -s <设备> install -r app\build\outputs\apk\release\app-release.apk
```

- 改了 **Rust** 时：必须完整构建，并核对包内 `arm64-v8a/libjasmine_ffi.so` 的哈希**变了**；
  若 `buildRustCore` 被判 `UP-TO-DATE`，先看 `core/agent/build/rust/jniLibs` 的时间戳是否已更新
  （更新了就是合法的 up-to-date，不必强编）。
- 改了 **ffi 结构体**（如 `HistoryEntry`）时：`generateRustBindings` 可能仍判 up-to-date →
  用 `.\gradlew.bat :core:agent:buildRustHostLib :core:agent:generateRustBindings --rerun-tasks` 强制重生成，
  再确认生成的 `jasmine_ffi.kt` 里出现了新字段。
- 界面验证：发一条会调工具的 → 卡头应是**扫光「调用工具」· 工具名 · 执行中** → 完成后
  **「已调用」· 工具名 · 已执行**；展开看分流后的内容；同一轮的思考行应有扫光「正在思考」+ 右侧摘要，
  完成后「思考 · 持续了 N 秒」。

---

## 六、踩过的坑（留个记性）

1. **并发编辑同一文件会互相覆盖** → 出现"注释未闭合""文件被写坏"。同文件的多次替换要串行；
   真写坏了就整份重写。
2. **Kotlin 注释里不能出现 `/*`**（`renderers/*.tsx` 这种写法会被当嵌套注释开始 → `Unclosed comment`）。
3. **插入代码块时别把 `@Composable` 留在插入物上方**（注解会落到新加的 `val` 上，报
   "annotation is not applicable to top level property"）。
4. **`generateRustBindings` / `buildRustCore` 可能误判 up-to-date**（Rust 源码变了但任务没跑）→
   先看产物时间戳，必要时 `--rerun-tasks` 强制重跑。
