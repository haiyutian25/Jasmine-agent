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
- ~~**F1b 回调移出持锁范围**：回合期间改为"把要回调的事件先收进本地队列，离开锁作用域之后再发"。~~
  **这一条形同废止**：事件攒到"回合结束"才发，等于整条回复一次性出现 —— 流式（实时渲染）
  正是靠"产生即发"。所以事件保持**在回合内、持锁时**就 `sink.emit`；把"回调里不得再入
  `AgentHandle`"（不得再取这把锁、不得再调 `block_on` 方法）写成 `ChatSink` 的 KDoc 契约，
  由平台侧遵守。读侧不受影响（`context_len` 已 `try_lock`，永不排队）。
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

- **F1b（回调搬出持锁范围）—— 实现错了，已彻底删除**：当时新增了一个 `Outbox`，回合整段只往
  它里面攒事件与"存储变了"信号，`drop(guard)` 之后才 `flush` 给平台。它确实解掉了三个理论风险
  （同线程非重入死锁 / 嵌套 runtime panic / 异常展开中毒），**但代价是流式没了**：真机上发一条
  消息，回复**整段一次性出现**，而不是逐字长出来 —— 攒到"回合结束"才交出去，等于把流式变成了
  轮询。产品要求是"事件产生即发、实时渲染"，所以 `Outbox` 整个删掉，恢复
  `let mut emit = |event: ChatEvent| sink.emit(event);` 与 `&|| self.notify_store_changed()`，
  三个风险改由 `ChatSink` 的 KDoc **契约**约束平台侧（回调里不得再入 `AgentHandle`）。
  `Cell` / `RefCell` 两个只服务于 `Outbox` 的 import 一并去掉。
- **F1a（收尾不被跳过）**：`send` / `respond_to_prompts` / `recover_turn` 的回合体各自放进一个
  立即调用的闭包，`?` 只从闭包出去 —— `release_if_detach_requested` 落在闭包之后，
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
- **新增 3 条 Rust 测试**：失败回合仍释放（F1a）、读长度不排队（F1c）、中毒会话可释放（F1d）。
  （原第 4 条 `Outbox` 的事件/信号语义随 `Outbox` 一起删除 —— 它测的机制已经不存在了。）

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

### F4 `BaseViewModel.onCleared` 关闭通道 — ✅

**现状（证据）**：`core/ui/.../base/BaseViewModel.kt` 全类无 `onCleared` 覆写；
`:88-92` 的 KDoc 明确写"fails only once the channel is closed, **i.e. after `ViewModel.onCleared`**"，
而 `:94` 的 `trySendAction` 返回该通道的 `trySend` 结果。

**为什么是问题**：`onCleared` 只取消 `viewModelScope`（消费协程停），通道**从不 close** →
`onCleared` 之后 `trySendAction` 仍返回 `true`，动作却永远没人处理。
这正是该方法文档想避免的"静默吞掉"；而 `feature/*/test` 里大量用它的布尔值做断言，会被假 `true` 误导。

**怎么改**：覆写 `onCleared()` 关闭 `internalActionChannel` 与 `eventChannel`。
（注意保持 `sendEvent` 的同帧入队语义不变。）

**怎么验证**：`core/ui` 单测 —— 构造一个最小 VM，`onCleared` 之后 `trySendAction` 返回 `false`。

**实施记录**：`BaseViewModel` 覆写 `onCleared()`，关掉 `internalActionChannel` 与 `eventChannel`。
**新增测试**：为 `core:ui` 建起测试源集（补 `kotlinx.coroutines.test`）`BaseViewModelTest` 3 例 ——
动作按序处理、`onCleared` 后 `trySendAction` 返回 `false`、**事件在它对应的状态落定之后才到达**
（C1 当初承诺过但没写的那条）。

### F5 `EventsEffect`：恢复后再投递，而不是丢弃 — ✅

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

**实施记录**：`filter` 换成 `repeatOnLifecycle(Lifecycle.State.RESUMED)`。停驻时内层块被取消、
事件**留在无界通道里**，回到 RESUMED 重新订阅时一件不落地补上 —— 这就是"推迟"与"丢弃"的差别。
与句柄/Composable 无关的那部分提成 `internal suspend fun collectEventsWhileResumed(events, lifecycle, handler)`
以便单测；`EventsEffect` 另外用 `rememberUpdatedState` 让重组合后的 handler 立刻生效。
**新增测试** `EventsEffectTest` 1 例：前台当场收到 → 退到 STARTED 期间产生的事件**不消费也不丢** →
回到 RESUMED 补上。（用旧的 `filter` 写法这条会红。）

