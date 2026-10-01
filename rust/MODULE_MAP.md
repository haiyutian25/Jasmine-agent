# Rust 化对照表（Android ↔ 参照实现）

> 本文档是 jasmine 把 Agent 核心改成 Rust 的**唯一对照依据**。动代码前先在这里查一行。
>
> 位置：`minimal-hello/rust/MODULE_MAP.md`（与骨架放在一起，跟着骨架一起进版本控制）
> 更新日期：2026-10-01
>
> 读法：**§2 里"Android 位置"列标着"旧引擎"的，指 Rust 化之前的 Kotlin 实现 —— 那套代码已整体删除**，
> 留在这里只为说明"这一项当年是从哪儿长出来的"，不是现存代码。

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
| `jasmine-http-client` | `http-client/` | 客户端构建、重试节流、端点拼接与错误文本 | 传输层（`RustAgentChat` 走的唯一 HTTP 出口） | ✅ 已实现并接线（reqwest + rustls，`default-features = false`） |
| `jasmine-client` | `client/` | 端点配置与请求构造（`Provider` / `RetryConfig`）、重试策略与退避执行、请求遥测 | 传输层之上的一薄层公共设施（Kotlin 侧无对应） | ✅ 已实现 |
| `jasmine-model-provider-info` | `model-provider-info/` | provider 静态元信息、wire 协议、内置预设 + 模型目录（含推理档支持度） | `ProviderConfig` / `ModelConfig`（去掉密钥） | ✅ 已实现 |
| `jasmine-model-provider` | `model-provider/` | 凭据注入、模型列表端点 | `ProviderConfig.apiKey` + `RustModelList` | ✅ 已实现 |
| `jasmine-api` | `api/` | 两套协议报文 + SSE 解析 | 传输/报文（Rust 侧自持，Kotlin 无对应） | ✅ 已实现（Chat Completions 与 Responses 两套端点都在真机跑通） |
| `jasmine-tools` | `tools/` | 工具契约（`Tool`/`ToolError`/`ToolFuture`）+ 工具词汇表（声明、json schema、结果）+ jasmine 自带工具 | 工具声明与执行（Rust 侧自持） | ✅ 已实现 |
| `jasmine-rollout` | `rollout/` | 会话落盘：每会话一个只追加 JSONL（首行是会话元信息），另附读取与发现 | `ConversationStore.kt` / `RustConversationStore.kt` | ✅ 已实现并接线（见 §2.6） |
| `jasmine-core` | `core/` | 会话门面（`AgentChatService`）、轮次主循环、模型客户端、上下文、工具注册表、宿主边界、探测 | `AgentChat.kt` / `ProviderProbe.kt` 的实现位 | ✅ 已实现并接线（Rust 化之前的 Kotlin 引擎已整体删除） |
| `jasmine-ffi` | `ffi/` | 跨语言边界（Android 无对应，必须新增）：`AgentHandle`（附着 / 发送 / 回答提问 / 继续 / 中断 / 结束 + `create_conversation` / `delete_conversation` / `conversations` / `transcript` / 窗口与档位读写 / `usage_stats` / `shutdown`）/ `EventListener`（回合事件）/ `ConversationStoreListener`（存储变更）/ `HostClock`（`now` + `format`）+ 适配器 + `probe` / `list_models` / `provider_catalog` / `built_in_providers` | `AgentChat.kt` / `ConversationStore.kt` / `ProviderProbe.kt` 的实现位 | ✅ 已接 UniFFI 0.32.2（注解 + 生成 Kotlin，见 §1.1）；Android 侧构建接线与 Kotlin 适配已完成 |

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
  - 坑二：Gradle 用户级代理（`D:\AndroidDev\gradle\gradle.properties` 里的 `127.0.0.1:7897`）不通时，用 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps...` 覆盖即可。
  - 反例：reqwest 的默认特性会带 native-tls → OpenSSL，交叉编译不到 Android，所以工作区里 `reqwest` 改为 `default-features = false` + rustls。
- **Kotlin 侧适配（已做）**：`RustAgentChat`（阻塞调用落 `Dispatchers.IO`，核心的回调转 `Flow<ChatEvent>`，失败走 `ChatEvent.Failed`）、`RustConversationStore`（会话列表/转写/建删；订阅核心的**存储变更推送**并防抖重读）与 `DeviceClock`（时间由平台格式化）。
- **引擎已切换，且只剩一套**：Hilt 装配（`core/agent/.../di/AgentModule.kt`）里 `AgentHandle` 是 `@Singleton`（进程内唯一的核心句柄：按会话分槽、存储变更推送都挂在它上面），`AgentChat` → `RustAgentChat(handle)`（**刻意非单例**：每条会话一个薄门面，隔离靠核心的槽而不是靠多实例），`ConversationStore` → `RustConversationStore(handle)`，`ProviderProbe` → `RustProviderProbe`。Rust 化之前的 Kotlin 引擎与它的会话库已整体删除；会话存储是核心侧的 rollout（每会话一个只追加 JSONL，见 §2.6），**列表更新由核心推送驱动**（`ConversationStoreListener` → 防抖 → 重读），不再靠"回合结束手动 refresh"。
  - 实测：`:core:agent:compileDebugKotlin`、`:app:compileDebugKotlin`（Hilt 整图校验）、`:app:assembleDebug` 均 BUILD SUCCESSFUL；APK 里 `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libjasmine_ffi.so` 与 JNA 的 `libjnidispatch.so` 都在。
- **中断已实现并真机验证**：`AgentHandle::interrupt` → 核心在下一个 await 点收手、把已经产出的条目落盘、以 `Aborted` 收尾；Flow 被取消时也会通报核心。真机留证：`interrupted_reasoning` / `interrupted_reply` / `<turn_aborted>` 用户片段 / `turn_aborted{duration_ms}` 四件齐备；继续时把 `<interrupted_turn>` 片段拼回用户消息，模型从断点接上。
- **真机实测已做**：多轮对话、两套 wire、并行工具调用与结果回填、思考流、用量落盘、重启恢复都验证过（会话文件在 `files/sessions/<年>/<月>/<日>/rollout-*.jsonl`）。
- **原先列在这里的四项"仍未做"已全部做完**（`P1_P2_FIX_PLAN.md` Phase D）：共享 tokio runtime（连接池不再每轮重建）、
  错误分型穿 FFI（`AgentFailure` 四变体：`NoSession` / `Transport` / `Transcript` / `Internal`）、事件背压
  （有界通道 + 文本合并，终态事件绝不丢）、`AgentHandle` 显式关闭（方法名 `shutdown` —— 生成物已带
  `AutoCloseable.close()`，同名会冲突）。

验证现状（2026-10-01）：workspace members = api / client / core / ffi / http-client / model-provider / model-provider-info / protocol / rollout / tools / utils/{string,output-truncation}；`cargo test --workspace` **186 passed**、`cargo fmt --check` 一致、`cargo check --workspace` 0 error。`[workspace.dependencies]` 已从 202 项精简到**成员真正引用的 32 项**（其余 170 项在被删前只存在于清单里，从未进过依赖图 —— `Cargo.lock` 里查不到它们）。

---

## 2. 逐项功能对照

### 2.1 会话与轮次

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 会话门面契约（14 个方法 + 9 类事件） | `AgentChat.kt` | `protocol` 的 `ChatEvent`/`SessionId`/`Role` + `core/src/session/service.rs` 的 `AgentChatService` | ✅ 已实现 |
| 会话附着 / 释放 | `旧 Kotlin 引擎（已删）`（383 行） | `AgentChatService::start_conversation` / `end_conversation`（附着时从会话文件装载已有上下文） | ✅ 已实现 |
| 轮次主循环 | 旧引擎内部（自研无代码） | `core/src/session/turn.rs`（`run_turn`） | ✅ 已实现 |
| 轮内循环（工具调用 → 回填 → 再采样） | 旧引擎内部 | 同上（串行，见 §3.2） | ✅ 已实现（串行） |
| 停止回复 | `ChatAction.StopClicked` + `handleStopClicked`（幂等状态位 + 出站 Effect） | `AgentChatService::interrupt`（CancellationToken，回合在下一个 await 点收手） | ✅ 已实现并真机验证 |
| 恢复被中断的轮次 | `AgentChat.persistInterruptedReply` | `AgentChatService::persist_interrupted_reply`（把半段回复放回模型上下文；持久化归平台） | ✅ 已实现 |
| 任务抽象（一次任务怎么跑） | 旧引擎 Runner | `core/src/tasks/`（`regular.rs` 等） | 未建（主循环落地时一并） |

### 2.2 模型与 provider

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 两套 wire 协议选择 | `OpenAiModelFactory.kt`（35 行） | `model-provider-info` 的 `WireApi` | ✅ |
| Chat Completions 报文 | ——（Kotlin 侧已无对应实现） | `jasmine-api` 的 `endpoint/chat_completions.rs` + `sse/chat_completions.rs` | ✅ 已实现并真机跑通（含 `reasoning_content` 解析与逐轮回传） |
| Responses 报文 | —— | `jasmine-api` 的 `endpoint/responses.rs` + `sse/responses.rs` | ✅ 已实现并真机跑通 |
| SSE 流式解析 | 旧引擎三个 `*Wire.kt` 的流式部分（**已删**） | `api/src/sse/`（两套协议各一个：`chat_completions.rs` / `responses.rs`） | ✅ 已实现 |
| 请求重试与节流 | 旧引擎的 `OpenAiWire.kt` 的重试部分（**已删**） | `http-client/src/retry_after.rs` + `client/src/retry.rs` | ✅ 已实现 |
| 凭据注入 | `ProviderConfig.apiKey` → 请求头 | `model-provider/src/auth.rs` | ✅ 已实现 |
| provider 静态表与内置预设 | `ProviderConfig.DEFAULTS` / `DEEPSEEK` | `model-provider-info` 的 `built_in_model_providers` | ✅ 已实现 |
| 模型列表拉取（"获取模型"按钮） | `ProviderModelDataSource.kt`（~70 行） | `model-provider/src/models_endpoint.rs` | ✅ 已实现（`fetch_model_ids`，兼容 OpenAI `{data:[{id}]}` 与 DeepSeek `{models:[{id\|model_name}]}`；base_url 已以 `/v1` 结尾时不重叠加） |
| 模型输入模态（文本/图片/音频） | 无（当前只走文字） | `protocol` 的 `InputModality` + `core/src/context_manager/normalize.rs` 的两个剥离 pass | ✅ 已实现（能力落在 `ModelConfig.input_modalities`，默认文本+图片） |
| **token 用量解析** | 旧引擎的 `OpenAiChatWire.kt:95` / `OpenAiResponsesWire.kt:130` → `UsageMetadata`（**均已删**） | 两套 wire 的 `sse/*.rs` → `ResponseEvent::Completed.token_usage` → `ChatThread::token_usage_info()` → `ChatEvent::Usage` | ✅ 已实现（见 §3.1；每轮回答后交给界面） |
| 连通性探测 | `ProviderProbe.kt` + `旧 Kotlin 探测（已删）`（107 行） | **参照无对应** | ✅ 自主实现（已登记，见 §4） |
| 模型选择与当前模型 | `ChatAction.ModelSelected` + `UserPreferences` | `models-manager` + `config` | 留平台（选择与持久化在 UI 侧） |

### 2.3 工具

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 工具声明（名称/描述/参数 schema） | 旧引擎的 `JasmineTools.kt` 的 `@Tool` 注解（**已删**） | `tools/src/tool_spec.rs`、`tool_definition.rs`、`json_schema.rs` | ✅ 已实现 |
| 工具注册与按名分发 | 旧引擎 的注解处理器生成 | `core/src/tools/registry.rs` + `spec_plan.rs` | ✅ 已实现 |
| 内置工具实现 | 旧引擎的 `JasmineTools.currentTime` / `listPastConversations`（**已删**） | 参照在 `core/src/tools/handlers/`；这里落在 `tools/src/current_time.rs`、`tools/src/list_past_conversations.rs` | ✅ 已实现（有意挪到 tools 侧：契约也在那里，依赖保持单向 `core → tools`） |
| 工具结果长度收口 | 展示层截断 | `utils/output-truncation/src/lib.rs` | ✅ 已实现 |
| **并行工具调用** | 无（Kotlin 侧已无对应） | `core/src/tools/parallel.rs`（`ToolCallRuntime`） | ✅ 已实现：一轮里的多个调用**并发执行**、结果按调用顺序归位；请求侧两套 wire 仍写死 `parallel_tool_calls: false`（不指望服务端听话，见 §3.2） |
| 工具执行编排（审批 → 沙箱 → 升级重试） | 无（工具是纯函数） | `core/src/tools/orchestrator.rs` + `sandboxing/` | 不迁（见 §4） |
| 动态工具（宿主注入） | 无 | `tools/src/dynamic_tool.rs` | 未建 |

### 2.4 上下文

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 模型上下文持有与组装 | 旧引擎的会话服务 | `core/src/context_manager/history.rs` | ✅ 已实现 |
| 残缺记录归一化（有调用无结果等） | 靠流程约束（`respondToPrompts` 的注释） | `core/src/context_manager/normalize.rs` | ✅ 已实现（含模型读不了的模态替换为占位文本的两个 pass） |
| 上下文片段注入（world state / 记忆等） | 旧引擎内部 | `core/src/context/` | 未建（用到再建） |
| 上下文压缩 | **未启用**（`旧 Kotlin 引擎（已删）:158` 保持默认值） | `core/src/compact.rs` + `compact_remote_v2.rs` | 不做（见 §5） |
| 上下文预算（`contextLength`/`maxOutputLength`） | `ModelConfig`（UI 可配） | token budget（`session/token_budget.rs`） | 🚧 部分：`maxOutputLength`→每轮请求上限、`contextLength`→会话起始窗口；压缩阈值等未做（见 §3.3） |

### 2.5 交互

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 交互提问（工具提问/给选项） | `ChatAction.PromptAnswered` + `ChatViewModel.handlePromptAnswered` | `AgentChatService::respond_to_prompts` + 核心的待答提问模型 | ✅ 已实现（一轮多问按序收齐、一次提交；真机验证） |
| 答案按序收齐一次提交 | `AgentChat.respondToPrompts` 注释里的血泪教训 | 同上 | ✅ 已实现（含"少交一个"拦截） |
| 中途引导（回复进行中追加输入） | 无 | `codex_thread.rs` 的 `steer_turn` | 参照有、Android 无（见 §5） |

### 2.6 持久化

| 能力 | Android 位置 | 参照实现的位置 | 骨架状态 |
|---|---|---|---|
| 会话列表读取 | `ConversationStore.conversationsStateFlow` / `latestConversation` | `rollout/src/list.rs`（读各文件自己的 meta，最新在前）+ `AgentChatService::conversations` | ✅ 核心侧供给（平台来问；时间戳给毫秒，界面按设备时区显示） |
| 会话建 / 删 | `createConversation` / `deleteConversation` | `rollout/src/recorder.rs`（创建即写首行元信息）/ `rollout/src/list.rs` 的 `delete_session` | ✅ 核心侧（`create_conversation` / `delete_conversation`） |
| 读某会话的转写 | `messagesOf(conversationId)` | `rollout/src/list.rs` 的 `read_response_items` + `AgentChatService::transcript` | ✅ 核心侧 |
| 已有上下文装载 | 旧引擎的会话服务 | `rollout/src/list.rs` 的 `read_response_items` + `AgentChatService::start_conversation` | ✅ 从会话文件装载（不再由平台传） |
| 回合落盘 | 旧引擎的 `SessionStore` 写 `StorageEvent` | `rollout/src/recorder.rs`（一行一条追加；`send` / `respond_to_prompts` / `persist_interrupted_reply` 三处） | ✅ 已实现 |
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

- **Android 确实在做**（旧引擎，均已删）：`OpenAiChatWire.kt:95`、`OpenAiResponsesWire.kt:130` 都解析 `usage`，
  并通过 `to旧引擎Usage()` 转成 旧引擎的 `UsageMetadata`（`旧 Kotlin 模型适配（已删）:257`、
  `OpenAiResponsesWire.kt:216`）。测试里也有 `{"input_tokens":10,"output_tokens":4,"total_tokens":14}` 的样例。
- **参照实现的对应位置**：`protocol/src/response_usage.rs`（`ResponseUsage` 类型），
  消费方是 token budget 与用量统计。
- **结论**：✅ 已实现。
  - 两套 wire 各自解析：`api/src/sse/chat_completions.rs`（`prompt/completion/total_tokens` → `TokenUsage`）、
    `api/src/sse/responses.rs`（`input/output/total_tokens` + `input_tokens_details.cached_tokens`/
    `cache_write_tokens` + `output_tokens_details.reasoning_tokens`），都挂在 `ResponseEvent::Completed.token_usage` 上；
    结构与参照实现逐字一致（只去掉参照专有的 `codex_rollout_budget_units`）。
  - 核心侧不再丢：`core/src/event_mapping.rs` 把用量带出 → `ChatThread::token_usage_info()`
    （`TokenUsageInfo` = 会话累计 + 最近一次 + 窗口，照参照实现的形状与命名，`new_or_append` 逐次累加）。
  - **界面事件**：每轮回答结束后发一个 `ChatEvent::Usage`，带 `TokenUsageInfo`、这一轮请求按来源的
    构成（系统提示词 / 系统工具 / 技能 / MCP 工具 / 消息）以及窗口上限。
    - **构成的份额是本地估算、总量是 provider 的真值**：`session/turn.rs::context_breakdown` 用
      `approx_token_count`（字节 ÷ 4，工具按 `client.rs::tool_declaration` 那份**真正上线**的 JSON 量，
      领域 MCP 工具按 `mcp__<server>__` 前缀分流），随后 `ChatThread::scaled_request_breakdown`
      按 `last_token_usage.input_tokens` 等比缩放、余数补给最大的一块 —— 核心没有分词器，provider 才有。
    - 窗口上限来自平台配置的 `ModelConfig.context_length`（0 = 没设 → 默认 200K），核心不消费它、只报给界面。
      界面显示在输入框左侧的环形入口里（见记忆 `project_context_usage_panel_port`）。
  - 单测：api 4 个（两套映射 + 缺字段按 0）、`core/src/thread.rs` 5 个（记下 / 逐次累加 / 未报告时为空 /
    按真值缩放 / 取整后总量仍等于真值）。

### 3.2 并行工具调用

- **Android 明确关掉了**：`旧 Kotlin 模型适配（已删）:160` → `parallelToolCalls = if (tools.isNullOrEmpty()) null else false`。
- **参照实现是开的**：`parallel_tool_calls: true`，并有 `core/src/tools/parallel.rs` 做并行分发
  （可并行判定 → `tokio::spawn`）。
- **请求侧**：与 Android 一致，`parallel_tool_calls: false`（`core/src/client.rs` 两套 wire 都写死）。
- **执行侧**：照参照实现补了 `rust/core/src/tools/parallel.rs`（`ToolCallRuntime`；工具用
  `supports_parallel` 声明能否共用会话 —— 可并行的共用读锁，其余排写锁；结果按完成顺序返回）。
- **写进历史的顺序**：`core/src/session/turn.rs` —— 同一轮的**调用先全部写入，再写结果**。

### 3.3 模型配置（`contextLength` / `maxOutputLength`）的落脚点

- Android 的 `ModelConfig` 带这两个字段（UI 可配），`ProviderConfig.models` 里持久化。
- **`maxOutputLength` → 每轮请求的输出上限**（2026-09-28 接好）：`session/service.rs` 的
  `output_token_cap`（0 = 没设 → 不发）→ `ModelClient::with_max_output_tokens`（`core/src/client.rs`）→
  按协议自己的字段名上线：Responses 写 `max_output_tokens`（`api/src/common.rs`，上游 `ResponsesApiRequest`
  没有这个字段 —— 上游根本不发输出上限），Chat 写 `max_tokens`。它属于**模型**而不是会话，所以会话中途换模型会跟着换。
- **`contextLength` → 会话起始窗口**：新会话第一次附着时取它，没填则 200K（`starting_context_window`），
  落进会话文件（`RolloutItem::ContextWindow`）；窗口一旦定下就冻结，会话中途换模型不改。
- 参照实现里更进一步的用法（**我们没做**）：按窗口算自动压缩阈值、按剩余窗口二次裁输出、把窗口当界面分母
  —— 那要等上下文压缩真做的时候再补（见 §5）。

---

## 4. Android 有、参照明确没有（不迁，或自主实现并在此登记）

| 能力 | Android 位置 | 为什么参照没有 | 处理 |
|---|---|---|---|
| 连通性探测 | `ProviderProbe.kt` + `旧 Kotlin 探测（已删）` | 参照不提供"这条配置能不能用"的功能 | 自主实现（`core/src/probe.rs`），已登记 |
| 会话持久化 | `ConversationStore.kt`、`RustConversationStore.kt` | 参照的持久化在 `rollout`/`thread-store`；jasmine 照参照做 `rust/rollout`（每会话一个只追加 JSONL，首行是会话元信息与标题/provider/model），列表与转写由核心给出 | ✅ 已改核心侧（Room 会话库整体下线） |
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
| 确定性编排（顺序/并发/循环跑子 agent） | **参照无对应**（它是模型驱动；"循环跑 agent"只作为局部函数出现在 `memories/write`） | Android 也未用（旧引擎 有 `LoopAgent` 等，未引用） |
| 运行期 hooks（9 类事件） | `core/src/hook_runtime.rs` | Android 未用 |
| 插件 / 技能 | `plugin/`、`core-plugins/`、`skills` | Android 未用 |
| MCP | `codex-mcp` + `core/src/mcp*` | Android 未用（0 处引用） |
| 沙箱与审批护栏 | `core/src/sandboxing/`、`guardian/`、`tools/orchestrator.rs` | Android 不需要（工具是纯函数） |
| 语音（WebRTC 实时对话） | `core/src/realtime_*.rs` | Android 无 |
| 上下文压缩（本地摘要 / 远程压缩） | `compact.rs`、`compact_remote_v2.rs` | Android 未启用 |
| 会话重命名 / 归档 / 搜索 | `append_thread_name`、`ARCHIVED_SESSIONS_SUBDIR` | Android 无 |
| 中途引导（steer） | `codex_thread.rs` | Android 无 |
| 用量统计与遥测 | `analytics`、`otel` | Android 无 |

---

## 6. 决策记录与当前待办

### 6.1 已决策（原先"待确认"四项的结论）

1. **§3.1 token 用量**：已补 —— 总量是 provider 真值、构成份额是本地估算后按真值缩放（见 §3.1），界面展示在输入框左侧的环形入口里。
2. **§3.2 并行工具调用**：**执行侧并行**（`tools/parallel.rs`），**请求侧保持 `parallel_tool_calls: false`** —— 有意偏离：服务端是否真并行不由我们决定，但执行侧必须能接住"一轮里多个调用"（DeepSeek 那次 400 就是缺这条）。
3. **§3.3 token 预算**：`contextLength` 落成会话起始窗口（写进会话文件、冻结），`maxOutputLength` 走模型配置逐轮上线；两者都不进 `model-provider-info`。
4. **构建与实现顺序**：都已落地（见 §1.1），并已真机验证。

### 6.2 已完成的上一批（原文里的"待办"）

这一批（`P1_P2_FIX_PLAN.md` Phase D）**已全部做完并真机验证**，这里保留原文只为说明当初为什么要做：

| 项 | 内容 | 落点 |
|---|---|---|
| D1 | 共享 tokio runtime（原先 `block_on` 每次新建 runtime，而 `reqwest::Client` 跨轮复用 —— 连接池的 keep-alive 实际每轮重建） | ✅ `service.rs` 持有唯一的 multi-thread runtime，`close`/`shutdown` 时释放 |
| D2 | 错误分型穿 FFI（原先塌缩成单变体 `AgentFailure::Failed{detail}`，界面只能匹配文本） | ✅ `AgentFailure` = `NoSession` / `Transport` / `Transcript` / `Internal` 四变体；Kotlin 侧映射成 `ChatFailureKind` |
| D3 | 事件背压（原先 `Channel.UNLIMITED`） | ✅ 有界 `EVENT_CHANNEL_CAPACITY` + 相邻文本合并，终态事件绝不丢（`EventSinkTest`） |
| D4 | `AgentHandle` 显式关闭（原先靠 GC 触发 UniFFI 析构） | ✅ `AgentHandle.shutdown()`（不叫 `close`：生成物已带 `AutoCloseable.close()`，同名会冲突） |
| D5 | `conversations()` / `transcript()` 把 IO 错误吞成空列表 | ✅ 都返回 `Result`（经 `AgentFailure`），"存储坏了"看得见；`core/data` 的读路径另有 `readFailures` 上报 |

**当前待办：无。** 后续新发现的问题记在 `F_G_FIX_PLAN.md`（Phase F / G）。
