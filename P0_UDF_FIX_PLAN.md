# P0 架构修复方案：UDF 彻底治理

> 本文档是 P0 级架构问题（状态多真源 / 影子状态 / 副作用绕过 / 乐观写无回滚）的完整修复方案。
> 源码注释中引用的旧外部"修复方案"（§2.3 / §3 / §4.3，未入库、已不可考）由本文档取代并扩展。
>
> 制定日期：2026-09-30。状态：**已评审，实施中**。

## 0. 背景

架构审计（2026-09-30）确认：项目的 MVVM+UDF 主干真实存在且被 `MvvmUdfGateTest` 门禁守护
（单一 `updateState` 写入点、`Internal.*` 回流、handler 纯同步），但门禁是源码正则，其覆盖
边界恰好是问题集中的地方。本方案只覆盖 P0 级问题；P1/P2 级（View 层旁路、文档脱节等）另案处理。

## 1. 问题定义（P0）

### P0-1 状态四份真源，靠手动投影同步

会话消息的同一份事实存四份：Rust rollout JSONL（真源）→ Rust 内存 `Attached.history` →
Kotlin `ConversationChats` 内存 Map → `ChatState.conversation` 手动投影
（`ChatViewModel.updateChat` 里 `if (key == displayKey())` 才投影）。投影靠人工在每个写点
对齐，漏一处就是"内存有、界面没"。会话列表则是 poll 模型：Rust 写文件不通知 Kotlin，靠
`endTurn` 等三处手动 `refresh()` 维持镜像，跑 turn 期间列表陈旧。

### P0-2 影子可变状态在协程中改写，门禁管不到

`ChatViewModel` 的行为状态不在 StateFlow 里：`attachedKeys`（suspend 中写：
runTurn / runContinuedTurn）、`pendingContextWindow/pendingReasoningEffort`（handler 写、
suspend 中清）、`conversationEpoch`（handler 写，已合规）、`languagePreference`（handler 写，
已合规）、`ConversationChats.drop`（keep-warm 协程里写）。门禁⑥的正则只认
`updateState/updateEditor/mutableStateFlow.update`，这些写点全部漏网；安全性依赖
"碰巧都在 Main 调度器串行"这一隐含前提。

### P0-3 handler 不纯：副作用绕过自己定的 Effect 协议

`ChatViewModel` 声明"出站命令走 Effect 是唯一旁路出口"，但 `releaseConversation` 在 handler
里同步直调 `agentChat.endConversation()`（失败无回流）；`LanguageViewModel` 的 handler 同步
调 `AppCompatDelegate.setApplicationLocales`（apply）与 `getApplicationLocales`（current）；
`MainViewModel.handleFontDeleteClicked` 在 handler 里同步 `customFontFamilyCache.evict`。
这些副作用失败时无法经统一兜底回流。

### P0-4 乐观写不回滚，同项目两套标准并存

`ChatViewModel` 对写核心失败有完整回退（`ContextWindowRejected`/`ReasoningEffortRejected`
带回权威值）；但 `ProviderViewModel` 的删除/保存、`MainViewModel` 的全部偏好写、
`ChatViewModel.handleModelSelected` 的 `updateActiveModel` 都是"乐观写 + 失败仅 toast"：
DataStore 未变则 StateFlow 去重不回灌，UI 永久停在未落盘的假象上。另有一个存储层空洞：
`ProviderDataStore` 解码失败时**静默跳过写入**（`return@edit`），既无异常又无回灌。

## 2. 设计决策总览

| # | 决策 | 治哪个 |
|---|---|---|
| D1 | `ConversationChats` 改为 `StateFlow<Map>`，`ChatState.conversation` 由 `combine` 派生，删除手动投影 | P0-1 |
| D2 | 影子状态字段全部收编：写只能发生在同步 handler 白名单内；门禁新增正则守护 | P0-2 |
| D3 | 新增 `core:ui` 通用 `EffectRunner`；全部出站调用（含 endConversation、平台 API、缓存驱逐）走 Effect | P0-3 |
| D4 | 统一"乐观写 + 守卫回滚"：所有先写状态后落盘的操作必须带 Rejected 回执，handler 按身份守卫回滚 | P0-4 |
| D5 | Rust 侧新增 `ConversationStoreListener` 推送 + `AgentChatService` 单例共享，取代手动 refresh | P0-1（Rust 半边） |

