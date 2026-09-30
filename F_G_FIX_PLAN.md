# Phase F / G 修复方案（第三期）

> 承接 `P0_UDF_FIX_PLAN.md` 与 `P1_P2_FIX_PLAN.md`（两期均已实施完毕）。
> 本文档是对**当前代码**重新做一次全面复审后，把尚未解决的问题整理成的两个阶段。
>
> 制定日期：2026-10-01。状态：**待确认**。

## 0. 这一期是怎么来的

第一期（P0）与第二期（P1/P2 + Rust + 结构）修完之后，对仓库重新做了一次覆盖面复核，方向四个：
① `feature/**` 视图与 ViewModel 层；② `core/**` 手写 Kotlin（含数据、markdown、ui 基座）；
③ `rust/**` 全部 crate；④ 测试覆盖空白与文档一致性。

**每条发现都由我本人回到源码 `文件:行` 复核过**才写进下面（复审给的行号在 E3 拆分后普遍漂移，
只照抄会写出假方案）。下面每条都带"现状 / 为什么 / 怎么改 / 怎么验证"。

结论：**主干（MVVM+UDF 的五条约定）没有新的越界点** —— 门禁守住的规则在现状代码里依然成立，
视图层没有 Composable 直调仓库、没有顶层可观察状态、没有非生命周期感知的 `collectAsState`，
乐观写都有对应的 `*Rejected` 回滚。剩下的问题集中在这几类：

1. **Rust 会话槽的生命周期与锁边界**（回调在持锁中同步触发 → 重入死锁 / 中毒 / 嵌套 `block_on` panic）；
2. **回合收尾在错误路径上被跳过**（`?` 提前返回，槽不释放）；
3. **状态损坏型 bug**（`take_prompt_answers` 先清空再校验）；
4. **失败仍然被静默吞掉**的地方（Kotlin 有 4 处、Rust 有 4 处）；
5. **自己文档说的和实现不一致**（`BaseViewModel.onCleared`、`EventsEffect`、`ProviderDataStore`）；
6. **会永久卡住的等待**（`MermaidRenderer` 无超时且持锁）；
7. **文档与现状脱节**（GUIDE / README / MODULE_MAP 里大量已删除或从未存在的结构）；
8. **行为级测试大面积空白**（`MainViewModel`、导航与返回键、`BaseViewModel`/`EffectRunner`、
   `RustConversationStore`、`RustAgentChat` 的事件与分型映射、Rust 的并发与取消）。

分期原则：
- **Phase F = 正确性与韧性** —— 修完会改变"会不会出错"：状态损坏、卡死、泄漏、失败不可见。
- **Phase G = 可观察性、一致性与护栏** —— 修完会改变"出问题时看不看得见、以后会不会再退化"。

---

## 1. Phase F — 正确性与韧性

### F1 Rust 会话槽：生命周期与锁边界（**本期最高优先**） — ✅

> **实施记录见本节末尾**（含一处对原方案的更正）。

这一组是同一个根因的四个面：**平台回调（`on_event` / `on_store_changed`）是在持有会话槽
`std::sync::Mutex`、且处于 `block_on` 的 runtime 上下文里被同步调用的**。

**现状（证据）**
- 持锁进入回合：`rust/core/src/session/service.rs:599`（`let mut guard = slot.lock()?;`），
  回调在这段里被调：`:617`（`let mut emit = |event| sink.emit(event)`）、
  `:638-648`（`record_boundary` 里的 `notify()`）；FFI 侧 `rust/ffi/src/lib.rs:127-131` 与 `:230-234`。
- 会被同一把锁挡住的读口：`service.rs:446-452` `context_window`、`:578-586` `context_len`、`:485-491` `reasoning_effort`。
- `block_on` 在当前线程进入 runtime：`service.rs:1681-1691`。
- 平台在界面线程调用的四个入口用阻塞 `lock()`：`:425`、`:465`、`:499`、`:918`
  （而 `end_conversation` 已经专门改成 `try_lock` + 记请求，注释见 `:1210-1216`）。
- 回合收尾被 `?` 跳过：`:667`、`:669-672`、`:675`、`:676`、`:683`（`outcome?`）之后才有
  `self.release_if_detach_requested(...)`（`:687`）；`respond_to_prompts`、`recover_turn` 同构。
- 中毒被静默降级：`:578-586`、`:446-452`、`:485-491`、`:1275-1279`、`:1706-1710` 用 `.ok()` / `unwrap_or*`；
  `end_conversation` 的 `try_lock().ok()`（`:1222-1226`）把"中毒"与"拿不到"混为一谈，中毒会话永远清不掉。

