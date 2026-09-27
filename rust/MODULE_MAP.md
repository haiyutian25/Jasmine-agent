# Rust 化对照表（Android ↔ 参照实现）

> 本文档是 jasmine 把 Agent 核心改成 Rust 的**唯一对照依据**。动代码前先在这里查一行。
>
> 位置：`minimal-hello/rust/MODULE_MAP.md`（与骨架放在一起，跟着骨架一起进版本控制）
> 更新日期：2026-09-27

---

## 0. 两条硬规则

1. **参照实现里有对应模块 → 照它来做。**
   模块划分、接口形状、连实现方式都照它 —— 不自己另写一套"差不多"的东西。
   理由：自己另写的那套没有对照物，评审时无法回答"为什么这么写"，出问题也无处可比。
2. **参照实现里没有对应模块 → 先问，不自己编。**
   宁可留空并在本文档登记，也不要为了"看起来完整"而造一个模块出来。

配套的第三条（写作规范）：代码里**不出现外部项目名、不写"移植/抄"这类说法**；
注释指向 **jasmine 自己的 Kotlin 实现**（`对应 Kotlin: xxx.kt（N 行）`），这是定位信息，不是来源注脚。

---

## 1. 已建的 crate 与职责

| crate | 目录 | 职责 | Android 对应 | 状态 |
|---|---|---|---|---|
| `jasmine-utils-string` | `utils/string` | 文本小工具（token/字节换算等） | `core:agent` 的展示截断 | ✅ 已实现 |
| `jasmine-utils-output-truncation` | `utils/output-truncation` | 输出长度收口（按字节/token 截断） | `core:agent` 的展示截断 | ✅ 已实现 |
| `jasmine-protocol` | `protocol/` | 跨边界类型：会话事件、角色、会话 id、探测结果 | `AgentChat.kt` 的事件 + `ProviderProbe.kt` | ✅ 已实现 |
| `jasmine-http-client` | `http-client/` | 客户端构建、重试节流、端点拼接与错误文本 | `core:network` + 各 `*Wire.kt` 的传输部分 | ⚠️ 重试/拼接已实现，客户端构建待接 |
| `jasmine-model-provider-info` | `model-provider-info/` | provider 静态元信息、wire 协议、内置预设 | `ProviderConfig.kt`（去掉密钥） | ✅ 已实现 |
| `jasmine-model-provider` | `model-provider/` | 凭据注入、模型列表端点 | `ProviderConfig.apiKey` + `ProviderModelDataSource.kt` | ✅ 已实现 |
| `jasmine-api` | `api/` | 两套协议报文 + SSE 解析 | `OpenAi*Model/Wire.kt`（1,123 行） | ⚠️ SSE 已实现，端点待接 |
| `jasmine-tools` | `tools/` | 工具契约（`Tool`/`ToolError`/`ToolFuture`）+ 工具词汇表（声明、json schema、结果）+ jasmine 自带工具 | `JasmineTools.kt` 的声明部分 | ✅ 已实现 |
| `jasmine-rollout` | `rollout/` | 会话落盘：每会话一个只追加 JSONL（首行是会话元信息），另附读取与发现 | `ConversationStore.kt` + ADK 的 `RoomSessionService` | 🚧 已实现，接线中（见 §2.6） |
| `jasmine-core` | `core/` | 会话门面（`AgentChatService`）、轮次主循环、模型客户端、上下文、工具注册表、宿主边界、探测 | `AgentChat.kt` / `AdkAgentChat.kt` / `ProviderProbe.kt` | ✅ 已实现（绑定待接） |
| `jasmine-ffi` | `ffi/` | 跨语言边界（Android 无对应，必须新增）：`AgentHandle` / `EventListener` / `HostConversations` / `HostClock` + 两个适配器 + `probe` | `AgentChat.kt` / `ProviderProbe.kt` 的实现位 | ✅ 已接 UniFFI 0.32.2（注解 + 生成 Kotlin，见 §1.1）；Android 侧构建接线与 Kotlin 适配待做 |

### 1.1 跨语言绑定（UniFFI）