顺序：D2 → D3 → D4 → D1 → D5。D1 改动面最大（动 `stateFlow` 构造），放在写点全部收编之后做，
它就是纯结构变换；D5 涉及 Rust 重建，放最后。

## 3. 详细设计

### 3.1 D1：派生投影（消除手动同步）

**BaseViewModel 改造**（core:ui）：

- `stateFlow` 由 `val` 改为 `open val`（默认 `mutableStateFlow.asStateFlow()`）；
- `state` 由 `protected val` 改为 `protected open val`（默认 `mutableStateFlow.value`）。

这是基座唯一改动，其余 ViewModel 不受影响。

**ConversationChats 改造**（feature:main:impl）：

`mutableMapOf` → `MutableStateFlow<Map<String, ConversationChatState>>`；`update/drop/rekey/has/stateOf`
语义不变，内部实现改为 `flow.update { ... }`。**写入仍然只允许发生在同步 handler**（门禁守护）。

**ChatViewModel 改造**：

```kotlin
// 私有：每会话状态的真身（唯一写入点 updateChat/drop/rekey，全部在同步 handler 里）
private val chatsFlow = MutableStateFlow<Map<String, ConversationChatState>>(emptyMap())

// 公开：全局状态 ⊕ 当前会话投影，由 combine 派生——不存在"忘了投影"
override val stateFlow: StateFlow<ChatState> =
    combine(mutableStateFlow, chatsFlow) { g, chats -> project(g, chats) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, project(mutableStateFlow.value, chatsFlow.value))

// handler 内的读-判-写同帧保证：同步重建，不经过 combine 的调度间隙
override val state: ChatState get() = project(mutableStateFlow.value, chatsFlow.value)

private fun project(g: ChatState, chats: Map<String, ConversationChatState>): ChatState =
    g.copy(conversation = chats[g.activeConversationId ?: ConversationChats.NEW_CONVERSATION]
        ?: ConversationChatState())
```

- `updateChat` 只写 `chatsFlow`，删除 `if (key == displayKey()) updateState { copy(conversation=...) }`；
- `publishChat` 整个删除（切会话只写 `activeConversationId`，投影自动跟上）；
- `ChatState.conversation` 字段保留（UI 形状不变），但只能由 `project` 填充——门禁新增规则
  **R9：`copy(conversation` 禁止出现在 ChatViewModel.kt**；
- `turnMessages(turn)` 改读 `chatsFlow.value[turn.chatKey]`（不经过投影，无调度间隙）。

兼容性：44 个 `ChatViewModelTest` 用例用 `trySendAction` + `advanceUntilIdle()` +
`stateFlow.value` 断言；`UnconfinedTestDispatcher` 下 combine 同步重算，无需改动测试。

### 3.2 D2：影子状态收编 + 门禁扩展

原则：**ViewModel 允许拥有不进 UI StateFlow 的"内部注册表"，但其写入只能发生在同步 handler
（白名单）内——与 updateState 同一条规矩，且由门禁正则守护。**

逐字段处置：

| 字段 | 现状非法写点 | 修复 |
|---|---|---|
| `attachedKeys` | `runTurn` / `runContinuedTurn`（suspend） | 附着成功后 `sendAction(Internal.ConversationAttached(id, key))`，handler 同步写；协程内不再写 |
| `pendingContextWindow` | `syncContextWindowAfterAttach` 里清除（suspend） | 新增 `Internal.PendingContextWindowConsumed(value)`：handler 里 `if (pendingContextWindow == value) pendingContextWindow = null`（等值守卫，防清掉竞态新值） |
| `pendingReasoningEffort` | `syncReasoningEffortAfterAttach` 里清除（suspend） | 同上：`Internal.PendingReasoningEffortConsumed(value)` |
| `chats.drop`（keep-warm） | `endTurnKeepingWarm` 协程里 delay 后直删 | delay 后 `sendAction(Internal.KeepWarmExpired(key))`；handler 同帧读-判-写：`*if* 无在跑轮次 *且* 非当前显示 → drop` |
| `conversationEpoch`、`languagePreference(+Seen)`、`turns`、`Turn` 可变字段 | 写点已全在 handler | 不动，纳入门禁 |