**为什么是问题**
1. 平台回调里只要顺手查一次 `contextLen()` / `contextWindow()`，就是**同线程非重入死锁**（`std::sync::Mutex`）。
2. 回调里再调任何 `block_on` 方法（`send` / `set_context_window` / `respond_to_prompts`），
   Tokio 会 **panic：Cannot start a runtime from within a runtime**。
3. 回调里抛异常（uniffi 会把 Kotlin 异常转成 Rust panic）会在 `guard` 存活期间展开 →
   `attached` **永久中毒** → 这条会话在本进程内再也不能用（且见 4）。
4. 回合结束时任一处 `?` 提前返回（网络失败、落盘失败），`release_if_detach_requested` 不执行 ——
   **平台已经释放的会话仍留在内存里**（HTTP 客户端、连接池、文件句柄都还挂着），
   而且下一次 `send` 会走 `attached_slot` 拿到"仍附着"的槽，等于平台以为关掉了却还能继续跑。
5. 界面线程改档位 / 改上下文窗口 / 重新附着同一会话，会**卡住直到整轮回复结束**。

**怎么改（设计）**
- **F1a 收尾无条件执行**：把回合本身与收尾拆开 —— 先算出 `outcome`（不再用 `?` 提前返回），
  在**统一的一处**做 `release_if_detach_requested` + `record_*`，成功失败都走同一条收尾。
  落盘失败仍要把错误返回给调用方，但释放不能被它跳过。
- **F1b 回调移出持锁范围**：回合期间改为"把要回调的事件先收进本地队列，离开锁作用域之后再发"；
  或等价的显式两层结构（`{ 持锁跑 } → { 出锁回调 }`）。
  附带把"回调里不得再入 `AgentHandle`"写成接口 KDoc，并在 `block_on` 里对重入做检测：
  发现当前线程已在 runtime 内 → 返回 `AgentError::Runtime`，而不是 panic。
- **F1c 读侧统一 `try_lock`**：`context_len` / `context_window` / `reasoning_effort` 拿不到锁就返回
  "未知"（`None` / `0`），绝不阻塞；`start_conversation` / `set_context_window` /
  `set_reasoning_effort` 采用与 `end_conversation` 相同的"记请求、回合内生效"或 `try_lock` + 明确错误。
- **F1d 中毒不再无声**：读侧至少返回 `Err(AgentError::Poisoned)`；`end_conversation` 区分
  `WouldBlock` 与 `Poisoned`，中毒时也要清理（`forget`）。

**怎么验证**
- Rust 单测：① 回合中途落盘失败，断言槽已释放（下一次 `send` 得到 `NoSession`）；
  ② 在监听回调里调 `context_len`，断言不阻塞、按期返回；③ 中毒后 `end_conversation` 能清掉。
- 真机：多轮对话中改档位/窗口，界面不卡。

**实施记录**

- **F1b（回调搬出持锁范围）**：新增 `Outbox` —— 回合整段只往它里面攒事件与"存储变了"信号，
  `drop(guard)` 之后才 `flush` 给平台。这一条**同时解掉三个问题**：回调跑的时候锁已经放掉
  （不再有同线程非重入死锁）、`block_on` 也已经返回（不再有嵌套 runtime panic）、
  回调抛异常不再穿过 `MutexGuard`（不再有中毒）。事件顺序不变（通道式攒取，先事件后信号）；
  存储信号**合并成一条**（它本来就是信号，平台侧自己防抖）。
  **行为变化**：`TurnStarted` 那次"存储变了"以前在中途发、现在随整轮一起在末尾发 —— 平台侧
  只会晚一点刷新列表，语义不变。已写进 `Outbox` 的文档注释。
- **F1a（收尾不被跳过）**：`send` / `respond_to_prompts` / `recover_turn` 的回合体各自放进一个
  立即调用的闭包，`?` 只从闭包出去 —— `release_if_detach_requested` 与 `flush` 落在闭包之后，
  成功失败都执行。
- **F1c 有一处对方案的更正**：原方案说 `context_window` / `set_context_window` /
  `set_reasoning_effort` 也在界面线程上阻塞。**复核后这条不成立** —— 它们在 `RustAgentChat`
  里全部包了 `withContext(Dispatchers.IO)`，只有 `contextLen` 与 `endConversation` 是直调。
  所以只改了 `context_len`（`try_lock`，读不到报 0）；另外三个**保持阻塞**并在注释里说明理由
  （它们在 IO 线程上、且返回值必须准确，不能为了"不排队"把它变成"未知"）。
  写进方案的是"我核实过的事实"，不是复审报告的原话。