### F6 "读坏了"必须可观察（Rust 侧残留） — ✅

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

**实施记录**
- **行级坏行**（`rollout/src/list.rs` 的 `read_lines`）：跳过 + `tracing::warn!`（带文件名与行号）。
  一行坏掉不能带走整个文件 —— 后面的行还是用户的记录。
- **整条会话读不出**（`rollout/src/list.rs` 的 `list_sessions`）：跳过 + warn。
  一条会话读不出来也不带走整张列表（那会让用户以为历史全没了），但**必须留痕**。
- **用量存档坏行**（`rollout/src/usage_archive.rs` 的 `read`）：跳过 + warn —— 这条是**真行为修复**，
  不只是日志：`usage_stats` 最后会把存档整体写回，于是坏行在第一次成功刷新后就被修掉。
  以前 `read` 用 `?` 直接上抛，`write` 因此永远走不到，存档再也回不来（必须人工删文件）。
  `archive_path` 改 `pub(crate)` 供测试使用。
- **transcript 兜底状态**（`core/src/session/service.rs`）：那个"从没等到结果的调用"的兜底循环
  显式覆写成 `stopped`（同文件另外两处同类兜底本来就标了 stopped，只有它靠 `tool_line` 的默认值）。
  同时把 `tool_line` 的注释改成"成对落下的才是 completed，没配对的由调用方覆写"。
- **给模型看的那个工具**：`ConversationTitles::conversations` 改为 `Result<Vec<_>, String>`，
  `ConversationsBridge` 把 IO 错误透上去，工具则答"读不出来，让用户重试"——
  以前 IO 失败被 `Err(_) => Vec::new()` 抹成"没有历史对话"，模型会照着告诉用户"你以前没聊过"。
- **新增 3 条测试**：用量存档坏行被跳过且被修好（`usage_stats_tests`）、
  未闭合的 `FunctionCall` 在转写里是 `stopped`（`service_tests`）、
  列表读坏时工具不许答成"没有历史"（`list_past_conversations` 的 tests）。
- **取舍说明**：`list.rs` 两处与 `usage_archive` 的"跳过"选的是 **warn + 跳过**而不是"整体报错" ——
  换成报错会让一个坏文件带走整张列表/整份用量，比少几行更糟。这个选择写在了各自的注释里。

### F7 Kotlin 侧残留的静默吞错 — ✅

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

**实施记录**
- 新增 `reportReadFailure(messageRes, error)`：记一条 `Log.w` + 发一次 `ChatUiEvent.ShowToast`。
- `readConversationFacts` → `ConversationFacts?`（三个读口任一失败就返回 `null` + 提示）；
  `readAllowedEfforts` → `List<String>?`；`loadCatalog`（provider）→ `Map<..>?`。
- 新增两个"读 + 回流"的小助手 `refreshConversationFacts` / `refreshAllowedEfforts`，
  把 6 个调用点收成一行，guard 只写一次（读不出来就**不回流**，保持现状而不是写一个假结论）。
- `interruptedTurn`：`Result` 显式分叉 —— 失败只提示，**不发** `CanContinueResolved`。
- 新增中英各 3 条文案（会话设置 / 档位目录 / 未完成回合）+ provider 侧 1 条。
- **一处对方案的削弱，写在这里**：原方案说"读失败时面板按**受限**处理（保守）"。要做到那样得把
  `allowedEfforts` 变成三态一路改到面板，收益很小；实际做法是**不覆盖、不回流 + 提示一次**。
  对新开的会话，"保持现状"仍然等于"不限制"，所以那一步的保护是**提示可见**而不是收紧 —— 如实记下。
- **未新增单测**：这四处要触发需要 fake 在这些读口抛错，而现有三个 fake 的对应方法都不抛。
  按 G6 的计划，"给 fake 加抛错开关 + 各加一条"归到 G6 一起做（避免同一处返工两遍）。

### F8 `ActiveModelReceived` 补身份守卫 — ✅

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