门禁新增（MvvmUdfGateTest）：

- **R8 影子写字段表**：正则覆盖
  `attachedKeys[.`/`conversationEpoch =`/`pendingContextWindow =`/`pendingReasoningEffort =`/
  `languagePreference =`/`chatsFlow`（写方法）/`turns[.`——每个命中点必须 (a) 不在 suspend 函数；
  (b) 不在 launch/withContext/async 块（复用 isInsideAsyncBlock）；(c) 所在函数在白名单内。
- **R9**：禁止 `copy(conversation`（D1）。
- 白名单扩充：`handleConversationAttached`、`handlePendingContextWindowConsumed`、
  `handlePendingReasoningEffortConsumed`、`handleKeepWarmExpired`。

### 3.3 D3：统一出站 EffectRunner

新增 `core/ui/.../base/EffectRunner.kt`：

```kotlin
/**
 * 出站命令执行器（单消费者）。每条命令：成功 → perform 的返回值回流；失败 → onFailure 造
 * Rejected/Failed action 回流。状态仍只在 handler 里改。
 */
class EffectRunner<F, A>(
    scope: CoroutineScope,
    private val sendAction: suspend (A) -> Unit,
    private val perform: suspend (F) -> A?,
    private val onFailure: (F, Throwable) -> A,
) {
    private val channel = Channel<F>(Channel.UNLIMITED)
    fun send(effect: F) { channel.trySend(effect) }
    init { scope.launch { channel.consumeAsFlow().collect { effect ->
        val followUp = try { perform(effect) }
            catch (ce: CancellationException) { throw ce }
            catch (t: Throwable) { onFailure(effect, t) }
        followUp?.let { sendAction(it) }
    } } }
}
```

收编动作：

1. **ChatViewModel**：内联 EffectRunner 换成通用件（行为不变）。**实施时修正**：
   `endConversation` **不能**进 Effect 通道 —— 释放必须与下一次附着严格有序，排队期间
   `runTurn` 可能已在 IO 线程重新 `startConversation`，迟到的旧释放令会割掉新附着。
   因此 `releaseConversation`/`onCleared` 保持同步调用，但补上了 `runCatching` + 失败回流
   （`EffectFailed(END_CONVERSATION_TAG)`）——它定位为"注册表维护"（本地、幂等、无文件 IO），
   是门禁 R11 的登记白名单，而不是一条命令。
2. **LanguageViewModel**：新增 `LanguageEffect.ApplyLanguage(value)`；handler 不再同步调
   `appLanguageRepository.apply`；`SystemLocaleSettled` 改为携带平台当前值的 action
   （读取发生在 UI 侧 effect 触发处 / 或协程里读后回流），handler 只落状态。
3. **MainViewModel**：`handleFontDeleteClicked` 的 `customFontFamilyCache.evict` 移入
   `MainEffect.DeleteFont`（evict + deleteFont 一起），结果回流 `Internal.FontDeleteCompleted(success)`。
4. 门禁新增 **R11**：ViewModel 文件内，`agentChat.`、`appLanguageRepository.`、
   `customFontFamilyCache.`、`Repository` 直写调用不得出现在非 suspend 白名单函数（handler）体内。

### 3.4 D4：统一乐观写 + 守卫回滚

**唯一标准**：先写状态后落盘的操作，落盘必须走 Effect 并带回执；失败回流 `*Rejected`，
handler 按身份守卫回滚（**当前状态仍等于乐观值才回滚**，防连点竞态）+ toast。
成功侧不需要回执——仓库 StateFlow 回灌即确认。