- **F1d**：`SessionSlot::try_lock_recovering` —— 中毒的锁也拿得下来（`TryLockError::Poisoned`
  取回内层数据），`end_conversation` 用它。读侧仍按"读不到"降级，在 `context_len` 的注释里
  写明这是有意取舍（它只用于诊断，不值得把返回值改成 `Result` 并一路改到 FFI）。
- **新增 4 条 Rust 测试**：失败回合仍释放（F1a）、读长度不排队（F1c）、中毒会话可释放（F1d）、
  `Outbox` 的事件/信号语义（F1b）。

### F2 `take_prompt_answers` 先校验再消费（**状态损坏**） — ✅

**现状（证据）**：`rust/core/src/thread.rs:252-258`
```rust
self.pending_prompts
    .drain(..)                     // ← 一 drop 就把整个区间移走
    .take(answers.len())
    .zip(answers.iter().cloned())
    .collect()
```
配合调用点 `rust/core/src/session/turn_input.rs:34-37`：`paired.is_empty()` 才报 `NoPromptWaiting`。

**为什么是问题**：`answers.len() == 0`（空提交）时 `take(0)` 产出空、`is_empty()` 成立 → 报错返回，
**但 `pending_prompts` 已经被整个清空**了。于是"非法输入"改掉了状态：待答提示没了、回合仍停在
paused，平台重试也只会再拿 `NoPromptWaiting` —— 这条会话的提问**永久答不上**。
`answers.len() < prompts.len()` 时多出来的提示被静默丢弃，对应的 `tool_call` 只能靠
`ensure_call_outputs_present` 兜成 aborted（`rust/core/src/context_manager/normalize.rs:29-63`）。

**怎么改**：先校验，后消费 —— `answers.len()` 与 `pending_prompts.len()` 不匹配就**不改任何状态**
直接返回错误（错误文案要区分"没有待答"与"答案数量不对"）；匹配时才整体取出。

**怎么验证**：Rust 单测三条 —— 空答案后 `pending_prompts` 不变；数量不足时不变且报错；
数量正确时正常消费。再补一条"提示数量 > 1 时必须全部取到"（这是既有注释里明说的致命场景：
少一个答案就会在历史里留下残缺的 tool_call，之后每次请求被 400 拒掉）。

**实施记录**：`take_prompt_answers` 改成 `-> Option<Vec<...>>`：数量对不上返回 `None` 且**一个都不动**
（`Some(vec![])` 表示"确实没有待答"，与 `None` 分得开）。新增
`SessionError::AnswerCountMismatch { expected, got }`，FFI 侧归 `AgentFailure::Internal`（调用时序问题，
不是网络/文件）。调用点 `turn_input.rs` 先数 `pending_prompts().count()` 再消费。
**新增 2 条单测**：数量不符时一个都不消费（空/少/多各一例 + 数量对上时按提问顺序配对）、
没有待答时的空提交**不是**数量不符。`cargo test -p jasmine-core` 40 例全绿。

### F3 `RustAgentChat.coreEvents`：Flow 必须闭合 — ✅

**现状（证据）**：`core/agent/src/main/java/com/lhzkml/jasmine/core/agent/RustAgentChat.kt:178-190`
```kotlin
val worker = CoroutineScope(Dispatchers.IO).launch {
    try { run(listener) }
    catch (failure: AgentFailure) { sink.offer(ChatEvent.Failed(...)) }
    sink.finish()          // ← 不在 finally 里，且只兜 AgentFailure
}
```

**为什么是问题**：`AgentChat.send` 的契约写明会抛 `IllegalStateException`（"会话未附着"），
而生成绑定里这几个方法并不声明 `AgentFailure` —— 这个 `catch` **恰好兜不住真正会发生的异常**。
后果双重：① 异常逃到 `CoroutineScope(Dispatchers.IO)` 这个没有 handler 的根作用域 → 未捕获异常；
② `sink.finish()` 被跳过 → 通道永不关闭 → 收集方 `for (event in events)` 永久挂起 →
ViewModel 的收集协程泄漏、界面永远停在"正在生成"。这恰好违背 D3 自己立的"终态绝不丢"前提
（终态压根没产生）。