- 版本 **0.32.2**；`jasmine-ffi` 的导出面用 proc-macro 注解（`#[uniffi::export]` / `#[derive(uniffi::Object)]` / `#[uniffi::export(with_foreign)]`）。**不另写一套镜像类型**：跨边界类型（`ChatEvent` / `Role` / `ProbeResult` / `WireApi`）的注解长在 `jasmine-protocol` / `jasmine-model-provider-info` 自己的类型上，这两个 crate 各带一个可选的 `uniffi` 特性（默认关）；cdylib 侧用 `uniffi_reexport_scaffolding!()` 把它们的脚手架带进产物。
- 生成 Kotlin：

  ```text
  cargo run -p jasmine-ffi --features bindgen-cli --bin uniffi-bindgen -- \
      generate --library <cdylib> --language kotlin --out-dir <输出目录>
  ```

  产出三个文件：`uniffi/jasmine_ffi/jasmine_ffi.kt`、`uniffi/jasmine_protocol/jasmine_protocol.kt`、`uniffi/jasmine_model_provider_info/jasmine_model_provider_info.kt`。
- 边界上的错误类型：UniFFI 不接受裸 `String` 当错误类型，所以用 `AgentFailure`（单变体 `Failed { detail }`，`Display` 就是那句原因）代替原来的 `Result<_, String>` —— 语义不变：只给一句能显示的话。
- 生成物依赖 **JNA**：Android 模块需要加 `net.java.dev.jna:jna:5.17.0@aar`（或更新版本）。
- **Gradle 接线（已做，实测通过）**：`core/agent/build.gradle.kts` 里三个任务 —— `buildRustCore`（cargo-ndk 出 4 个 ABI 的 `.so`）、`buildRustHostLib`（本机 cdylib）、`generateRustBindings`（UniFFI 生成 Kotlin）；产物落 `core/agent/build/rust/{jniLibs,kotlin}`，经 `androidComponents.onVariants { variant.sources.jniLibs/kotlin.addStaticSourceDirectory(...) }` 挂进变体源集，`preBuild` 依赖它们。JNA 走版本目录（`jna = "5.19.1"`，Android 用 `@aar` 变体）。
  - 坑一：AGP 9 的 library 模块不能用旧式 `android.sourceSets`（访问即 `DefaultAndroidLibrarySourceSet_Decorated cannot be cast to AndroidLibrarySourceSet`），必须走变体源集 API。
  - 坑二：Gradle 用户级代理（`D:\AndroidDev\gradle\gradle.properties` 里的 `127.0.0.1:10808`）不通时，用 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=6480 -Dhttps...` 覆盖即可。
  - 反例：reqwest 的默认特性会带 native-tls → OpenSSL，交叉编译不到 Android，所以工作区里 `reqwest` 改为 `default-features = false` + rustls。
- **Kotlin 侧适配（已做）**：`RustAgentChat`（用 Rust 实现现有 `AgentChat` 接口：阻塞调用落 `Dispatchers.IO`，核心的回调转 `Flow<ChatEvent>`，失败走 `ChatEvent.Failed`）、`ConversationStoreHost`（`ConversationStore` → 核心要的会话数据；转写里的工具活动没有 call id，按作者文本跨界）与 `DeviceClock`（时间由平台格式化）。
- **引擎已切换**：Hilt 装配（`core/agent/.../di/AgentModule.kt`）里 `AgentChat` → `RustAgentChat(conversationStore)`、`ProviderProbe` → `RustProviderProbe`（用核心的 `probe`）。ADK 的实现与它的 provider 仍留在模块里（自己的测试也还在），**换回去是这一行的事**。会话存储改为核心侧的 rollout（每会话一个只追加 JSONL，见 §2.6），ADK 的 Room 会话库随之下线。
  - 实测：`:core:agent:compileDebugKotlin`、`:app:compileDebugKotlin`（Hilt 整图校验）、`:app:assembleDebug` 均 BUILD SUCCESSFUL；APK 里 `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libjasmine_ffi.so` 与 JNA 的 `libjnidispatch.so` 都在。
- 尚未做：**设备上实测一轮**（发消息 → 回复 / 工具调用）；**停止/中断一轮尚未实现**（Rust 侧是阻塞调用，取消 Flow 不会中断核心的轮次）。

验证现状（2026-09-27）：workspace members = api / client / core / ffi / http-client / model-provider / model-provider-info / protocol / tools / utils/{string,output-truncation}；`cargo check --workspace --all-targets` 0 error / 0 warning、`cargo test --workspace` 129 passed、`cargo clippy --workspace --all-targets` 0 error / 0 warning、`cargo fmt --check` 一致。

---

## 2. 逐项功能对照

### 2.1 会话与轮次

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 会话门面契约（5 个方法 + 6 个事件） | `AgentChat.kt`（117 行） | `protocol` 的 `ChatEvent`/`SessionId`/`Role` + `core/src/session/service.rs` 的 `AgentChatService` | ✅ 已实现 |
| 会话附着 / 释放 | `AdkAgentChat.kt`（383 行） | `AgentChatService::start_conversation` / `end_conversation`（附着时经宿主 trait 装载已有上下文） | ✅ 已实现 |
| 轮次主循环 | ADK 内部（自研无代码） | `core/src/session/turn.rs`（`run_turn`） | ✅ 已实现 |
| 轮内循环（工具调用 → 回填 → 再采样） | ADK 内部 | 同上（串行，见 §3.2） | ✅ 已实现（串行） |
| 停止回复 | `ChatAction.StopClicked` + `handleStopClicked` | `Op::SuspendTurnAndShutdown` | ⚠️ 待接 |
| 恢复被中断的轮次 | `AgentChat.persistInterruptedReply` | `AgentChatService::persist_interrupted_reply`（把半段回复放回模型上下文；持久化归平台） | ✅ 已实现 |
| 任务抽象（一次任务怎么跑） | ADK Runner | `core/src/tasks/`（`regular.rs` 等） | 未建（主循环落地时一并） |

### 2.2 模型与 provider

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 两套 wire 协议选择 | `OpenAiModelFactory.kt`（35 行） | `model-provider-info` 的 `WireApi` | ✅ |
| Chat Completions 报文 | `OpenAiChatCompletionsModel.kt`(312) + `OpenAiChatWire.kt`(149) | `codex-api` 的 `endpoint/` + `sse/` | ⚠️ 待接 |
| Responses 报文 | `OpenAiResponsesModel.kt`(223) + `OpenAiResponsesWire.kt`(221) | 同上 | ⚠️ 待接 |
| SSE 流式解析 | 三个 `*Wire.kt` 的流式部分 | `codex-api/src/sse/` | ✅ 已实现 |
| 请求重试与节流 | `OpenAiWire.kt` 的重试部分 | `http-client/src/retry_after.rs` | ✅ 已实现 |
| 凭据注入 | `ProviderConfig.apiKey` → 请求头 | `model-provider/src/auth.rs` | ✅ 已实现 |
| provider 静态表与内置预设 | `ProviderConfig.DEFAULTS` / `DEEPSEEK` | `model-provider-info` 的 `built_in_model_providers` | ✅ 已实现 |
| 模型列表拉取（"获取模型"按钮） | `ProviderModelDataSource.kt`（~70 行） | `model-provider/src/models_endpoint.rs` | ✅ 已实现（`fetch_model_ids`，兼容 OpenAI `{data:[{id}]}` 与 DeepSeek `{models:[{id\|model_name}]}`；base_url 已以 `/v1` 结尾时不重叠加） |
| 模型输入模态（文本/图片/音频） | 无（当前只走文字） | `protocol` 的 `InputModality` + `core/src/context_manager/normalize.rs` 的两个剥离 pass | ✅ 已实现（能力落在 `ModelConfig.input_modalities`，默认文本+图片） |
| **token 用量解析** | `OpenAiChatWire.kt:95` / `OpenAiResponsesWire.kt:130` → `UsageMetadata` | 两套 wire 的 `sse/*.rs` → `ResponseEvent::Completed.token_usage` → `ChatThread::last_token_usage()` | ✅ 已实现（见 §3.1；不新增界面事件） |
| 连通性探测 | `ProviderProbe.kt` + `AdkProviderProbe.kt`（107 行） | **参照无对应** | ✅ 自主实现（已登记，见 §4） |
| 模型选择与当前模型 | `ChatAction.ModelSelected` + `UserPreferences` | `models-manager` + `config` | 留平台（选择与持久化在 UI 侧） |

### 2.3 工具

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 工具声明（名称/描述/参数 schema） | `JasmineTools.kt` 的 `@Tool` 注解 | `tools/src/tool_spec.rs`、`tool_definition.rs`、`json_schema.rs` | ✅ 已实现 |
| 工具注册与按名分发 | ADK 的注解处理器生成 | `core/src/tools/registry.rs` + `spec_plan.rs` | ✅ 已实现 |
| 内置工具实现 | `JasmineTools.currentTime` / `listPastConversations` | 参照在 `core/src/tools/handlers/`；这里落在 `tools/src/current_time.rs`、`tools/src/list_past_conversations.rs` | ✅ 已实现（有意挪到 tools 侧：契约也在那里，依赖保持单向 `core → tools`） |
| 工具结果长度收口 | 展示层截断 | `utils/output-truncation/src/lib.rs` | ✅ 已实现 |
| **并行工具调用** | `OpenAiChatCompletionsModel.kt:160` → `parallelToolCalls = false`（**明确关掉**） | `parallel_tool_calls: true` + `core/src/tools/parallel.rs` | ❌ **缺**（见 §3.2） |
| 工具执行编排（审批 → 沙箱 → 升级重试） | 无（工具是纯函数） | `core/src/tools/orchestrator.rs` + `sandboxing/` | 不迁（见 §4） |
| 动态工具（宿主注入） | 无 | `tools/src/dynamic_tool.rs` | 未建 |

### 2.4 上下文

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 模型上下文持有与组装 | ADK 会话服务 | `core/src/context_manager/history.rs` | ✅ 已实现 |
| 残缺记录归一化（有调用无结果等） | 靠流程约束（`respondToPrompts` 的注释） | `core/src/context_manager/normalize.rs` | ✅ 已实现（含模型读不了的模态替换为占位文本的两个 pass） |
| 上下文片段注入（world state / 记忆等） | ADK 内部 | `core/src/context/` | 未建（用到再建） |
| 上下文压缩 | **未启用**（`AdkAgentChat.kt:158` 保持默认值） | `core/src/compact.rs` + `compact_remote_v2.rs` | 不做（见 §5） |
| 上下文预算（`contextLength`/`maxOutputLength`） | `ModelConfig`（UI 可配） | token budget（`session/token_budget.rs`） | ❌ 缺落脚点（见 §3.3） |

### 2.5 交互

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 交互提问（工具提问/给选项） | ADK 的 `get_user_choice` / `adk_request_input` | `tools/handlers/request_user_input.rs` + `elicitation.rs` | ⚠️ 数据模型已就位（`PendingPrompts` + 调用 id 配对），恢复待接 |
| 答案按序收齐一次提交 | `AgentChat.respondToPrompts` 注释里的血泪教训 | 同上 | ✅ 已实现（含"少交一个"拦截） |
| 中途引导（回复进行中追加输入） | 无 | `codex_thread.rs` 的 `steer_turn` | 参照有、Android 无（见 §5） |

### 2.6 持久化

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 会话列表读取 | `ConversationStore.conversationsStateFlow` / `latestConversation` | `rollout/src/list.rs`（读各文件自己的 meta，最新在前） | 🚧 改核心侧（平台来问） |
| 会话增 | `createConversation` | `rollout/src/recorder.rs`（创建即写首行元信息） | 🚧 已实现，接线中 |
| 会话删 | `deleteConversation` | 参照是 `archived_sessions` 归档 | 未做 |
| 读某会话的转写 | `messagesOf(conversationId)` | `rollout/src/list.rs` 的 `read_response_items` | 🚧 改核心侧 |
| 已有上下文装载 | ADK 会话服务 | `rollout/src/list.rs` + `AgentChatService::start_conversation` | 🚧 从会话文件装载（不再由平台传） |
| 回合落盘 | ADK 的 `SessionStore` 写 `StorageEvent` | `rollout/src/recorder.rs`（一行一条追加） | 🚧 接线中 |
| 会话重命名 / 归档 / 搜索 | **无** | `append_thread_name`、`ARCHIVED_SESSIONS_SUBDIR` | 参照有、Android 无（见 §5） |

### 2.7 界面与平台专有（不迁）

| 能力 | Android 位置 | 参照实现 | 结论 |
|---|---|---|---|
| Markdown / Mermaid 渲染 | `core:markdown`（2,793 行，自研 incremark 引擎） | 终端渲染（不可复用） | 留 Kotlin |
| Mermaid 图片保存到相册（含 API 26-28 存储权限） | `MarkdownBlockList.kt:528-538` | 无 | 留 Kotlin |
| 链接外跳 | `MarkdownImage.kt:49` | 无 | 留 Kotlin |
| 主题 / 字体 / 自定义字体下载 | `core:ui`、`feature:settings`、`FontRemoteDataSource` | 无（终端 UI） | 留 Kotlin |
| 导航 | `core:navigation` | 无 | 留 Kotlin |
| 侧边栏 / 模型选择器 / 时间与模型标签 | `feature:main:impl`（4,135 行） | 无 | 留 Kotlin |

---

## 3. 本轮审计新发现（Android 有、参照也有，但骨架里还缺）

这三项是**这次审计新查出来的**，之前没纳入对照。

### 3.1 token 用量解析与传递

- **Android 确实在做**：`OpenAiChatWire.kt:95`、`OpenAiResponsesWire.kt:130` 都解析 `usage`，
  并通过 `toAdkUsage()` 转成 ADK 的 `UsageMetadata`（`OpenAiChatCompletionsModel.kt:257`、
  `OpenAiResponsesWire.kt:216`）。测试里也有 `{"input_tokens":10,"output_tokens":4,"total_tokens":14}` 的样例。
- **参照实现的对应位置**：`protocol/src/response_usage.rs`（`ResponseUsage` 类型），
  消费方是 token budget 与用量统计。
- **结论**：✅ 已实现。
  - 两套 wire 各自解析：`api/src/sse/chat_completions.rs`（`prompt/completion/total_tokens` → `TokenUsage`）、
    `api/src/sse/responses.rs`（`input/output/total_tokens` + `input_tokens_details.cached_tokens`/
    `cache_write_tokens` + `output_tokens_details.reasoning_tokens`），都挂在 `ResponseEvent::Completed.token_usage` 上；
    结构与参照实现逐字一致（只去掉参照专有的 `codex_rollout_budget_units`）。
  - 核心侧不再丢：`core/src/event_mapping.rs` 把用量带出 → `ChatThread::last_token_usage()`（会话状态里最近一次响应的用量）。
  - **不新增界面事件**：Android 的 `ChatEvent` 只有 6 个事件、没有用量，用量在 ADK 里是挂在**响应**上的（`LlmResponse.usageMetadata`），
    界面上不展示。所以 Rust 侧同样把它当"响应元数据"留在会话状态，宿主/FFI 需要时读 `last_token_usage()`。
  - 单测：api 4 个（两套映射 + 缺字段按 0）、core 4 个（用量被记下 / 未报告时为空）。

### 3.2 并行工具调用

- **Android 明确关掉了**：`OpenAiChatCompletionsModel.kt:160` → `parallelToolCalls = if (tools.isNullOrEmpty()) null else false`。
- **参照实现是开的**：`parallel_tool_calls: true`，并有 `core/src/tools/parallel.rs` 做并行分发
  （可并行判定 → `tokio::spawn`）。
- **结论**：这是**行为差异**，不是缺模块。要按参照实现来（默认并行 + 并行分发），
  就需要在 `core/src/tools/` 下补一个 `parallel.rs`；要保持串行则应在文档里写明是**有意偏离**。
  **待你定**。

### 3.3 模型配置（`contextLength` / `maxOutputLength`）没有落脚点

- Android 的 `ModelConfig` 带这两个字段（UI 可配），`ProviderConfig.models` 里持久化。
- 参照实现里对应的是 token budget（`session/token_budget.rs` + `context_manager`）。
- **结论**：目前压缩没做，用不到；但一旦做上下文预算就得有位置。
  候选：放 `model-provider-info`（与 `ModelProviderInfo` 平级），或每次请求由平台带进来。
  **待你定**。

---

## 4. Android 有、参照明确没有（不迁，或自主实现并在此登记）

| 能力 | Android 位置 | 为什么参照没有 | 处理 |
|---|---|---|---|
| 连通性探测 | `ProviderProbe.kt` + `AdkProviderProbe.kt` | 参照不提供"这条配置能不能用"的功能 | 自主实现（`core/src/probe.rs`），已登记 |
| 会话持久化 | `ConversationStore.kt`、`core:data` | 参照的持久化在 `rollout`/`thread-store`；jasmine 照参照做 `rust/rollout`（每会话一个只追加 JSONL，首行是会话元信息与标题/provider/model），列表与转写由核心给出 | 🚧 改核心侧 |
| Provider 配置持久化与界面 | `ProviderDataStore` + `feature:provider:impl` | 参照是配置文件驱动 | 留平台 |
| Markdown/Mermaid 渲染与图片保存 | `core:markdown`、`MarkdownBlockList.kt` | 参照是终端渲染 | 留平台 |
| 主题/字体/导航/界面 | `core:ui`、`core:navigation`、`feature:*` | 同上 | 留平台 |
| 残缺记录归一化 | 靠流程约束 | 参照无同名文件（它的历史由 rollout 保证） | 自主实现（`context_manager/normalize.rs`），已登记 |
| 宿主回调（会话数据/时钟） | `ConversationStore`、`JasmineTools.currentTime` | 跨语言边界特有 | ✅ 自主实现（`core/src/host.rs` 的 `ConversationSource`/`Clock`；两个内置工具各自声明窄 trait：`tools/src/current_time.rs` 的 `Clock`、`list_past_conversations.rs` 的 `ConversationTitles`；**时间一律由平台格式化**） |

---

## 5. 参照有、Android 没有（要迁就得先决定要不要这个能力）

| 能力 | 参照实现的位置 | 现状 |
|---|---|---|
| 多智能体（模型驱动的子 agent：spawn/send/interrupt/list） | `core/src/agent/` + `tools/handlers/multi_agents_v2/` | Android 未用 |
| 确定性编排（顺序/并发/循环跑子 agent） | **参照无对应**（它是模型驱动；"循环跑 agent"只作为局部函数出现在 `memories/write`） | Android 也未用（ADK 有 `LoopAgent` 等，未引用） |
| 运行期 hooks（9 类事件） | `core/src/hook_runtime.rs` | Android 未用 |
| 插件 / 技能 | `plugin/`、`core-plugins/`、`skills` | Android 未用 |
| MCP | `codex-mcp` + `core/src/mcp*` | Android 未用（0 处引用） |
| 沙箱与审批护栏 | `core/src/sandboxing/`、`guardian/`、`tools/orchestrator.rs` | Android 不需要（工具是纯函数） |
| 语音（WebRTC 实时对话） | `core/src/realtime_*.rs` | Android 无 |
| 上下文压缩（本地摘要 / 远程压缩） | `compact.rs`、`compact_remote_v2.rs` | Android 未启用 |
| 会话重命名 / 归档 / 搜索 | `append_thread_name`、`ARCHIVED_SESSIONS_SUBDIR` | Android 无 |
| 中途引导（steer） | `codex_thread.rs` | Android 无 |
| 用量统计与遥测 | `analytics`、`otel` | Android 无 |
| 会话恢复（RecoverTurn） | `Op::RecoverTurn` | Android 无 |

---

## 6. 待你确认的问题

1. **§3.1 token 用量**：现在补（照参照实现），还是等界面要展示时再补？
2. **§3.2 并行工具调用**：按参照实现改成默认并行（补 `parallel.rs`），还是保持串行并写明是有意偏离？
3. **§3.3 模型配置的 token 预算**：放 `model-provider-info`，还是每次请求由平台带进来？
4. **下一步做什么**：① 先接构建（cargo-ndk → `jniLibs`，让 Kotlin 能加载到库）；② 先填实现（加依赖、实现一个端点 + 主循环）。