- **MainViewModel**（6 处偏好写）：`MainEffect.PersistTheme/PersistColorMode/PersistTypography/
  PersistCustomFont/PersistFontScale/PersistAgentOutputLanguage`，各带 `optimistic` 与 `fallback`；
  对应 `Internal.*Rejected` handler：`if (state.x == optimistic) updateState { x = fallback }` + toast。
- **ProviderViewModel**：
  - 删除：`ProviderEffect.DeleteProvider(id, snapshot)` → `Internal.ProviderDeleteRejected(snapshot)`
    → 列表仍缺该 id 则按原位插回 + toast；
  - 保存：`ProviderEffect.SaveProvider(config, previousList)` → `Internal.ProviderSaveRejected(config, previousList)`
    → 恢复列表快照 + 重开编辑器（草稿 = config）+ toast。
- **ChatViewModel**：`handleModelSelected` 的 `updateActiveModel` 收编为
  `ChatEffect.PersistActiveModel(...)` → `Internal.ActiveModelPersistRejected(optimistic, fallback)`
  → 守卫回滚 + toast。
- **ProviderDataStore 静默跳过写的洞**：解码失败由 `return@edit` 改为抛
  `IllegalStateException("provider list corrupted")`——失败必须可观察，由 EffectRunner 兜底回流。

### 3.5 D5：Rust 推送取代手动 refresh（含单例共享）

前置事实（已核实）：`RustConversationStore` 与每个 `RustAgentChat` 各持一个 `AgentHandle`
（各是一个 `AgentChatService`），实例间唯一共享介质是文件系统；推送要工作，必须先单例共享。
单例共享同时修复一个既有洞：store 删除正在跑 turn 的会话时，chat 侧槽变孤儿。

1. **DI**：`AgentModule` 新增 `@Singleton AgentHandle` provider；`RustConversationStore` /
   `RustAgentChat` 注入共享 handle（`AgentChat` 接口实现仍非单例）。
2. **FFI**（rust/ffi/src/lib.rs）：新增
   `#[uniffi::export(with_foreign)] trait ConversationStoreListener: Send + Sync { fn on_store_changed(&self); }`
   与 `AgentHandle.set_store_listener(...)`（照 HostClock 常驻模式）。
3. **core**（session/service.rs）：新增 `store_listener: Mutex<Option<Arc<dyn ConversationStoreListener>>>`；
   在 `record_boundary` / `record_turn` / `create_conversation` / `delete_conversation` 四个收口点
   成功后触发（先克隆 Arc 再调用，不在 slot 锁内持 listener 锁）。
4. **Kotlin**：`RustConversationStore` 注册 listener；回调里**只** `trySend` 进内部 channel
   （回调线程是核心持有的线程，禁重活、禁抛异常），协程 conflate + 300ms debounce 后 `refresh()`。
5. **ChatViewModel**：删 `endTurn` 里的 `ChatEffect.RefreshConversations`（推送取代）；
   init 的启动刷新保留（兜底首次填充）。
6. 构建：`generateRustBindings` + `buildRustCore`（工具链已核实齐备：cargo 1.97.1 +
   cargo-ndk 4.1.2 + 四 ABI target + NDK 28/29）。

## 4. 实施顺序

| Phase | 内容 | 依赖 |
|---|---|---|
| 1 | D2 影子状态收编 + 门禁 R8 | 无 |
| 2 | D3 EffectRunner + 三处收编 + 门禁 R11 | 无 |
| 3 | D4 守卫回滚（Main/Provider/Chat + DataStore 洞） | Phase 2 |
| 4 | D1 派生投影 + 门禁 R9 + BaseViewModel open 化 | Phase 1 |
| 5 | D5 Rust 推送 + 单例共享 | 无（独立），但落在 Phase 4 之后验证 |

## 5. 测试与验证矩阵