**怎么改**：`sink.finish()` 移进 `finally`；`catch` 放宽到 `Throwable`（`CancellationException` 原样重抛），
非 `AgentFailure` 也转成 `ChatEvent.Failed`（分型 `INTERNAL`）。

**怎么验证**：`core/agent` 单测 —— 让 `run` 抛一个非 `AgentFailure` 的异常，断言 flow 仍然以
`ChatEvent.Failed` 收口并正常结束（而不是挂住）。

**实施记录**：把与句柄无关的那部分提取成顶层 `internal fun coreEventFlow(run, onAbandoned)`
（`RustAgentChat.coreEvents` 只剩"没人收时请核心收手"这一句接线）—— **提出来的直接目的就是能测**：
它只依赖 `EventListener` 与 `ChatEvent`，测试不需要真核心句柄。
`catch (failure: AgentFailure)` 放宽到 `Throwable`（`CancellationException` 原样重抛），
`sink.finish()` 移进 `finally`；`AgentFailure.toKind()` 扩成 `Throwable.toKind()`，
把 `IllegalStateException` 认成 `INTERNAL`（本地抛的调用时序问题，不是"网络失败"）。
**新增 3 条单测**（`CoreEventFlowTest`，每条都带 5s 超时 —— 这个 bug 的表现是**永久挂起**而不是报错，
没有超时的话回归时会卡死）：非 `AgentFailure` 的失败也收口、正常路径按顺序收口、
收集方中途走人时通报一次"收手"。

### F4 `BaseViewModel.onCleared` 关闭通道

**现状（证据）**：`core/ui/.../base/BaseViewModel.kt` 全类无 `onCleared` 覆写；
`:88-92` 的 KDoc 明确写"fails only once the channel is closed, **i.e. after `ViewModel.onCleared`**"，
而 `:94` 的 `trySendAction` 返回该通道的 `trySend` 结果。

**为什么是问题**：`onCleared` 只取消 `viewModelScope`（消费协程停），通道**从不 close** →
`onCleared` 之后 `trySendAction` 仍返回 `true`，动作却永远没人处理。
这正是该方法文档想避免的"静默吞掉"；而 `feature/*/test` 里大量用它的布尔值做断言，会被假 `true` 误导。

**怎么改**：覆写 `onCleared()` 关闭 `internalActionChannel` 与 `eventChannel`。
（注意保持 `sendEvent` 的同帧入队语义不变。）

**怎么验证**：`core/ui` 单测 —— 构造一个最小 VM，`onCleared` 之后 `trySendAction` 返回 `false`。

### F5 `EventsEffect`：恢复后再投递，而不是丢弃

**现状（证据）**：`core/ui/.../base/util/EventsEffect.kt:25-31`
```kotlin
viewModel.eventFlow
    .filter { lifecycleOwner.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    .onEach(handler).launchIn(this)
```
文档 `:15-17` 却写"the pending event is **delivered once the screen resumes**"。

**为什么是问题**：`eventFlow` 是 `receiveAsFlow()`（单消费者，`BaseViewModel.kt:60`），
这个链一挂上就持续消费；`filter` 只把不符合的事件滤掉 —— **事件此刻已经从通道取走**，滤掉即永久丢失。
屏幕处于 STARTED / 后台时产生的失败提示被静默吞掉，与"失败必须可观察"相悖。

**怎么改**：改为"在满足状态前挂起、恢复后再消费"（`repeatOnLifecycle(RESUMED)` 订阅，或
在 `filter` 位置改成 `currentState` 的等待再放行），保证事件被推迟而不是丢弃。

**怎么验证**：`core/ui` 单测 —— 在非 RESUMED 期投一条事件，回到 RESUMED 后 handler 收到它。

### F6 "读坏了"必须可观察（Rust 侧残留）

**现状（证据）**
- 行级坏行静默跳过：`rust/rollout/src/list.rs:222-224`（`if let Ok(parsed) = ...` 无日志）、
  `rust/rollout/src/list.rs:32-39`（`if let Some(...) = read_session(&path)` 读不出就丢掉整条会话）、
  `rust/rollout/src/usage_stats.rs:34-38`、`:83-84`。
- `usage_archive` 一行坏 JSON 让 `usage_stats()` **永久失败**且无法自愈：
  `rust/rollout/src/usage_archive.rs:46-51`（`?` 上抛）+ `:54-63`（修复用的 `write` 只在 `read` 成功后执行）。