**实施记录**：新增影子状态 `pendingActiveModel: Pair<String, String>?`（在途的那次模型落盘）。
`handleModelSelected` 发 Effect 前置上它；`ActiveModelPersistRejected` 里解除；`ActiveModelReceived`
在它非空且来值不同时**直接丢弃**（`return`），相等则解除并照收。
顺带把这枚新影子字段加进门禁 R8 的正则（它以前不在护栏视野里）—— 两个写点一个在
`handleModelSelected`、一个在 `handleAction`，都在白名单内。
**新增 1 条单测**：选中 model-2 → 投一条旧的 `ActiveModelReceived(model-1)` → 状态仍是 model-2；
再投权威值 → 照收。`ChatViewModelTest` 48 例、门禁 10/10。

---

## 2. Phase G — 可观察性、一致性与护栏

### G1 `ProviderDataStore` 读路径：与自己的文档对齐 — ✅

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

**实施记录**

- `ProviderDataStore` 新增 `private val failures = MutableSharedFlow<String>(extraBufferCapacity = 1)`
  与 `val readFailures: Flow<String>`；读路径 `.getOrDefault(...)` 改成 `.getOrElse { failures.tryEmit(原因) ; builtInProviders.list() }`
  —— 失败**先上报再**回落到出厂清单（顺序上保证"报"不被回落吞掉）。
- `ProviderRepository` 接口加 `val readFailures: Flow<String>`，Impl 直接转发 DataStore 的那条流。
- `ProviderViewModel` 构造期收集它：`Log.w(TAG, ...)` + `ProviderEvent.ShowToast(R.string.provider_read_failed_toast)`
  （中英各一条：`没能读到你保存的供应商配置，先显示出厂清单。`）。
- 两个测试替身（`ChatViewModelTest.FakeProviderRepository`、`ProviderViewModelRollbackTest.FakeProviderRepository`）
  补 `override val readFailures: Flow<String> = emptyFlow()`。
- **未做方案里那条验证**：`core/data` 至今没有 `src/test`，而 `ProviderDataStore` 要 `Context` + 真 DataStore
  才起得来（纯 JVM 单测不行）—— 已如实归入 G6（需 Robolectric）。

### G2 `MermaidRenderer.awaitRender` 加超时、缩短持锁 — ✅

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

**实施记录**

- 文件级 `private const val PAGE_READY_TIMEOUT_MS = 5_000L`；`awaitRender` 整体包进
  `withTimeoutOrNull(PAGE_READY_TIMEOUT_MS) { suspendCancellableCoroutine { ... } }`
  —— 超时即返回 `null`（调用方按渲染失败处理），持锁的 `render` 因此**必然**能返回。
- `invokeOnCancellation` 里除 `pending = null` 外**同时清掉 `queued`**：否则超时之后那次攒下的调用
  会在页面终于就绪时突然发出去（已经没人等的渲染指令）。
- **对原方案的一处更正**：方案里"把持锁范围收窄到只保护 `pending` / `queued`"**没有做**，也不该做 ——
  那把锁保护的是"全进程唯一的那个 WebView + 它的 `pending`/`queued`"，渲染本来就是与它的顺序交互；
  缩锁只会把并发放进来（同一时刻两条 `evaluateJavascript` 打同一个 WebView）。造成**永久**排队的根因是
  等待**无上界**，超时把它解掉了。
- **未做方案里那条验证**：要真 `WebView`（纯 JVM / Robolectric 都起不了）—— 归入 G6；后来**仍未做**
  （见 G6 实施记录：mermaid 超时只能在仪器化测试 / 真机上验）。

### G3 `IncrementalMarkdownDocument.blocks` 的别名 — ✅

**现状（证据）**：`core/markdown/.../IncrementalMarkdownDocument.kt:22-30` —— `blocks` 与
`append` / `finalizeStream` 返回的都是**内部同一个可变列表**；`IncrementalMarkdownParser.apply`
会就地 `subList(...).clear() + addAll(...)`（同一 crate 的 `IncrementalMarkdownParser.kt:98-103`）。

**为什么是问题**：调用方一旦保存这个 `List`，"快照"会在下一次 `append` 时被就地改掉，
违反 `List` 的不可变预期（UDF 场景下尤其危险 —— 界面上一帧的块列表会变）。

**怎么改**：对外返回 `_blocks.toList()`（或换用持久化列表），保证返回值是快照。

**怎么验证**：`core/markdown` 补测试 —— 取一次 `blocks`，再 `append`，断言先前取到的那份没变。