| 验证点 | 手段 |
|---|---|
| 既有行为不回退 | `ChatViewModelTest`（44 例）/ `UsageStatsViewModelTest` / `LanguageViewModelTest` 全绿 |
| 门禁新规则自身有效 | MvvmUdfGateTest 新增 R8/R9/R11 用例（含"故意违规则红"的负向自检注释） |
| 影子状态收编 | 新增：附着后 `attachedKeys` 经 action 可见（现有附着断言覆盖）；keep-warm 到期不落StateFlow 外 |
| 守卫回滚 | 新增 `MainViewModelTest` / `ProviderViewModelTest`：fake 仓库注入失败 → 断言状态回滚 + toast；连写竞态（A→B→A失败）断言不回滚 |
| 派生投影 | 既有切会话/并行会话/回流用例（#24-#27 等）即覆盖；新增"后台会话 keep-warm 到期后投影回落"用例 |
| Rust 推送 | `cargo test -p jasmine-core`；手工/emulator 验证跑 turn 期间列表 updatedAt 刷新 |

每 Phase 完成后跑：`gradlew :feature:main:impl:testDebugUnitTest :feature:settings:impl:testDebugUnitTest :feature:provider:impl:testDebugUnitTest`；
Phase 5 追加 `cargo test`（rust/）与 `:core:agent:assembleDebug`（验证四 ABI 构建）。

## 6. 验收标准

1. 门禁全绿，且 R8/R9/R11 在源码里"故意写坏一行"时确实变红（抽查一次）。
2. 全部单测通过；新增回滚用例覆盖 Main/Provider 的删除、保存、偏好写失败路径。
3. `ChatViewModel.kt` 中不再存在 suspend/协程内的影子字段写点（门禁保证）。
4. `ChatState.conversation` 没有任何手动 `copy(conversation=...)` 赋值点（门禁保证）。
5. 跑 turn 期间侧边栏会话列表的 updatedAt 随核心落盘自动刷新（D5，真机/模拟器抽查）。

## 7. 明确不做的事（本次范围外）

- P1 级：View 层 `stateFlow.value` 直读、`customFontFamily` 查询旁路、UI 层 SidebarState 投影、
  推理档位表双份维护、跨 feature 类型依赖方向——另案。
- P2 级：README/Cargo.toml/MODULE_MAP 文档与依赖清理——另案。
- ChatViewModel 文件拆分（2330 行）：等 P0 落地、门禁稳固后另案评估。
- Rust 侧错误分型穿 FFI、共享 tokio runtime、事件背压——另案。

---

## 8. 实施记录（2026-09-30 完成）

全部五个 Phase 已实施完毕。逐项落实与计划一致；下面是**实际做法与计划不同**的地方（都记了原因），
以及验证结果。

### 8.1 与计划的偏差

1. **D3：`endConversation` 没有进 Effect 通道**（已在 §3.3 就地修订）。释放必须与下一次附着严格
   有序：Effect 排队期间 `runTurn` 可能已在 IO 线程重新 `startConversation`，迟到的旧释放令会把
   新附着割掉。改为"同步 + `runCatching` + 失败经 `EffectFailed(EndConversation)` 回流"，定位为
   注册表维护，是门禁 R11 的登记白名单项。
2. **D2：`closeStreamParser` 进了 R8 白名单**。它是同步助手（由 `startAssistantSegment` /
   `sealAssistantSegment` 调用），写 `turn.parsedLength` 属同一帧内的回合游标维护，非异步结果。
3. **D3：LanguageViewModel 的 `SystemLocaleSettled` 改为协程读平台 + `CurrentResolved` 回流**，
   而不是计划里含糊的"UI 侧 effect 触发处"——保持 handler 纯净的同时，读仍是异步路径。
   `ApplyFailed` 的回滚是"重读平台权威值"而非携带回退值：平台的当前值就是唯一权威。
4. **D4：ProviderDataStore 的抛错措辞**为
   `"stored provider list is undecodable; refusing to overwrite it"`（语义同计划）。
5. **门禁实现细节**：`functionRanges` 增加对 `init {` 的切分（否则订阅装配会被并进上一个函数，
   R11 误伤）；①号规则增加 `D1ProjectionRead` 只读豁免（`stateFlow`/`state` 的投影覆写要读
   `mutableStateFlow`）；R9 用 `copy\(conversation\s*=` 精确匹配并滤注释行，避免打到
   `copy(conversations = …)`。