- transcript 的兜底把"从没等到结果的调用"标成**已完成**：
  `rust/core/src/session/service.rs:1198-1206` 走 `tool_line`，而 `tool_line` 硬编码
  `tool_status: "completed"`（`:1313-1314`）；同一个文件里另外两处同类调用都显式改成 `stopped`
  （`:1153`、`:1171`）—— 只有这个兜底漏了。
- 给模型看的那个工具把 IO 失败说成"没有历史对话"：
  `rust/core/src/session/service.rs:1742-1751`（`Err(_) => Vec::new()`）→
  `rust/tools/src/list_past_conversations.rs:63-65`。

**为什么是问题**：这与 D5 明确立下的"读坏了 ≠ 空表"直接矛盾 —— 转写少几行、列表少一条会话、
用量少一份，全都没有任何信号；`usage_archive` 那条更狠：一旦坏行存在，用户永远看不到用量，
且系统自己修不回来（必须手工删文件）。transcript 那条会让界面把"被打断的调用"显示成"执行完成"。

**怎么改**
- 行级跳过：至少 `tracing::warn!` 记下"哪一行、为什么"；文件级读取失败按 D5 语义**上报为错误**
  （或至少在结果里带出"有 N 条被跳过"）。
- `usage_archive`：读侧跳过坏行并记 warn，让 `write` 有机会把存档修回去。
- transcript 的兜底循环显式给 `stopped`（`tool_line` 增加显式状态入参，不再靠默认值）。
- `ConversationsBridge`：把错误透到工具输出（"读取失败，稍后再试"），不伪装成空表。

**怎么验证**：Rust 单测 —— ① 在会话文件里插一行坏 JSON，断言 `transcript()` 仍返回其余行且
（新加的）告警可断言；② 未闭合的 `FunctionCall` 在 transcript 里是 `stopped`；
③ `usage_archive` 坏行后 `usage_stats()` 仍成功且坏行被修掉。

### F7 Kotlin 侧残留的静默吞错

**现状（证据）**（都在 `feature/main/impl/.../chat/ChatViewModel.kt` 与 provider）
- `:469-472` `runCatching { conversationStore.interruptedTurn(id) }.getOrNull()` →
  失败被当成"没有未完成回合" → `canContinue = false` → **"继续"按钮静默消失**，
  用户以为回复完整。（这条路径**不**经过 `readFailures`，是真静默。）
- `:1208` 附近 `readConversationFacts` 三个 `runCatching{}.getOrNull()` → 读失败被塞进与
  "核心从没记过"同一个 `null` → 上下文窗口被悄悄换成模型预设、档位显示成"未设置"。
- `:967` 附近 `readAllowedEfforts` → 目录读取失败变空表，而空表语义是"不限制" →
  面板**列出全部档位**，用户可能选到模型不支持的档。
- `feature/provider/impl/.../ProviderViewModel.kt:600` `loadCatalog` 失败变空表 →
  模型名 / 上下文容量静默填不出来。

**为什么是问题**：第二期把 `ConversationStore` 的读失败做成了可见（`readFailures` + Toast），
但**上面这四条绕过了那条通道**，仍然是"失败 = 业务结论"。属于同一约定的收尾没做完。

**怎么改**：给这四条各加一条**可见回流**（`Internal.*` 带失败态，或复用 `readFailures` 的机制）：
- `interruptedTurn`：把"没读到"与"确认没有"分成两态，前者不改变 `canContinue` 的乐观显示并提示一次。
- `readConversationFacts`：引入三态（已记录 / 未记录 / 读失败），读失败时不覆盖 `null` 语义。
- `readAllowedEfforts`：读失败时面板按**受限**处理（保守），并提示一次；"不限制"只留给"目录明确没声明"。
- `loadCatalog`：把失败带进 `CatalogLoaded` 的结果，界面给出提示。

**怎么验证**：`ChatViewModelTest` 各加一条 —— 让 fake 在这四个读口抛错，断言出现可见提示
且**错误的值没有被当成业务结论采用**。

### F8 `ActiveModelReceived` 补身份守卫

**现状（证据）**：`ChatViewModel.kt:431-434`
```kotlin
is ChatAction.Internal.ActiveModelReceived -> {
    updateState { copy(activeProviderId = ..., activeModelId = ...) }
}
```
对比同一文件里带守卫的兄弟落地：`:557`（`AllowedEffortsLoaded` 按 provider/model 身份丢弃）、
`:615`（`ActiveModelPersistRejected`）、`:522`（`CanContinueResolved` 按会话）。