**实施记录**：`val blocks: List<MarkdownBlock> get() = _blocks.toList()`（对外只给**快照**）。
`append` / `finalizeStream` 仍返回内部那份（它们是"本次追加的结果"，调用方立刻取用；而且"就地改"正是
它们与 `blocks` 的约定）。**方案里那条验证**（取一次 `blocks`、再 `append`、断言先前那份没变）**仍未做**：
`IncrementalMarkdownDocument` 的解析器是 JNI 的（构造时就 `System.loadLibrary`），纯 JVM 单测加载
不了 `.so`，只能在设备/仪器化测试里做。不过这一族里**活在调用链上的两个纯函数**后来补上了断言
（`IncrementalBlocksTest` 6 例，见 G6 实施记录）。

### G4 `chats` 里"只被打开过"的会话条目不回收 — ✅

**现状（证据）**：`ChatViewModel.kt:1174`（选中冷会话时 `updateChat` 建条目）+ 
`chat/ConversationChats.kt:90`（`update` 无条件建条目）；唯一回收路径是
`ChatViewModel.kt:588` 的 `KeepWarmExpired`，而它**只在有回合结束后才排期**；另一条是删除（`:1238`）。

**为什么是问题**：只是"打开看了一眼"的会话会永久留在 `chats` 里（直到 ViewModel 销毁），
切遍 N 条会话就常驻 N 份消息列表 —— 没有回合就没有 keep-warm 计时器来清。

**怎么改**：给"仅打开、未跑回合"的条目也接入 keep-warm 计时，或对建出来的空条目做 LRU 回收；
两者取其一，保持"有回合的会话不被误清"。

**怎么验证**：`ChatViewModelTest` —— 连续打开 N 条冷会话，断言常驻条目数有上界。

**实施记录**

- 排期点从"进入 + 回合收尾"改成**"离开 + 回合收尾"**：`handleConversationSelected` 与
  `handleNewConversation` 在换掉当前会话时，对 `displayKey()`（**离开**的那一份，含"新建但还没发第一条
  消息"那份 `""`）调 `keepWarm(leftKey)`；`endTurnKeepingWarm` 因此改名 `keepWarm`（现在不止一个排期点）。
- **为什么不是"进入时排期"**（第一版这么写的，测试直接把它逮住了）：正显示着的那份到期时，
  `KeepWarmExpired` 的守卫（`displayKey() != key`）**一定**放它过去 —— 计时器就此消耗掉，此后不会再有第二次。
  于是"一直看着它超过 30 秒再切走"这条常见路径会把这一条永久留在 `chats` 里，正是 G4 要治的病。
  按**离开**排期则保证"不再显示"之后必然还有一次到期；到期那一刻它若又跑了新一轮、或被切回来，
  守卫照旧把它留下（"有回合的会话不被误清"这条不变量不破）。
- 验证：`ChatViewModelTest` 新增 `a conversation that was only opened is released once it goes cold` ——
  先只把**回读**推完（`advanceTimeBy(10)`，keep-warm 是 30 秒，这一拍到不了），切走之后再推到空闲；
  断言"冷掉之后回去会**重新回读一次**转写"。
  没有用方案里写的"常驻条目数有上界"：`chats` 对测试不可见，而"重读一次"正是"内存里那份已经没了"的
  直接可观测后果（`FakeConversationStore` 记 `messagesOfCalls`）。
- ⚠️ 写这个用例时必须知道的事：`advanceUntilIdle()` **不是**"推进一点点"，它会把 30 秒的计时器一并跑完。
  第一步排查时正是踩了这个 —— 第一版用例在"还显示着 c1"的那一帧把计时器跑掉了，于是后面怎么等都等不到回收。

### G5 文档与现状全面对齐 — ✅

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

**实施记录**

三份"描述当前系统"的文档逐条回源码核对并改写（核对日期 2026-10-01）：