6. **D5：`AgentHandle` 全部实例合并为一个 `@Singleton`**（不只是"store 与 chat 共享"）——
   按会话分槽的 service 本就支持多会话并存，合并后顺带修掉"store 删正在跑的会话、chat 侧槽变孤儿"。
7. **D5：`endTurn` 整个删除**（不再是"删掉刷新、保留空函数"）。它当时只剩刷新这一件事，保留空壳
   是死代码；回合收尾的语义已由 `finishTurn`/`failTurn`/`handleTurnInterrupted` 各自承担，且
   `endTurnKeepingWarm` 负责 keep-warm。附带影响：门禁白名单里的 `endTurn` 成为无用项（无害）。

### 8.2 验证结果

| 项 | 结果 |
|---|---|
| `gradlew testDebugUnitTest`（全模块，含 app 截图测试） | ✅ BUILD SUCCESSFUL |
| `cargo test -p jasmine-core -p jasmine-ffi -p jasmine-rollout` | ✅ 58 passed / 0 failed |
| `gradlew :core:agent:generateRustBindings` | ✅ 生成 `ConversationStoreListener` / `setStoreListener` |
| `gradlew :core:agent:buildRustCore`（四 ABI） | ✅ arm64-v8a / armeabi-v7a / x86_64 / x86 全出 |
| 门禁负向自检（故意在 `runTurn` 里写 `attachedKeys[...] = …`、`updateState { copy(conversation = …) }`） | ✅ R8 / R9 / ②三条各自变红；还原后全绿 |
| `cargo check -p jasmine-ffi` / `-p jasmine-core --tests` | ✅ 无警告级错误 |

### 8.3 验收标准对照

1. 门禁全绿 ✅；负向自检三条规则确实变红 ✅
2. 全部单测通过 ✅；回滚用例：Provider 侧已新增（见 8.4），Main 侧未加（原因见 8.4）
3. `ChatViewModel.kt` 内 suspend/协程中无影子字段写点 ✅（R8 保证）
4. `ChatState.conversation` 无手动赋值点 ✅（R9 保证，唯一赋值在 `project()`）
5. 跑 turn 期间列表自动刷新 —— 逻辑与构建均已就绪；**待真机/模拟器抽查**

### 8.4 回滚用例与一个踩到的坑

新增 `feature/provider/impl/.../ProviderViewModelRollbackTest.kt`（3 例），覆盖 D4 在 Provider 侧的
三条路径：删除失败按**原位**插回、保存失败恢复列表快照并**重开草稿**、以及**身份守卫**
（落盘挂起期间用户已开新草稿，迟到的失败不得顶掉它）。为跑它给 `feature:provider:impl` 补了
`testImplementation(junit / coroutines-test)`。

**踩到的坑（值得记）**：这三个用例第一版全红，根因不是回滚逻辑错，而是
`effectFailed` 开头的 `android.util.Log.w(...)` 在纯 JVM 单测里抛 `Method w not mocked` ——
异常发生在 `EffectRunner` 的 catch 块里，把 effect 消费者协程直接打死，回滚 action 永远发不出来。
修法是给该模块补 `testOptions { unitTests { isReturnDefaultValues = true } }`（`main:impl` /
`settings:impl` 早就这么配了）。附带结论：**"失败路径里的日志"本身是个脆弱点** —— 它一抛异常，
整条失败回流就断了；各 VM 的失败回流都应在这条配置下被测到。

### 8.5 本次未做的事（遗留，另案）

- **MainViewModel 的回滚用例**：`MainViewModel` 依赖的是**具体类** `CustomFontRepository` /
  `CustomFontFamilyCache`（final class，且构造要 `Context`），不是接口 —— 要给它做单测，得先把
  这两个依赖抽成接口（属 P1 级重构）。它的偏好回滚已按 D4 统一实现并过门禁，故留到那一批一起做。
- **D5 的真机验证**：需要设备/模拟器确认"跑 turn 时侧边栏 updatedAt 跟随刷新"。
- P1/P2 清单（§7）与 `MODULE_MAP.md` 的过时描述（第 57 行仍写 `RustAgentChat(conversationStore)`、
  手动 refresh 那套）未同步——属 P2 文档批。