**为什么是问题**：`handleModelSelected` 是"乐观写 + 落盘"，而数据源 `preferencesStateFlow`
是**整份** `UserPreferences` 的流 —— 任何不相关的偏好变更（主题、字体、字号）都会重发一次，
带上尚未落盘的旧 `activeModelId`。这条无守卫的落地会把刚选的模型**盖回旧值**
（落盘完成后再翻回来），表现为"选完模型界面闪回旧模型"。

**怎么改**：给 `ActiveModelReceived` 加与落盘一致的判定（例如在途 persist 期间忽略与该次乐观值
不同的旧值，或只在当前值等于该次乐观值时才接受）。

**怎么验证**：`ChatViewModelTest` —— 选中模型后投一条带旧 `activeModelId` 的
`ActiveModelReceived`，断言状态仍是刚选的那个。

---

## 2. Phase G — 可观察性、一致性与护栏

### G1 `ProviderDataStore` 读路径：与自己的文档对齐

**现状（证据）**：`core/data/.../datastore/ProviderDataStore.kt:45-53` 读失败时
`.getOrDefault(builtInProviders.list())`，而同一个类的 KDoc `:32-33` 写
"Nothing here silently replaces what it could not parse"。

**为什么是问题**：存了坏 JSON 时读出来是**出厂种子**，与"还没存过"在调用方看来完全一样，
`core` 内没有任何上报通道（不像 `ConversationStore.readFailures`）；而写路径 `:65-78`
遇到同一份坏数据却抛异常**拒绝覆盖**。于是：界面显示出厂列表 → 用户一改就被 Rejected →
用户自己的配置（含 API key）被"藏起来"，两处对同一事实判断不一致。

**怎么改**：读路径把解码失败上报（新增一条 `failures` 流，或让仓库把它变成显式状态），
成对消除"界面看起来正常、实际读的是种子"。

**怎么验证**：`core/data` 补测试（该模块目前无 `src/test`）—— 写入坏 JSON 后读，
断言上报了一次失败且没有把它当成"没存过"。

### G2 `MermaidRenderer.awaitRender` 加超时、缩短持锁

**现状（证据）**：`core/markdown/.../ui/MermaidRenderer.kt:111-134` —— `suspendCancellableCoroutine`
**没有超时**，`pageReady` 为 false 时把 `fire` 存进 `queued` 等宿主页 `onReady`；
渲染全程包在 `mutex.withLock` 里（`:81-88`）。

**为什么是问题**：页面模块始终不回调 `onReady`（assets 缺失 / 加载失败）时，`cont` 永不恢复、
`mutex` **一直被占** → 全进程后续所有 mermaid 渲染（含"保存图片"）永久排队，`MermaidImage`
停在加载态。（同文件的 `MermaidImage.awaitIdle` 有 2000ms 超时，这一层没有兜底。）

**怎么改**：给 `awaitRender` 加超时（与 idle 同量级），超时清空 `queued`、返回 `null`；
并把持锁范围收窄到只保护 `pending` / `queued` 状态，不覆盖整段等待。

**怎么验证**：`core/markdown` 补测试（该模块目前无 `src/test`）—— 让 `pageReady` 永不置位，
断言按期返回 `null` 而不是挂住。

### G3 `IncrementalMarkdownDocument.blocks` 的别名

**现状（证据）**：`core/markdown/.../IncrementalMarkdownDocument.kt:22-30` —— `blocks` 与
`append` / `finalizeStream` 返回的都是**内部同一个可变列表**；`IncrementalMarkdownParser.apply`
会就地 `subList(...).clear() + addAll(...)`（同一 crate 的 `IncrementalMarkdownParser.kt:98-103`）。

**为什么是问题**：调用方一旦保存这个 `List`，"快照"会在下一次 `append` 时被就地改掉，
违反 `List` 的不可变预期（UDF 场景下尤其危险 —— 界面上一帧的块列表会变）。

**怎么改**：对外返回 `_blocks.toList()`（或换用持久化列表），保证返回值是快照。

**怎么验证**：`core/markdown` 补测试 —— 取一次 `blocks`，再 `append`，断言先前取到的那份没变。

### G4 `chats` 里"只被打开过"的会话条目不回收

**现状（证据）**：`ChatViewModel.kt:1174`（选中冷会话时 `updateChat` 建条目）+ 
`chat/ConversationChats.kt:90`（`update` 无条件建条目）；唯一回收路径是
`ChatViewModel.kt:588` 的 `KeepWarmExpired`，而它**只在有回合结束后才排期**；另一条是删除（`:1238`）。