- **`COMPONENT_DEVELOPMENT_GUIDE.md`**
  - **第 8 节"底部导航栏 (BottomNavBar)"整节删除**（`BottomNavBar.kt` / `ProductionBottomNavBar` /
    `NavigationTab` / `nav_tab_chat` 全都不存在），目录与后文小节号 9–14 顺次前移为 8–13，
    交叉引用（"第 13 节速查表"、"见 11.2"、TOC 锚点）同步改掉。
  - `MainAction.TabSelected` → 实际 action（`SidebarOpened/Closed/Toggled`、`ThemeSelected` 等）；
    "标签页 / currentTab / CANVAS 单标签"这类说法一律去掉。
  - `ChatViewModel` 的 Event：`Nothing` → `ChatUiEvent`（`ShowToast` / `ShowError`）。
  - 对话界面一章按现状重写：没有 `ChatHeader`（那行 48dp 已整体删除）、没有"未就绪态整屏引导页"
    （未就绪时照常渲染，发送被 `handleSendClicked` 静默忽略）、第 10.3 节的"落库 / SQLite / 外键级联 /
    历史面板 BottomSheet"整段换成事实：**本项目没有数据库**，转写由核心写 rollout 只追加 JSONL，
    历史列表在侧边栏（标题 + 最后一条消息时间），删除走核心 `delete_conversation`。
  - `familyOf` 的"去 `-light`/`-dark` 后缀回退" → 实际只在 `families` 里精确匹配；
    "屏幕左缘 32dp 边缘滑出（`SidebarEdgeZone`）" → `SidebarEdgeZoneFraction = 2f/3f`（按比例）。
  - 速查表：删掉"底栏分页滑动"与"对话头部行高"两行；发送键 44dp → **26dp**（图标 14dp）；
    输入框 `ImeAction.Send` → **`ImeAction.Default`（回车换行）**；补上 `KEEP_WARM_MS = 30s`。
- **`README.md`**：`MainViewModel` 的 "tab" 去掉（并说明单顶层界面、没有底栏）；
  "Hilt wires the **database**" → 无数据库模块（转写在核心 rollout、配置在 DataStore）；
  Testing 一节补 `core:ui` / `core:agent` 两条命令与总例数。
- **`rust/MODULE_MAP.md`**：`:64` 与 §6.2 的 D1–D5"未做/待办"改成**已完成**（保留原文动机 + 落点）；
  补 `jasmine-client` crate 一行（原来整表漏了它）；`codex-api/src/sse/` → jasmine 自己的
  `api/src/sse/`（`chat_completions.rs` / `responses.rs`）；`JasmineTools.kt` 与三个 `*Wire.kt` /
  `OpenAiChatWire.kt` 标注"旧引擎（已删）"（§0 的读法规则要求这么标，原表漏标）；
  `cargo test --workspace` 例数 176 → **186**；`AgentHandle` 方法表补 `shutdown`。
- **`P0_UDF_FIX_PLAN.md`**：不改写历史，只在开头加一段"读法"：本文是已完成计划的存档，
  行号与例数都是**制定当时**的快照（"44 个 `ChatViewModelTest` 用例"当时确实是 44，现在是 49）；
  要了解当前系统请读 GUIDE / README / MODULE_MAP。
- 顺带修**一处代码注释**（不是文档文件，但同属"断言与代码相反"）：`ChatScreen.kt` 的 KDoc 写
  "没有可用模型时转写被整屏换成引导页"——实际没有这个界面，已改为"照常渲染，发送被忽略"。

**没有改的**：`MODULE_MAP.md` 里 `codex_thread.rs` 一类**参照实现**的文件名（对照表另一栏，
不是本仓库路径）；`P1_P2_FIX_PLAN.md` / `P0_UDF_FIX_PLAN.md` 正文里的历史快照（按方案要求不改写历史）。

**怎么验证**：`grep -n` 抽查已删符号（`BottomNavBar` / `TabSelected` / `ChatHeaderHeight` /
`SidebarEdgeZone` / `44dp` / `ImeAction.Send` / `取 Nothing`）在这三份文档里只剩"说明其不存在"的句子；
其余常量（47dp / 295dp / 320ms / 280ms / 26dp / 30s / 60 字符）与源码逐个对上。

### G6 补齐行为级测试空白 — ✅（两项如实顺延，见实施记录）

**现状（证据）**：目前的行为级测试集中在 `ChatViewModelTest`(49)、`MvvmUdfGateTest`(10)、
`UsageStatsViewModelTest`(2)、`ProviderViewModelRollbackTest`(3)、`LanguageViewModelTest`(3)、
`EventSinkTest`(4)、`CoreEventFlowTest`(3)、`ReasoningEffortTest`(6)、`BaseViewModelTest`(3)、
`EventsEffectTest`(1)、app 侧 3 例（共 87 例 JVM 单测）。**完全没有被运行时断言覆盖的**：
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

**实施记录**

按"① core/ui → ② core/agent → ③ MainViewModel → ④ core/data → ⑤ Rust"的顺序补完，共 **+33 例**
（JVM 由 87 增到 **120**，Rust 由 186 增到 **189**），全量测试、`cargo fmt --check`、`cargo clippy`
（0 告警）全绿。

- **① `core/ui`（+4）**：`EffectRunnerTest` —— 命令**单消费者**（前一条没跑完，后一条不许开始）、
  成败两条路都回流成 action、**取消穿透**（`CancellationException` 不许被兜成 `*Rejected`，那会在
  ViewModel 已经销毁后回滚状态并弹一个红字）、作用域取消后 `send` 是 no-op。C1 当初承诺过这条验收
  测试却没写，这次补上。`BaseViewModelTest` / `EventsEffectTest` 已由 F4/F5 落地，不重复。
- **② `core/agent`（+12）**：
  - `RustAgentChatMappingTest`(8)：九个核心事件逐个过映射表（漏一个 = 那类事件在界面上消失）、
    回合终态真值表（**用量不是终态**、提问是终态）、`AgentFailure` 四型分型、本地
    `IllegalStateException` 算 INTERNAL、认不出来的不猜、用量带构成、未配窗口保持 null。
  - `RustConversationReadTest`(4)：会话列表**读失败保留上一次快照**、只把原因交出去、没有 message
    的异常也要有一句能显示的原因（否则等于"读成功"，界面一声不吭）。
  - 为可测做的两处**必要改动**（都保持行为不变）：`toChatEvent` / `toKind` / `endsTurn` 由 private
    提到 internal；`read()` 里"一次读盘怎么落到状态上"提成纯函数 `applyConversationRead`（真身要真
    `AgentHandle`，JVM 起不来，但判定不依赖句柄）。
- **③ `MainViewModel`（+13，放在 `app` 模块）**：整类此前零测试。覆盖偏好回灌（**侧栏是会话瞬态位置、
  不跟着回灌**）、侧栏 toggle 不碰偏好写口、**六条乐观写的身份守卫回滚**逐条 + 每条都出提示、
  迟到的失败不顶掉新选择、排版引擎那条的双字段守卫、删当前字体立刻清空选择、删别的字体不动它、
  删除失败要说、下载失败要说、下载/导入完成各有各的提示、主题派生（显式浅色不受系统深色影响 /
  跟随系统才受影响）、正文字体回落排版引擎。
  放在 `app` 是因为 `MainViewModel` 要真 `Context`（读系统深色模式），而 `app` 已经配好 Robolectric；
  字体仓库用**真的**（`filesDir` 是 Robolectric 的临时目录），配一个抛异常的假 `FontDownloadApi`
  走真实的下载失败路径。为此给 `app` 加了 `testImplementation(core:network, okhttp)`。
- **④ `core/data`（+4）**：`ProviderReadTest` —— 读路径的三岔路口（没存过 → 出厂种子且**不报**；
  存过能解 → 用用户那份；存过解不出 / 形状不对 → 出厂种子 **+ 上报原因**，G1 的行为级回归）。
  同样把判定提成纯函数 `readStoredProviders`（真身要 `Context` + 真 DataStore），`core:data` 因此
  第一次有了 `src/test`。
- **⑤ Rust（+3）**：`rollout` 三条 —— 坏一行**不带走整个文件**（后面的行照读，列表也照列）、
  整份文件读不出来时**只跳这一条**、`delete_session` 删得掉且重复删是 no-op。其余几项本来就已被
  覆盖，逐条点名：并发/不排队 `a_running_turn_does_not_hold_up_another_conversation`、
  读侧不阻塞 `reading_the_context_length_does_not_wait_for_a_running_turn`、中毒可清
  `ending_a_poisoned_conversation_still_clears_it`、释放不被跳过 `a_failed_turn_still_releases_the_conversation`、
  工具并行结果按调用顺序归位 `tools::parallel::answers_come_back_in_the_order_the_calls_were_asked`、
  坏数据自愈 `usage_stats::a_corrupt_archive_line_is_skipped_and_repaired`、FFI 的调用时序错误
  `handle_reports_call_order_errors_as_text`。