**为什么是问题**：只是"打开看了一眼"的会话会永久留在 `chats` 里（直到 ViewModel 销毁），
切遍 N 条会话就常驻 N 份消息列表 —— 没有回合就没有 keep-warm 计时器来清。

**怎么改**：给"仅打开、未跑回合"的条目也接入 keep-warm 计时，或对建出来的空条目做 LRU 回收；
两者取其一，保持"有回合的会话不被误清"。

**怎么验证**：`ChatViewModelTest` —— 连续打开 N 条冷会话，断言常驻条目数有上界。

### G5 文档与现状全面对齐

**现状（证据）**：复核发现文档里有大量**已被删除或从未存在**的结构，举例：
- `COMPONENT_DEVELOPMENT_GUIDE.md`：`:357`/`:481` 的 `BottomNavBar` / `ProductionBottomNavBar` /
  `nav_tab_chat`（文件不存在，`MainScreen.kt:43-46` 明说"There is no bottom navigation bar"）；
  `:121`/`:148` 的 `MainAction.TabSelected`；`:119` 的"标签页"；`:483` 的 `ChatHeaderHeight`；
  `:379-380` 的"未就绪态整屏引导页 + `ChatHeader`"；`:393-396` 的"落库 / SQLite / 外键级联"；
  `:346`/`:479` 的"屏幕左缘 32dp 边缘滑出 `SidebarEdgeZone`"（实际是 `SidebarEdgeZoneFraction = 2f/3f`）；
  `:292` 的"去 `-light`/`-dark` 后缀回退"（实际无回退）；`:485` 的"44dp 圆形发送键"
  （实际 26dp 圆角方）；`:486` 的 `ImeAction.Send`（实际 `Default`，回车换行）；
  `:161` 的"ChatViewModel 的 Event 取 `Nothing`"（实际是 `ChatUiEvent`）。
- `README.md:30` 的 "theme, typography, fonts, **tab**, sidebar"；`:33` 的 "wires the **database**"。
- `rust/MODULE_MAP.md:64` 与 §6.2（`:249-257`）仍把 D1–D5 列为**未做/待办**（实际已完成）；
  `:91` 引用不存在的 `codex-api/src/sse/`。
- `P0_UDF_FIX_PLAN.md:103`/`:228` 写"44 个 `ChatViewModelTest` 用例"（实际 47）。

**为什么是问题**：这些不是"不够详细"，是**断言与代码相反** —— 照着 GUIDE 找 `ChatHeaderHeight`
或按"落库"去理解持久化，会直接把人带偏；而这类文档恰恰是新人唯一的地图。

**怎么改**：逐条按现状改写；对"计划类文档里已经完成的行号快照"统一加一句读法说明
（`P1_P2_FIX_PLAN.md` §1.2 已经是"当前代码"视角，和历史记录混在一起，需要标注）。
**注意**：`P0_UDF_FIX_PLAN.md` / `P1_P2_FIX_PLAN.md` 是**已完成计划的存档**，其"现状盘点"小节
本就该保持当时的快照；本次只修**会误导人**的硬错（例数、以及 GUIDE/README/MODULE_MAP 这三份
"描述当前系统"的文档），档案类文档一律不改写历史。

**怎么验证**：把 GUIDE / README / MODULE_MAP 里每一条技术断言与代码逐条核对；
可核对的（符号是否存在、常量值）用 grep 抽查。

### G6 补齐行为级测试空白

**现状（证据）**：目前的行为级测试集中在 `ChatViewModelTest`(47)、`MvvmUdfGateTest`(10)、
`UsageStatsViewModelTest`(2)、`ProviderViewModelRollbackTest`(3)、`LanguageViewModelTest`(3)、
`EventSinkTest`(4)、`ReasoningEffortTest`(6)、app 侧 3 例。**完全没有被运行时断言覆盖的**：
- `MainViewModel`（整类零测试）：6 条偏好写的身份守卫回滚、字体删除清 `activeCustomFontId`、
  下载/导入完成与失败的 toast、派生 `resolveFontPreviews` / `resolveContentFont`；
- 导航与返回键（`MainNavHost` / `MainScreen`）：抽屉拦截返回、
  Provider 编辑态 `BackHandler` 与顶栏返回共用同一条规则（B1 修的那个真 bug 没有回归测试）、
  `MainEvent.ShowToast` 的 `formatArgs`；
- `core/ui` 的 `BaseViewModel` / `EffectRunner`：出站 FIFO、失败回流、`CancellationException` 透传、
  单消费者、`sendEvent` 同帧入队（**C1 的验收测试当时承诺了但没写**）；