**当初顺延的两项，后来各补上了一半**（补的部分在 `d040aab` 那批里）：
1. **`core:markdown` 的 G2/G3 回归** —— 顺延的理由是 `IncrementalMarkdownDocument` 的解析器是 JNI 的
   （构造时就 `System.loadLibrary`）、`MermaidRenderer` 要真 `WebView` 与 `Looper`。
   **已补**：那一族里**真正活在调用链上的两个纯函数**可以在 JVM 里钉住 —— 新增
   `IncrementalBlocksTest`（6 例）：`IncrementalMarkdownParser.apply` 就地改（`ChatRestore` 的恢复
   路径在用）、`IncrementalMarkdownDocument.applied` **不改入参**（`ChatViewModel` 每片流式在用）、
   三分支（空增量原样返回 / 纯追加 / 中间截断重建）、以及"两条路径对同一增量结果必须一致"。
   **仍缺**：`blocks` getter 给的是不是快照（要构造实例 → 要 `System.loadLibrary`）与 G2 的
   mermaid 超时 —— 这两个只能在仪器化测试 / 真机上做。
   （顺带更正一处：`IncrementalMarkdownDocument` 的**实例**在生产代码里根本没人构造，只用伴生
   `applied` —— 所以给它注入 `MarkdownParser` 换可测性是白改，试过后已回退。）
2. **导航与返回键（`MainScreen` 抽屉拦返回、`MainNavHost` 的 `MainEvent.ShowToast` 格式化）** ——
   顺延的理由是"要 Compose UI 测试 + 一个真 `Activity`（`createAndroidComposeRule`），属于新引入的
   一类测试基建"。**基建已经建起来了**：`ProviderScreenBackTest`（2 例，Robolectric +
   `createAndroidComposeRule<ComponentActivity>()`）钉住"编辑态按系统返回 → `CancelClicked`、
   列表态不拦"，配套 `ProviderBackRuleTest`（5 例）钉住 `canNavigateBack`（= 编辑态已关）这条规则
   在三个编辑入口上都成立。
   **仍缺**：当初点名的那两个（抽屉拦返回、`ShowToast` 格式化）还没断言 —— 现在有了这套基建，
   补起来没有了障碍，但本期没做，不假装做了。

**顺带修的两处**（都在验证时撞见）：`cargo fmt --check` 此前**不一致** —— F1/F2/F6 那几批提交里
有 6 个文件没过 fmt（`service.rs` / `service_tests.rs` / `thread.rs` / `lib.rs` / `usage_stats_tests.rs` /
`list_past_conversations.rs`），而 MODULE_MAP 一直声称"fmt 一致"；已全部 fmt 并复核
（`cargo fmt --check` 现在真的干净）。`core/ui/build.gradle.kts` 里还留着一条提到已删
`BottomNavBar` 的陈旧注释，一并改掉。

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
| G1–G4 | 一致性 / 卡死 / 别名 / 回收 | 无 | G4 有 `ChatViewModelTest` 回归；G1/G2/G3 的行为级测试因需 `Context`/`WebView`/JNI 顺延到 G6（各条实施记录里已记录） |
| G5 | 文档对齐 | 无 | 逐条 grep 抽查 |
| G6 | 测试空白补齐 | F、G1–G4 | 全量测试全绿 |

建议提交粒度：F1 / F2+F3 / F4+F5 / F6 / F7+F8 / G1–G4 / G5 / G6 —— 每批一个提交，各自带测试。

**验证记录（2026-10-01，本机）**

| 项 | 命令 | 结果 |
|---|---|---|
| 全量 JVM 单测 | `gradlew testDebugUnitTest` | ✅ 全绿（**120 例**，21 个测试文件） |
| Rust 单测 | `cargo test --workspace` | ✅ 全绿（**189 例**；随 `Outbox` 删除 1 例后为 **188**，见文末复测） |
| Rust 静态检查 | `cargo fmt --check` / `cargo clippy --workspace --all-targets` | ✅ 一致 / 0 告警（F1–F6 遗留的 6 处 fmt 已在 G6 修掉） |
| debug 构建 | `gradlew :app:assembleDebug` | ✅（含 cargo-ndk 四个 ABI 的 `.so` 与 UniFFI 生成） |
| release 构建 | `gradlew :app:assembleRelease` | ✅（R8 minify + shrinkResources） |
| 真机冒烟 | Redmi 6 Pro / sakura / Android 9（`101.42.15.60:6000`） | ✅ 已做，见下 |

**真机冒烟明细（2026-10-01，覆盖安装 APK 41,659,048 B，与设备 `base.apk` MD5 一致
`4d55ed0ee8cfee1d4042d6a0621663d4`）**

| 项 | 做法 | 结果 |
|---|---|---|
| 启动 / 恢复 | 覆盖安装后拉起 → `pidof` + logcat | ✅ `Displayed +676ms`，无 FATAL/ANR；上一次的长转写（表格、LaTeX、代码块、脚注）完整还原 |
| **F1 读侧**（回合中不阻塞） | 一边流式生成，一边点开「上下文用量」面板 | ✅ 回合进行中点开即出，数据是活的（4198/100万 · 消息 89.1% / 系统提示词 6.2% / 系统工具 4.6%）；UI 无冻结 |
| **F1 写侧**（回合中改档位） | 同一条回合还在跑时打开「推理强度」面板并选「低」 | ✅ 面板立刻打开、选中后输入区的档位立刻变「低」，而那一轮仍在流式输出 |
| **F1 落盘证据** | 拉回会话文件看记录顺序 | ✅ 那一轮 `turn_started(reasoning_effort=high)` → 回合结束后 `turn_complete` → **`reasoning_effort(low)`**；下一轮 `turn_started(reasoning_effort=low)` —— 中途那次改动按"记请求、回合内生效"落地，UI 全程没卡 |
| **F3 失败不再卡「正在生成」** | 把 DeepSeek 的 API 密钥改成非法值 → 发一条 | ✅ 出现失败气泡：「网络请求失败了，可以再发一次。」+ 原始原因（`http 401 Unauthorized … api key ****999 is invalid`）；输入区**回到可用态**（不是转圈/停止），进程无 ANR。文件侧：这一轮只写了 user 的 `response_item` + `token_usage_record`，**没有伪造模型回复** |
| **G4 切遍多会话** | 冷启动后连切 6 条会话（每条都是长转写），逐次量 PSS | ⚠️ 部分：内存 91.5 MB → 102.2 MB（约 +1.8 MB/条），空闲 45s 后 98.9 MB。**没有无界增长**，但 PSS 看不出"条目被回收"这一下课——JVM 释放的对象不会立刻把页还给系统，应用侧也没有可读的计数器。回收规则本身由 `ChatViewModelTest` 的回归用例钉住 |
| 收尾 | 用安装前备份的 `model_providers.preferences_pb` 还原密钥、重启验一轮 | ✅ 文件按原属主写回（555 B，`u0_a182`），应用正常起、无 401；屏幕仍是竖屏锁（`user_rotation=0`） |

> 真机操作里踩到的几条（都已写进 `.codebuddy/memory/MEMORY.md` 的「远程真机测试环境」一节）：
> `input tap` 打不到 Compose 的按钮（要点用 `input swipe x y x y 120`）；讯飞输入法会把注入的 ASCII
> 字母吃成拼音（只有数字/符号能安全注入）；输入区在"键盘开/关"两种布局下分别是 y≈1325 / y≈2100，
> 用 `dumpsys input_method` 的 `mInputShown` 判断。

**F1b 回归修复后的复测（2026-10-01）**

`Outbox` 删掉、恢复"事件产生即发"之后重新出包（release APK，与设备 `base.apk` MD5 一致
`87981515a1a55bed0af2d84bae9c707b`）装机复测：

| 项 | 做法 | 结果 |
|---|---|---|
| **流式（本次回归点）** | 发一条消息后每 ~0.9s 截一张图，连截 8 张 | ✅ 回复**逐字长出来**：第 2 张还在「正在思考」的首段，第 4/6/8 张里同一段推理已明显变长、面板持续跟随 |
| **流式（日志佐证）** | `adb logcat -s ChatScroll` | ✅ 同一轮内 `文本长=456 → 464 → 465 → 506 → … → 630`，全程 `streaming=true`，最后一次 `streaming=false`（收尾）；`已滚到末尾` 持续出现 |
| 收尾 | 等这轮跑完 | ✅ 完整回复渲染（表格 / 代码块 / 脚注都在），发送键由「停止」回到「发送」，无 FATAL/ANR |
| Rust 单测 | `cargo test --workspace` | ✅ 全绿（**188 例**，比原来少的那 1 例就是随 `Outbox` 删掉的语义测试） |

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