- `RustConversationStore`：读失败保留上次快照 + `readFailures` 上报（测试里的 fake 全部写的
  `emptyFlow()`，这条用户可见行为没有任何运行时保护）；
- `RustAgentChat`：`toChatEvent` 的 9 个变体映射、`AgentFailure → ChatFailureKind` 四型映射、
  `endsTurn`（分型映射写错会让失败提示给错"能怎么办"）；
- Rust：`interrupt` 与取消路径、锁中毒、工具串行（`supports_parallel=false`）、
  `delete_session`、`read_lines` 的坏行处理、FFI 的 `Transport`/`Transcript`/`Internal` 三个变体；
- `core/data` / `core/markdown` / `core/network` **没有 `src/test` 目录**。

**为什么是问题**：这一系列修复的价值全靠"不变量被钉住"来保值；没有运行时断言的模块，
下次重构会以"没有任何测试变红"的方式静默退化（本期复审已经发现多处
"当初承诺的新增单测并不存在"，见上）。

**怎么改**：按"先补地基、再补业务"的顺序补：
① `core/ui`（`BaseViewModel`/`EffectRunner`）→ ② `core/agent`（`RustConversationStore`、
`RustAgentChat` 映射）→ ③ `MainViewModel` 与导航返回键 → ④ `core/data`、`core/markdown`（与 G1/G2 配套）
→ ⑤ Rust 的并发/取消/坏数据。需要时可给 `MainViewModel` 的依赖抽接口（P0 §8.4 已记录这个前提）。
**Phase F 的每一条修完，必须同时补该条的回归测试**（见各条的"怎么验证"）。

**怎么验证**：全量 `gradlew testDebugUnitTest` + `cargo test --workspace` 全绿，且新增用例数可数。

---

## 3. 实施顺序与验证

| 阶段 | 内容 | 依赖 | 验证 |
|---|---|---|---|
| F1 | Rust 会话槽生命周期与锁边界 | 无 | Rust 单测（释放不被跳过 / 读侧不阻塞 / 中毒可清）+ 真机多轮中改档位不卡 |
| F2 | `take_prompt_answers` 先校验再消费 | 无 | Rust 单测 3 条 |
| F3 | `coreEvents` 兜全部失败 + `finish()` 入 finally | 无 | `core/agent` 单测（flow 必闭合） |
| F4 | `onCleared` 关闭通道 | 无 | `core/ui` 单测 |
| F5 | `EventsEffect` 恢复后投递 | 无 | `core/ui` 单测 |
| F6 | Rust "读坏了"可观察 + transcript 兜底状态 | 无 | Rust 单测 3 条 |
| F7 | Kotlin 四处静默吞错收口 | 无 | `ChatViewModelTest` 各 1 条 |
| F8 | `ActiveModelReceived` 身份守卫 | 无 | `ChatViewModelTest` 1 条 |
| G1–G4 | 一致性 / 卡死 / 别名 / 回收 | 无 | 各自模块新增单测（`core/data`、`core/markdown`、`chat` 需先建测试源集） |
| G5 | 文档对齐 | 无 | 逐条 grep 抽查 |
| G6 | 测试空白补齐 | F、G1–G4 | 全量测试全绿 |

建议提交粒度：F1 / F2+F3 / F4+F5 / F6 / F7+F8 / G1–G4 / G5 / G6 —— 每批一个提交，各自带测试。

---

## 4. 明确不做

- **V-12 已确认"未发现"的四类**（事件即状态 / Composable 直调多方法 / VM 互引 / 绕 VM 直写仓库）：
  继续保持不处理。
- **`feature:main:impl` 直接依赖 `feature:provider:impl` / `feature:settings:impl`**：这是
  NavHost 组装对方屏幕所必需（第二期 §B5 已论证），改为只依赖 `:api` 是一次**模块边界重构**，
  与本期的"正确性"目标不同源，不混进来。
- **`MicroTexRenderer` 的进程级作用域与主线程共用锁**：代码注释已说明是为规避 native SIGBUS 的
  有意取舍，且影响是掉帧而非错误 —— 归为已知取舍，不在本期。
- **`http-client` 不认识的 method 退化成 GET、`tool_search` 的 `unreachable!`、
  `client.rs` 固定 `parallel_tool_calls=false` 与 `parallel.rs` 并存**：属于"几乎不会触发 / 设计不一致"，
  记录在案，本期不做。
- **不改档案类文档的历史快照**（见 G5 的边界说明）。
