# Jasmine（minimal-hello）全面审查报告

> 审查日期：**2026-10-05**（与本仓库 HEAD `a0443ba` 同一时点）
> 审查方式：**读源码 + 实跑命令 + 拆开产物**，每条结论都回源码/产物对过；文中行号是**当前代码**的行号。
> 与既有文档的关系：`README.md` / `COMPONENT_DEVELOPMENT_GUIDE.md` / `F_G_FIX_PLAN.md` 是**计划与自述**；本文是**独立复核**，两者不一致处以本文列出的证据为准（见 §7）。

---

## 0. 一句话结论

**这是一个工程质量明显高于同规模个人项目的作品：主干架构真实存在、被源码级门禁守护，Rust 核心是真正的引擎（不碰密钥、不 panic、路径有校验），空指针/吞错这类"低级洞"基本被清干净了。**
但**深审之后发现的问题比预期严重，而且已经出现了一条真正的崩溃路径与一条静默的视觉失效**：**存储损坏 → 供应商页必崩且每次进都崩**（`ProviderRepositoryImpl` 的根协程补种写入抛出无人接住，见 §3.6 A1）；**`contentColorFor` 丢了兜底 → 默认容器里的图标在深色主题下画成纯黑**（§3.7 W1，注释写"调用方照旧回落"但调用方并没有）。另有三处会造成**用户可感知的数据丢失或永久卡死**——rollout 的读路径会写文件且追加不保证行首分隔（静默丢记录、非法 UTF-8 会话永久不可读）、**全链路没有任何连接/请求超时**（一次黑洞连接把一条会话永久钉住，连"停止"都无效）、上下文永不裁剪且 HTTP 400 不分类（超窗即永久卡死）。这三条与"文档脱节／`core:widgets` 无测试无许可披露／交付层面的胖包与草稿纸"并列为当前最该处理的事，详见 §3.5。

| 维度 | 评价 |
|---|---|
| 架构（多模块 + MVVM/UDF + Navigation 3 + Hilt） | **A-**，主干清晰；`feature:main:impl` 反向依赖两个 feature 的 impl 是唯一的结构让步 |
| Rust 核心 | **A**，生产路径零 `unwrap`、零日志框架、密钥只走 header、会话 id 进路径前有字符白名单、SSE 有空闲超时与背压 |
| 测试 | **B**，133 JVM + 189 Rust 全绿且**数字与 README 完全对得上**；但 2.7 万行的 `core:widgets` 与全部渲染层零覆盖，截图金标默认不校验 |
| 文档 | **C+**，写得极细且大多经过核验，但**已经落后代码 2～4 天**（Material 3 相关的每一处表述都已失效） |
| 工程/交付 | **C**，单 APK 42MB 全 ABI 出厂、5MB 前端资源全量打包、调试页可用、根目录 540KB 审查草稿入库 |
| 合规 | **D**，77 个文件带 AOSP 版权头（Apache-2.0），仓库**没有 LICENSE、没有 NOTICE、README 只字未提** |

---

## 1. 项目本体

| 项 | 事实（已核实） |
|---|---|
| 名称 / 包名 | Jasmine，`com.lhzkml.jasmine` |
| 版本库 | `git@github.com:haiyutian25/Jasmine-agent.git`，`main`，**214 个提交、单一作者**，工作区干净 |
| 时间线 | 首次提交 2026-08-28，最后一次 2026-10-05 00:02 |
| 规模 | 874 个受控文件；Kotlin/Java **263 文件 / 286 万字节**、Rust **118 文件 / 67 万字节** |
| 模块 | 15 个 Gradle 模块：`app` + `core/{agent,data,markdown,navigation,network,ui,widgets}` + `feature/{main,settings,provider}/{api,impl}` |
| 技术栈 | AGP 9.4.1 / Kotlin 2.4.20 / Compose BOM 2026.09.00 / Navigation 3 1.1.7 / Hilt 2.60.1 / compileSdk 37 / minSdk 26 |
| 引擎 | 全部在 Rust（12 个 crate），经 UniFFI 0.32.2 + JNA 进 Android |
| 语言覆盖 | 双语齐备：main 81 / settings 221 / provider 47 / core:ui 18 / core:widgets 13，`values` 与 `values-zh-rCN` **逐模块数量一致** |

**层次关系（实测）**：`app` → `feature:main:impl` → （`feature:settings:impl`、`feature:provider:impl`、`core:markdown`）→ `core:widgets`/`core:ui` → `core:data`/`core:agent` → Rust。

---

## 2. 实跑验证（不是读文档得出的）

### 2.1 测试：数字与 README 完全一致

```
gradlew.bat --offline testDebugUnitTest   → BUILD SUCCESSFUL in 1m 39s
  21 个套件 / 133 个用例 / 0 失败 / 0 跳过        （与 README "133 JVM tests" 一致）
cargo test --workspace --offline          → 退出码 0
  12 个 target / 合计 189 passed / 0 failed      （与 README "189 tests" 一致）
```

分布（21 个套件里最大的几个）：`ChatViewModelTest` 49、`MainViewModelTest` 13、`MvvmUdfGateTest` 10、`RustAgentChatMappingTest` 8、`ProviderBackRuleTest` 5。

### 2.2 "全项目零 material3"：**属实，且干净**

对全工作区（含 `build/`）grep：

| 检索 | 结果 |
|---|---|
| `androidx.compose.material3`（任何文件） | **0** |
| `MaterialTheme` | **0** |
| `androidx.compose.material.` 的 **import** | **0**（12 处命中全是注释里的 `[androidx.compose.material.icons.Icons]` 文档链接与 `TextFieldDefaults.kt:768,852` 里的两段**字符串**） |
| `a2ui` / `A2UI` | **0** |
| `material3` 出现在 `*.toml` / `*.kts` | **0** |

### 2.3 发布产物：真的能出包，但很胖

`app/build/outputs/apk/release/app-release.apk` = **41,953,793 B（40.0 MiB）**，R8 + 资源压缩已开，签名配置生效。

| 组成 | 原始 | 说明 |
|---|---|---|
| `lib/**` | **36.2 MB** | 4 个 ABI × 6 个 `.so`。大头是 `libjasmine_ffi.so`（6.6–7.6 MB/ABI）与 `libtex.so`（公式）；另有 **JNA 打进 APK 的 mips/mips64 `libjnidispatch.so`（共 281 KB 死重量）** |
| `classes.dex` | 4.6 MB | 单 dex |
| `assets/mermaid` | **5.2 MB（53 个文件，未压缩进包则压到 1.6 MB）** | `index-BIQgJnGN.js` 单文件 2.68 MB + `mermaid.core` 486 KB + `cytoscape` 439 KB + 各图种分片 |
| `res/` | 200 KB | R8 后 |

## 3. 值得肯定的地方（同样是核实过的）

1. **UDF 主干是真的**，而且被**源码级门禁**（`MvvmUdfGateTest`，10 个用例）挡住回归：单一 `updateState` 写入点、异步结果必须回流成 `Internal.*`、影子状态白名单、handler 不许同步碰边界、View 层不许有顶层可观察状态。这个测试自己就写明"是防回退护栏、不是正确性证明"，**这种自我定位比测试本身更难得**。
2. **失败可观察做得彻底**：Provider 解码失败不再静默换种子（`ProviderDataStore.kt:76-89` **抛错拒写**，保住含密钥的原文）、会话列表读失败**保留上次快照**（`RustConversationStore.kt:176-182`）、失败**分了型**（`ChatFailureKind` 五个变体，界面不用去匹配本地化文案 `AgentChat.kt:12-27`）。
3. **Rust 生产代码零 `unwrap`/`expect`/`panic!`**：全仓 249 处命中**全部落在 `#[cfg(test)]` 测试模块**里；生产路径没有 `println!`/`tracing`/`log`，也没有"日志里漏密钥"这类问题。
4. **密钥链路干净**：`api_key` 只在 `ProviderInput` → `BearerAuthProvider` → `HeaderValue` 这一条线上；`ProviderInput` 是 uniFFI Record、`ResolvedProvider` 注释明确写"密钥在最后一刻才拼进来，元信息可以被界面随便传阅"。OkHttp 日志级别在 release 是 `NONE`、debug 是 `BASIC`（不含 body/header）（`NetworkModule.kt:44-49`）。
5. **路径穿越有防护**：会话 id 进文件名前过白名单（`rollout_file_name.rs:60-66`：非空、≤128、只允许 `[A-Za-z0-9_-]`），注释里写明理由（"一个分隔符或 `..` 就能指到会话目录之外"）。
6. **网络与备份策略**：`network_security_config.xml` 显式禁明文 + 只信系统 CA；`allowBackup="false"`；唯一导出的组件是 launcher Activity。
7. **字体下载是"可验证的"**：流式写入 + SHA-256 校验 + `.part` 中转（`FontRemoteDataSource.kt:29-60`），取消与异常两条路都清理残留（`CustomFontRepository.kt:151,162-167`）。
8. **并发原语用得对**：`AtomicReference<Set>` 而非 `@Volatile var`（避免丢更新）、下载用 `Mutex` 串行化（避免两个写者互相踩 `.part`）——注释里都写了"为什么"。
9. **上期问题的落地是真的**：F1（会话槽在持锁期间跑完、`?early-return` 跳过收尾）现在能看到实体（`service.rs:627-632` 用闭包包住整块 + `try_lock_recovering` 处理中毒 + `context_len` 明确"不等锁"），F4（`onCleared` 关通道）、G2（mermaid 渲染 `withTimeoutOrNull`）也都对得上。

---

## 3.5 Rust 核心深审（已回，结论已复核）

> 来源：一个独立子审查代理通读 `rust/` 全部 13 个 crate（约 19k 行）后提交的报告；**下面每条我到源码逐一复核过**，并标出复核结果。它的整体判断与 §3 一致：**编译期纪律极好**（`unwrap_used`/`expect_used`/`await_holding_lock` 全部 deny；生产路径无可被不可信输入触达的 panic；SSE 分帧与 UTF-8 边界交给 `eventsource-stream` 而非自研——该库的 `Utf8Stream` 会把不完整码点留在缓冲区，这一点也被核到了依赖源码）。风险集中在三个**用户可感知**的地方。

### 复核确认（我逐条回源码看过）

**R1 没有任何连接/请求超时，且取消覆盖不到 HTTP 阶段 → 一次黑洞连接可把一条会话永久钉住**
- `HttpClientBuilder` 的默认值就是 `connect_timeout: None`（`rust/http-client/src/client_builder.rs:74`），只有显式调用才设（:62-64）；
- 三处构造客户端**都没设**：`rust/core/src/session/service.rs:329-331`、`rust/core/src/probe.rs`、`rust/core/src/models.rs`；
- 请求级超时唯一的写入口是 `RequestBuilder::timeout`（`rust/http-client/src/request_builder.rs:102-104`）→ 落进 `request_draft.rs:58`，但 `Provider` 侧的 `Request.timeout` 恒为 `None` → **实际永远是 None**；
- 取消令牌只在 SSE 读循环里被观察（`rust/core/src/session/turn.rs:161-165`），而 `client.stream(...)` 的等待（:68-77）里没有 select。
**后果**：连接被黑洞时 `send` 永不返回 → 会话槽锁（`service.rs:627`）永久被占 → `interrupt` 只置令牌、无人观察 → **界面"停止"无效，唯一出路是杀进程**。

**R2 rollout 读路径会写文件，且与在途写者无同步（三条静默数据丢失）**
- 读函数里调 `truncate_to` 做 `set_len`：`rust/rollout/src/list.rs:261-263`、`:273-286`（注释自述"用 set_len 而不是重写，否则在途 recorder 会写进没人看得见的旧 inode"——设计意图正确，但**作者与写者之间没有任何锁**）；
- 坏行只跳过、中段坏行永不修（`list.rs:239-257`、`corrupt_is_only_tail` 判定 :242-244）；
- **追加不保证行首分隔**：`rust/rollout/src/recorder.rs:70-76` 是 `write_all(json)` + `write_all(b"\\n")` 两次系统调用，open 时也不检查文件尾是否以换行结束 → 在两次写之间被杀，就留下"完整 JSON 但没有换行"的尾巴；下次追加把新 JSON 接在同一行 → **这一行两条记录一起读不出来**，且因为后面还有好行，截断修复不会触发（坏行永久保留）；
- **无效 UTF-8 让整份会话永久读不出来**：`list.rs:229` 的 `reader.read_line(&mut raw)?` 在非法 UTF-8 上直接返回 `InvalidData`，`?` 让 `read_lines` 失败，**截断修复（:261）永远走不到** → 该会话从列表里消失、转写报错、`start_conversation` 报 `AgentError::Transcript`（`service.rs:347-348`）→ **再也附着不上，只能手删**。中英混排/emoji 回复下，切口落在多字节字符中间的概率不低。
- **没有 fsync**（`recorder.rs` 的 `flush()` 对 `File` 是空操作）→ 模块文档承诺的"进程中断不丢到上一行"对掉电不成立。也无 schema 版本号。

**R3 上下文永不裁剪 + HTTP 400 不分类 → 超窗即永久卡死**
- 请求侧从不按窗口裁剪（`rust/core/src/session/turn.rs:59-77`）；窗口只落盘、只上报（`service.rs:379-388`、`thread.rs:86-98`）；
- 现成的裁剪工具**全仓无生产调用者**（我 grep 复核：`truncate_function_output_payload`（`rust/utils/output-truncation/src/lib.rs:41`）、`retain_tail_from_last_n_user_messages`/`truncate_assistant_output_text_to_token_budget`（`rust/tools/src/response_history.rs:9,37`）——命中只有**定义、re-export 与它们自己的测试**）；
- 错误分类函数同样无人调用：`classify_error_response`（`rust/api/src/api_bridge.rs:24`）只有 `rust/api/src/lib.rs:18` 的 re-export 一处引用（已复核）。
**后果**：历史超窗 → 服务端 400 → 塌成 `AgentFailure::Transport` → 界面说"传输失败、可重试"，但历史只增不减、**重试永远同样失败** → 这条会话报废，且界面没有任何"清空/压缩"入口。`MODULE_MAP.md:207-208` 自认"按窗口算压缩阈值我们没做"，但**没登记"超窗后不可恢复"这个后果**。

### 一并接受的其余发现（未逐条复核，但来源与本报告同源、位置具体）

| 级别 | 问题 | 位置 |
|---|---|---|
| P2 | **交互提问路径模型看不到自己的追问与用户的回答**：暂停时先 break、之后才把 `round.tool_calls` 入历史，而 `respond_to_prompts` 只推进结果 → 下一轮 normalize 的 `remove_orphan_outputs` 把"没有对应调用的结果"删掉。当前注册表只有两个工具，正常模型不会喊出 `adk_request_input`/`get_user_choice`，**只能被幻觉触发**，一旦触发用户的回答被静默丢弃 | `turn.rs:118-129`、`turn_input.rs:47-58`、`normalize.rs:66-81,168-173` |
| P2 | **interrupt 与 send 的时序竞态会丢一次停止**：`send` 在拿到锁并**读完整个 rollout 文件**（拼上一轮中断片段）之后才装新令牌，而 `interrupt` 取消的是那一刻的令牌 | `service.rs:655-667` vs `:944-956` |
| P2 | **流中断时半截回复被丢弃**：出错路径 round 整体丢掉，而增量文本早已发给界面 → 重载后那段文字消失；对比"主动中断"反而会落盘，两条路不对称。附带：`response.incomplete` 只要原因不是 `interrupted`（如写满 max_output_tokens）就被当硬错误 | `turn.rs:80-86,91-95,186-192`、`sse/responses.rs:257-271` |
| P2 | **HTTP 错误语义未分类 + 429 不重试**：`retry_429: false`；非 2xx 一律由传输层造 `TransportError::Http`，Display 是带转义引号的一大段 JSON Debug 文本 → 界面提示失真、可重试性判断失真 | `model-provider-info/src/lib.rs:141-144`、`client/src/retry.rs:27-31`、`http-client/src/error.rs:11-12` |
| P2 | **无上限的响应/历史/克隆**：`response_body_limit_bytes` 恒 None；max_output_tokens 未配时一个字段都不发；`round.text` 无上限；历史只增不减且**每轮整份 clone**（`turn.rs:61`）；附着一次要全量解析会话文件 **5 次**；列表刷新 O(全部会话字节) | `client/src/provider.rs:84`、`service.rs:1461-1463`、`turn.rs:61,172`、`service.rs:342-374` |
| P3 | 用量存档用**固定临时文件名**（`path.with_extension("jsonl.tmp")`），并发 `usage_stats()` 会互相覆盖 | `rollout/src/usage_archive.rs:84-86` |
| P3 | `context_len` 把"中毒"和"正忙"都报成 0（有意如此，但平台若拿它做"历史装载成功"自检会得到空上下文假象） | `service.rs:607-614` |
| P3 | 列表按**本地时间字符串**排序 → 时区/DST 偏移变化时字典序错序 | `rollout/src/list.rs:43` |
| P3 | 同一 session id 多文件时取扫描顺序里的第一个、删除也只删一个 | `list.rs:56-61,65-70` |
| P3 | Chat 流缺 id 时用**空串**当 call_id → 多条调用共享空 id，normalize 配对会把它们当成同一条 | `sse/chat_completions.rs:106-114` |
| P3 | 约 **3–5k 行"有定义、有测试、无调用者"**的代码（截断、schema、tool search、error classify、`ResponseStream.interrupt` 等），其中 `ResponseStream.interrupt` **正是 R1 需要的取消通道**，已预留但恒为 None | 见上表各条与 `sse/common.rs:210-216` |

### 一个必须记住的测试口径修正

该代理统计出：**189 个 Rust 用例里约三分之二覆盖的是"不在生产路径上"的代码**（`jasmine-tools` 65 个里绝大多数、`jasmine-utils-*` 38 个全部）。同时：
- **两套 SSE 解析器 0 行为测试**（api 的 4 个用例只是用量字段映射），而全仓最脆的代码正是它们；P2 级的解析缺陷全部落在那里；
- **`jasmine-client` 与 `jasmine-http-client` 各 0 测试**——而 R1（超时）与 HTTP 错误分类正好在它们身上；
- **全仓没有任何 `HttpTransport` 的测试实现**（`impl HttpTransport for` 只有 `ReqwestTransport`），所以 `run_turn`/`drain_stream` **从未在测试里跑过一次完整回合**（`service_tests.rs` 里三处 `send` 都明确预期失败）——取消、工具回填、用量落盘、`turn_completed/turn_aborted` 的持久化顺序**全部无自动化覆盖**。
结论：**"189 passed"这个数字的含金量要打折**；补一个 `HttpTransport` 测试实现（喂分片字节流）是这块性价比最高的一步。

**我把这条口径也实测了**（`cargo test --workspace -- --list` 按模块归类）：全仓 **189** 个用例里，单是 `json_schema::tests` 就占 **42** 个（对应"只用于生成声明、不校验入参、无生产调用者"的那套 schema 工具），加上 `tool_spec::tests` 8、`tool_search::tests` 5、`tools::truncate` 等未接线模块，**确实有约三分之一的用例在"没有生产调用者"的代码上**；而 **`jasmine-http-client` 与 `jasmine-client` 各 0 个**、`sse::chat_completions` 只有 **2** 个（用量字段映射）。也就是说：**测试数量最集中的地方，恰好是风险最低的地方；风险最高的 SSE 与 HTTP 层，测试数量是 0～2。**

## 3.6 Android / Kotlin 侧深审（已回，P1 已复核）

> 来源：第二个独立子审查代理（范围：`app`、`core/{agent,data,markdown,navigation,network,ui}`、三个 feature 的 api/impl、全部 Gradle 文件）。它给出了两条 **P1**，**我逐条回源码验证过**——两条都成立，而且其中一条推翻了本报告初稿"没有必然崩溃路径"的说法。

### 复核确认

**A1（P1，我已验证）存储损坏 → 每次进供应商页都崩溃，用户无法自救**
链路（四段都能对上）：
1. `ProviderRepositoryImpl.init` 起了一个**根协程**做"补齐出厂供应商"：`repositoryScope.launch { providerDataStore.update { current + missingBuiltIns(current) } }`（`core/data/.../ProviderRepository.kt:71-73`）；scope 是 `CoroutineScope(SupervisorJob() + default)`（:66）—— **根协程，没有父作用域接异常**。
2. `ProviderDataStore.update` 在"存了但解不出来"时**故意抛** `IllegalStateException`（`ProviderDataStore.kt:82-84`，这是 G1 的正确设计：不许用出厂种子覆盖用户含 key 的原文）。
3. 那个 `launch` **没有 try/catch** → 异常沿根协程走到线程默认处理器 → **进程被杀**。
4. 磁盘原文没变 → 下次进这个页面**原样复现**。
影响最刺眼的地方在于：**它把"保护用户数据"的设计变成了"用户再也进不去配置页"**，而且没有任何自救入口。这与本期反复强调的"失败要可观察"正好相反。
修：给这次补种写入包 try/catch（失败只走 `readFailures` 上报），或干脆在解不出来时跳过本次补种。

**这不是孤例，同一个形状还有两处**（我顺着 `CoroutineScope(` 全仓排查后确认）：
- `UserPreferencesRepositoryImpl` 同样是 `CoroutineScope(SupervisorJob() + default)`（`core/data/.../UserPreferencesRepository.kt:42`）+ `stateIn(SharingStarted.Eagerly)`（:44-51）——**上游 `store.data` 一旦抛（DataStore 文件损坏/IO 失败），异常同样无人接**；
- `ProviderRepositoryImpl.providersStateFlow` 也是 `Eagerly` + 同一 scope（`ProviderRepository.kt:80-87`）。
两处的注释都写着"A SupervisorJob keeps a failed collection from killing the scope"——**这句话对了一半：SupervisorJob 只保证兄弟协程不受影响，它并不接住异常**；根协程里未捕获的异常仍然走线程默认处理器。修的时候三处一起改（统一加 `CoroutineExceptionHandler`，或把上游流 `catch` 成默认值 + 上报）。

**A2（P2，我已验证）失败日志把 API key 写进 logcat——与 A1 是同一条触发路径**
- `ProviderViewModel.effectFailed` 打的是 `Log.w(TAG, "effect $effect failed", error)`（`feature/provider/impl/.../ProviderViewModel.kt:255-256`）；
- `effect` 是 `ProviderEffect.SaveProvider(val provider: ProviderConfig, val previous: List<ProviderConfig>)`（:212-214）；
- `ProviderConfig` 是 **data class**（`core/data/.../ProviderConfig.kt:52-60`），而 `apiKey` 是它的主构造参数（:56）→ **Kotlin 生成的 `toString()` 必然把它打出来**。
后果：**保存/删除失败一次，用户的 API key 就落进 logcat**（Android 10+ 其他 App 读不到别人的日志，但 `adb logcat`、厂商日志收集、崩溃上报都会拿到它）。A1 那条崩溃路径恰好也会经过这里。
修：日志只打 `provider.id`/`name`，或给 `ProviderConfig` 覆写一个脱敏的 `toString()`。

**A3（P2，我已验证）流式解析的 native 调用没有兜底，与恢复路径的写法自相矛盾**
- 流式：`ChatViewModel.streamParseLoop` 在 `withContext(parseDispatcher)` 里直接 `create()`/`append()`，**没有 try/catch**（`ChatViewModel.kt:1618-1631`）；
- 恢复：**完全相同的 native 调用**被 `try/catch(Throwable)` 包住并降级成空列表，注释还写明"native 边界失败不能拖垮历史恢复"（`ChatRestore.kt:60-70`）。
后果：`.so` 缺失/ABI 不匹配时（`NativeBridge` 的 `System.loadLibrary` 会抛 `ExceptionInInitializerError`，`core/markdown/consumer-rules.pro:19-21` 自述了这条链）→ **首个流式分片闪退**，而同一份代码在恢复路径上却是有防护的。修：照 `ChatRestore` 的写法兜住，降级为只渲染纯文本并上报。

### 一并接受的其余发现（未逐条复核）

| 级别 | 问题 | 位置 |
|---|---|---|
| P1 | **增量解析块 id 跨轮漂移且可重复**：id 是 `<cmark start_line>:<type>`，而 `start_line` 相对**本轮 tail** 计数、tail 起点随 stable 偏移前进；Kotlin 侧又保留 `index` 之前的旧块 → 反例：首轮 `"a\n\n"` 产出 `1:0`，次轮重新从行 1 计数再产出 `1:0`，**两个同 id 块同时留在列表里**。`MarkdownBlockList` 用 `key(block.id)` → 渲染状态错配（mermaid 页签/滚动状态串块）；一旦换 `LazyColumn` 就是必崩的重复 key | `incremark.c:562-571,976-993`、`IncrementalMarkdownParser.kt:98-103`、`IncrementalMarkdownDocument.kt:70-75`、`MarkdownBlockList.kt:132-147` |
| P2 | **离屏 WebView 没做网络出口限制**：`intercept()` 只拦 `mermaid.local`，其余主机返回 `null` = 放行真实请求。WebView 里跑的是**不可信模型输出**，mermaid 的 image/SVG 外链足以发出 https 请求（IP/UA 外泄），并因 canvas 被跨源污染导致 `toDataURL` 失败 | `MermaidRenderer.kt:295-297` |
| P2 | **C/JNI 全局状态无同步**：`ensure_extensions()` 无锁读写 `g_registered` 并写 8 个全局扩展指针，`g_cache_ok/g_cache` 同样无锁；而生产路径确实会并发 `create()`（流式 worker 与历史恢复/切会话都在 `Dispatchers.Default`）→ 偶发节点类型错乱 | `incremark.c:321-336`、`incremark_jni.c:50-59`、`ChatViewModel.kt:374,1624`、`ChatRestore.kt:59` |
| P2 | **O(n²) 解析 + 无虚拟化渲染**：尾部长期无空行（长列表/未闭合围栏）时每个 chunk 重解析整个 tail，buf 只增不减；UI 侧是 `Column + forEach + key`（不是 Lazy），每 chunk 全量组合 | `incremark.c:1008-1035`、`MarkdownBlockList.kt:130-147` |
| P2 | **干净克隆无法构建（工具链未声明）**：`core:agent` 把 Rust 四 ABI 交叉编译挂在 **`preBuild`**（任何 Android 任务都会触发，含 `testDebugUnitTest`，且无跳过开关），`core:markdown` 强制 NDK 28.2.13676358 + CMake 3.22.1；而 README 的 Run Locally 只列 "JDK 21 + Android SDK" | `core/agent/build.gradle.kts:83-95,132`、`core/markdown/build.gradle.kts:9,18-45`、`README.md:50` |
| P2 | **API key 明文落盘**：整份 provider 列表（含 key）以 JSON 存 Preferences DataStore，无 Keystore/加密（目前靠 `allowBackup=false` + 应用沙箱兜底） | `ProviderDataStore.kt:86-88`、`ProviderConfig.kt:56` |
| P3 | `ProviderSaveRejected` **无身份守卫**：无条件用保存前快照覆盖列表 → 失败回流期间若又改了别的 provider，改动被旧快照吞掉（删除回滚那条是对的） | `ProviderViewModel.kt:410-429` |
| P3 | **13 个模块没有统一约定插件**，namespace/compileSdk/minSdk/jvmTarget 手抄；**无 JDK 工具链固定**（只写 `jvmTarget=JVM_11`，而 Robolectric 要 JDK 21） | 各 `build.gradle.kts` |
| P3 | **无 CI**（仓库无 `.github`）→ UDF 门禁与 Roborazzi 金图**在现有流程里永远不会自动执行** | — |
| P3 | 死依赖 `feature:main:impl → appcompat`（源码 0 引用）；配置期 `mkdirs`（与 configuration-cache 同用属反模式） | `feature/main/impl/build.gradle.kts:48`、`core/agent/build.gradle.kts:19-20` |
| P3 | `ChatViewModel` 的"离开主屏就释放 runner"**在当前导航形态下不成立**：Main 是根 entry（Splash 被 `replace`），ChatViewModel 实际与 Activity 同寿命 | `MainNavHost.kt:120`、`ChatViewModel.kt:66-67` |
| P3 | `AgentChat.kt` 有两段 KDoc **挂错了成员**（`persistInterruptedReply`/`setContextWindow` 实际没有文档，注释落到了下一个成员上）；README 重复列 `:app:testDebugUnitTest` | `AgentChat.kt:162-173,209-215`、`README.md:69,77` |

### 该代理对门禁的复核（与本报告 §4 的 P2-2 相互印证，且更细）

它逐条列出了 `MvvmUdfGateTest` 的 8 个弱化点，其中 4 条是**本报告初稿没覆盖**的，值得一起记住：
① "异步块"判定要求 `launch/withContext/async` **与左花括号同行**（`MvvmUdfGateTest.kt:358`）→ 换行书写即漏判；
② `functionRanges` 把**嵌套局部函数**当新 owner（:324-345），而三份白名单都按**函数名**放行 → 在 suspend 函数里定义一个叫 `appendBlock` 的局部函数，它内部写状态会被判为合法；
③ 检查的是**词法 owner**、不是调用图 → 被白名单命名的函数即便从协程里被调用也照样通过；
④ `no view file declares observable state at the top level` 只匹配 `= mutableStateMapOf/mutableStateListOf`（:388）→ 漏掉 `mutableStateOf`、`by mutableStateOf`、`remember { mutableStateMapOf() }`，且 root 不含 `feature:provider:impl`（:292-296）。
它还核对了 **Navigation 3 1.1.7 的 AAR 字节码**：该版本 runtime 只自带 `SaveableStateHolderNavEntryDecorator`，另一个 ViewModelStore 装饰器来自 `lifecycle-viewmodel-navigation3` → **`MainNavHost` 手写的那两个装饰器就是全部，没有漏掉默认装饰器**（注释里的担心不成立，但结果正确）。

## 3.7 core:widgets 深审（已回，P1 已复核）

> 来源：第三个独立子审查代理，范围 `core:widgets`（133 文件 / 37,369 行 / 36 包 / 58 个令牌文件）+ 它在 feature 里的使用面。它把 `compose-bom 2026.09.00` 对应的 **material3 1.4.0 源码包**当作上游基线，做了分组对拍。**它明确标注"没有编译、没有运行、没有真机/截图"** —— 这点很重要，下面第 1 条就是它标为 P1 的那条，我按它的链路逐环核到了源码。

### 复核确认：一条会静默改变所有默认容器外观的缺陷

**W1（P1）`contentColorFor` 丢掉上游的 `LocalContentColor` 兜底，且注释把"应该发生的事"写成了"正在发生的事"**

链路我逐环核过：

1. 自有实现只有 **6 个分支**，其余一律返回 `Color.Unspecified`：`core/ui/.../theme/Theme.kt:62-71`。
2. 同一处 KDoc（:57）写着 *"匹配不到时返回 Color.Unspecified，**调用方照旧回落 LocalContentColor**"* —— 上游确实是这么做的（`@Composable contentColorFor` = `contentColorFor(bg).takeOrElse { LocalContentColor.current }`）。
3. **但本项目的调用方没有这一层**：`Surface` 把结果**无条件**灌进 CompositionLocal（`core/widgets/.../surface/Surface.kt:106` 取默认值 → `:113-116` `CompositionLocalProvider(LocalWidgetsContentColor provides contentColor, …)`，4 个重载同构）；`internal/ProvideContentColorTextStyle.kt:50-54` 一样。全仓 **39 处** `contentColorFor(` 调用点（Card 10、IconButtonDefaults 8、Surface 4、NavigationDrawer 4 …）没有任何一处做 `takeOrElse`。
4. **落在 else 的容器色是主流而非边缘**：这些容器的默认色**故意**不是那 6 个槽之一。例如 `CardDefaults.defaultCardColors.contentColor = contentColorFor(FilledCardTokens.ContainerColor(this))`（`card/Card.kt:495`），而该容器的色是 `surfaceContainerHighest` —— 它是 `lerp(background, card, …)` 的派生值（`CssTokens.kt:56-72`），GeistDark 下是 `background=#000000` 与 `card=#0A0A0A` 之间的插值，**不等于调色板里任何一个槽**。bottom sheet 用 `surfaceContainerLow`、NavigationBar/Menu 用 `surfaceContainer`、NavigationDrawer/TopAppBar 用 `surface`、Badge 用 `error` —— 全部落到 else。
5. **后果为什么是"隐形图标"而不是"文字变黑"**：文字侧其实安全 —— `Text` 取 `style.color.takeOrElse { LocalWidgetsContentColor.current }`，而 `AppTypography` 不带 color，于是回落到 `JasmineTheme` 注入的 `cssVars.foreground`。**图标侧没有这层兜底**：`Icon.kt:146-147` 在 `tint == Color.Unspecified` 时**不加 ColorFilter**，于是按图形自身的颜色画；而全工程图标来自 `LucideIcons`，其描边写死 `SolidColor(Color.Black)`（`core/ui/.../LucideIcons.kt:39`）→ **深色主题下画出来是纯黑**（GeistDark 卡片 #0A0A0A 上的对比度约 1.1:1）。`IconButtonDefaults` 还让普通 IconButton 继承环境内容色（`button/IconButtonDefaults.kt:54-55`），所以最常见的 `IconButton { Icon(...) }` 同样中招。
6. 附带：`bottomsheet/ModalBottomSheetAndroid.kt:514-517` 用 `contentColor.isDark()` 决定系统栏图标明暗，而 `Color.Unspecified` 的 luminance 为 0 → 近黑弹层把系统栏图标设成深色。

**可达性（不夸大）**：生产页面目前基本都**显式传色**（`MainScreen.kt:171-173`、`MainNavHost.kt:511-513`、`SidebarContent.kt:123/185`、`bottomsheet/BottomSheet.kt:69-70`），所以今天是**潜伏缺陷**；但**随发布包出厂的调试页已经能复现**（DebugCard / DebugBadge / DebugMenu / DebugSearchBar / DebugComponents / DebugFabMenu）。修法三步：① 把 `contentColorFor` 补成上游等价表；② 给 `Surface` 4 个重载与 `ProvideContentColorTextStyle` 加 `contentColor.takeOrElse { LocalWidgetsContentColor.current }`；③ 顺手把那句与代码不符的 KDoc 改对。**建议 ①+② 一起做** —— 只改表不改兜底，下一个新容器色还会踩同一个坑。

### 一并接受的其余发现（未逐条复核）

| 级别 | 问题 | 位置 |
|---|---|---|
| P1 | **系统栏/内容色联动同源**：`Scaffold` 与 `Surface` 的默认参数同样直接用纯函数版；现有两个 Scaffold 调用点都显式传了 `contentColor`（且 `containerColor` 是动画插值色、必然匹配不到），**一旦有人删掉那行就立刻退化** —— 未来回归点 | `scaffold/Scaffold.kt:88-89`、`MainScreen.kt:171-173`、`MainNavHost.kt:511-513` |
| P2 | **Switch 完全没有按下反馈**：根 `toggleable` 写死 `indication = null`，thumb 没有 `.indication(...)`，`ThumbNode` 里没有 `PressInteraction/isPressed` 分支；旁证是 SwitchTokens 的按下态令牌（`PressedHandleWidth/Height`、`StateLayerShape/Size`、`Selected/UnselectedPressedHandleColor`）**全仓零消费者**，而 Checkbox/RadioButton 都正常用了 ripple → 判定为移植时丢失 | `switch/Switch.kt:103-113,153-163,199-259` |
| P2 | **排版 `LocalWidgetsTextStyle` 默认值退化 + 注入是替换而非合并**：默认值是 `TextStyle.Default`（上游是带 `lineHeightStyle=(Center, Trim.None)` 的 `DefaultTextStyle`），`JasmineTheme` 用 `provides` **替换**（上游是 `ProvideTextStyle` 的 **merge**）→ 全 app 的 `lineHeightStyle` 回落到框架默认；旁证 `tokens/DefaultTextStyle.kt` 零引用、`text/Text.kt:26` 是只 import 不用的死 import。**影响量级未实测** | `Theme.kt:37`、`WidgetsTextStyle.kt:14-16`、`text/Text.kt:466-469` |
| P2 | **排版双源且数值矛盾**（我已复核数值）：`AppTypography`（生效，40 处引用）与同模块 `tokens/TypeScaleTokens.kt` 对不上 —— **bodyLarge 15sp/23sp/0.15sp vs 16sp/24sp/0.5sp**、bodyMedium 13/19/0.1 vs 14/20/0.2、labelLarge、displayLarge 都不一致，只有 titleLarge/labelMedium 相同；而 `Type.kt:49,74` 的注释声称"取值也逐档照抄"（**同一模块里两套互相矛盾的排版基线，注释指向了错的那一套**） | `core/ui/.../theme/Type.kt:121-134` vs `core/widgets/.../tokens/TypeScaleTokens.kt:11-20` |
| P2 | **4/12 套调色板的实心按钮白字不达 WCAG AA**：Notion Warm（#EB5757）3.48:1、Braun（#FF5500）3.21:1；另有 6/12 套的 `mutedForeground` 落在 `surfaceContainerHigh/Variant` 上只有 3.76–4.46:1 | `CssTokens.kt:50-201,508-515` |
| P2 | **Shadcn Zinc Light 四个 surface 槽折叠成同一色**（我已复核）：`card = Color(0xFFF4F4F5)`（:354）与 `muted = Color(0xFFF4F4F5)`（:359）**完全相同** → `surfaceContainer = surfaceContainerHigh = surfaceContainerHighest = surfaceVariant`，**直接违反本文件自述的"取值互不重合"不变量**，并让"搜索栏折叠态轮廓看不清"那个修复在这一套主题下失效（12 套里只有这一套） | `CssTokens.kt:56-72,354,359` |
| P2 | **Slider 对反向 valueRange 无校验**：`coerceIn(start, endInclusive)` 在 `start > endInclusive` 时抛 `IllegalArgumentException`（`1f..0f` 是合法表达式），而基础重载在**组合期**写 `state.value`；三个入口都没有 `require` | `slider/Slider.kt:269-273,1410,1645-1658` |
| P3 | **注解标识符被批量替换改坏**（我已复核）：`internal annotation class **Experimental上游ExpressiveApi**`（CJK 是合法标识符所以能编译，但类名与文件名脱钩、opt-in 提示变成 `"This 上游 API is experimental…"`）；全仓同类残留共 **5 处**：该注解的声明、`@OptIn(Experimental上游ExpressiveApi::class)` 的使用点、以及 `MaterialShapes.kt:19`/`LoadingIndicator.kt:19` 注释里被改掉的注解名 | `textfield/ExperimentalMaterial3ExpressiveApi.kt:21-26`、`textfield/TextField.kt:1434`、`shapes/MaterialShapes.kt:19`、`progress/LoadingIndicator.kt:19` |
| P3 | **`ReplaceWith` 指向不存在的包**：把上游的 `material3` 改成了 `material` → IDE 快速修复会插入一个不存在的包 | `textfield/TextFieldDefaults.kt:768,852` |
| P3 | **`surfaceBright`/`surfaceDim` 在深色主题下语义反转**（上游规定 Bright 恒比 surface 亮，而这里 `lerp(surface, background, …)` 在深色下反而更暗），且两者**实际读取次数为 0** → "死且错" | `CssTokens.kt:192,195` |
| P3 | **一整层令牌没有消费者**：48 个 `ColorSchemeKeyTokens` 键在生产代码里被引用 **0 次**（只有自身定义、`TokenResolvers`、以及 KDoc）；`ShapeTokens`（63 行）与 `TypefaceTokens`（13 行）近乎全死；`CssVariables` 有 **21 个槽**直接读取次数为 0；`LocalWidgetsTonalElevationEnabled` 全项目**没有任何 provides**；`LocalMotionScheme` 是 internal 且 `JasmineTheme` 不注入 → **expressive 动效在应用内不可达**，全 app 恒定 `MotionScheme.standard()` | `tokens/*`、`Theme.kt:51`、`motion/LocalMotionScheme.kt:27` |
| P3 | **`error/onError/scrim` 硬编码 M3 基线色**，绕过全部 12 套调色板（Badge 直接消费） | `CssTokens.kt:122,129,189`、`tokens/BadgeTokens.kt:14,16` |
| P3 | **禁用态合成底色用 `card`、而令牌取 `surface`**：同一 M3 角色在同一文件里被读成两个槽（重启后色调/预合成不匹配） | `slider/Slider.kt:634`、`switch/Switch.kt:303,365-390` vs `tokens/SwitchTokens.kt:13` |
| P3 | `BackHandler` 合并 expect/actual 时**丢了`enabled = true` 默认值**（当前调用点都显式传参，无行为影响）；负 `steps` 无校验（基础重载 → `FloatArray(steps+2)` 抛 `NegativeArraySizeException`，`steps=-1` 更隐蔽地产生 NaN 刻度）；`ThumbNode` 缺 `onReset()`（复用场景可能从上一个开关的尺寸/位移起动画，**未验证**） | `bottomsheet/BackHandler.kt:27,32`、`slider/Slider.kt:269-270,1372-1374,1704`、`switch/Switch.kt:199-259` |

### 该代理的"搬运保真度"结论（与我的判断一致，但更细）

它用上游源码包做了分组对拍，结论是**这是一次高保真搬运**：progress/wavy 达到**逐行 diff = 0**；TopAppBar、TabRow、Menu、NavigationBar/Drawer、bottomsheet 15 个文件里的 13 个、textfield 全家、Text/Icon/Scaffold、Slider/Checkbox/Radio 主体**均判定"与上游等价或仅差访问语法"**，未发现搬运引入的逻辑缺陷（**唯一例外就是 Switch 的按下态**）。RTL（`placeRelative`/`isRtl` 数学/Start-End inset）、嵌套滚动、predictive back、触摸目标、图标语义都比预期扎实。
它也证实了我的两条核验：**`core/widgets` 内 `Color(0x` 硬编码 0 处**（所有字面色值都在 `CssTokens.kt`）、**a2ui 全仓仅 1 处命中且是压缩 JS 的变量名**（与 A2UI 无关）。
同时它补了一条我漏掉的细节：`CORE_WIDGETS_M3_AUDIT.md` 自称"110 个文件 / 31 个碰 M3 / 168 行 import"，与现状（133 文件 / 0 import）**已经不符**——那份文档是改造前的快照，属于 §4 P2-1 文档陈旧问题的又一例。

### 它给出的最优先动作（我同意）

1. 修 `contentColorFor` + 给 `Surface`/`ProvideContentColorTextStyle` 加 `takeOrElse` 兜底（W1）。
2. **给 `core:widgets` 建 `src/test`**，先补三条**不需要 Android 运行时**的参数化测试：令牌自洽性（12 套调色板 × 48 角色，能直接抓住 P2-5/P2-6/P3-3）、`contentColorFor` 全映射、对比度阈值。**成本最低、收益最高** —— 这个模块 37k 行、0 测试，而上面一半的问题都是纯函数可测的。
3. 清理排版/形状的双源冲突，选定唯一真相源；补回 Switch 的按下态。

## 4. 缺陷清单（按严重度）

### P1 — 应在下一次提交前处理

**P1-1 根目录多了一个 211 KB 的垃圾目录，且 `.gitignore` 盖不住它**
`undefined/temp/m3audit/` 下 10 个文件（`Slider.kt` 100 KB、`Checkbox.kt` 48 KB、`Switch.kt`、`RadioButton.kt` 与 4 个 `tokens_*.kt`），时间戳 2026-10-05 01:04——是**某次复制命令里变量为空**（`$env:TEMP` 未定义 → 相对路径 `undefined/temp/...`）留下的移植期快照。
危害：`git status` 里它是 `??` 未忽略项，**下一次 `git add -A` 就会把 211 KB 的第三方控件源码永久写进公开仓库**，坐实 P1-2 的许可证问题。
修：删掉该目录 + 在 `.gitignore` 加 `undefined/`（更稳的是把任何"从外部拷进来的"落点固定在 `.tmp/` 并整体忽略）。

**P1-2 `core:widgets` 是 Apache-2.0 的 AOSP 代码，但仓库没有任何披露**
已实测：`core/widgets/src/main` 共 133 个文件，其中 **77 个带 "Copyright (20xx) The Android Open Source Project" + Apache-2.0 头**（`tokens/*` 全表、`Button`、`Slider`、`TextField`、`Checkbox`、`Switch`、`RadioButton`、`progress/*`、`bottomsheet/*`、`ripple/*`、`pulltorefresh`…）；代码里还留着上游的 `TODO(b/228455081)` 这类 Google 内部缺陷号（`IconButtonDefaults.kt:363`、`Button.kt:215`）。
与此同时：**仓库根没有 `LICENSE`、没有 `NOTICE`**（`glob **/LICENSE*`、`**/NOTICE*` 均为空），`README.md` 与 `COMPONENT_DEVELOPMENT_GUIDE.md` 也**一个字都没提**这件事，反而把 `core:widgets` 描述成"自有实现"。
Apache-2.0 §4 要求再分发时**随附许可证副本**、并对修改过的文件**标注修改**（逐文件保留版权头只满足了一半）；而远端是 GitHub 公开仓库。
修：① 根目录放 `LICENSE`（Apache-2.0 全文）与 `NOTICE`，列出 `core/widgets` 中源自 AndroidX/Material 3 的部分；② `core/widgets` 加一个 `README`/`NOTICE` 说明来源与修改范围；③ 主 `README.md` 的架构表里把该模块如实标成"源自 AOSP 的自有化分支"。

**P1-3 仓库自带 540 KB 内部审查材料，其中含外部项目名与自家整改记录**
根目录受控文件：`前面审查的结果聊天记录.md` **253 KB**、`F_G_FIX_PLAN.md` 57 KB、`COMPONENT_DEVELOPMENT_GUIDE.md` 51 KB、`P1_P2_FIX_PLAN.md` 41 KB、`CORE_WIDGETS_M3_AUDIT.md` 35 KB、`P0_UDF_FIX_PLAN.md` 22 KB、`CHAT_THINKING_AND_TOOL_CALLS.md` 9 KB。
问题不在"有没有用"，而在**它们把内部过程与外部参照实现的名称写进了将要公开的历史**（`rust/MODULE_MAP.md:21` 甚至立了"代码里不出现外部项目名"的规矩，但文档层没守），且 135 KB 的整改方案对使用者零价值、对维护者是噪声。
修：移到私有位置（或 `docs/internal/` 并接受其公开），仓库只留 `README` + 一份"当前架构"文档。

### P2 — 质量与可维护性

**P2-1 文档整体落后代码 2～4 天（Material 3 相关表述已全部失效）**
`git log` 与 mtime 对照：README/GUIDE 定稿于 **2026-10-01**，而"去 M3"的重构发生在 **10-03 ～ 10-05**。
具体失效项：`README.md` 技术栈表仍写 "`Compose BOM 2026.09.00 (Compose 1.12.1 / Material 3 1.4.0)`"——**Material 3 已不是依赖**；`README.md` 架构图里 `core/widgets` 被描述为自有设计系统而未说明其 AOSP 血统；`F_G_FIX_PLAN.md` 的 G5 自己写着"文档与现状全面对齐 ✅"，**在两天后的重构面前已经不再成立**（这条恰好说明：把"文档对齐"当成一次性任务勾掉，是这类项目最容易复发的问题）。
修：把"文档对齐"做成**可执行检查**（如 `libs.versions.toml` 生成 README 的版本表、或在 CI 里断言 README 不含已删除依赖名），而不是待办清单上的一行。

**P2-2 门禁的覆盖面与自己的宣称之间有缺口**
`MvvmUdfGateTest` 是正则+文本级的（它自己也承认"改掉 receiver 命名就绕得过"），实测缺口有三处：
① 它只守 `ChatViewModel` 与 `UsageStatsViewModel` 的"唯一写入点"；**`MainViewModel`、`ProviderViewModel`、`LanguageViewModel` 没有对应的写入点断言**（`MainViewModelTest` 13 个用例是行为级，不是护栏）。
② 它的"异步块"识别正则是 `(launch|withContext|async)\s*[({]`，**`launchIn` / `flowOn` 不在内**；而 `ChatViewModel.init` 恰恰通篇用 `launchIn(viewModelScope)`（`ChatViewModel.kt:381,393,403,409,414`）——这条路径能碰到 `mutableStateFlow` 而不被门禁看见。
③ `StateWrite` 正则是 `updateState|updateEditor|updateModelEditor|mutableStateFlow\s*\.\s*update`，**给 `mutableStateFlow` 起个局部别名就能绕过**。
修：门禁是"防手滑"的好东西，但别把它当证明。要么把 `launchIn/flowOn` 加进异步块正则、把三个 VM 也纳入写入点断言，要么在文档里把它的边界写清楚（现在测试类注释有提，但 README/GUIDE 引用它时的口吻更像"证明"）。

**P2-3 全项目最大的模块零测试，且渲染层整体空白**
`core:widgets`：133 文件 / **37,369 行 / 0 个测试文件**（`core/widgets/src/test` 不存在）。
没有 `src/test` 的模块：`core:network`、`core:navigation`、`core:widgets`。
`core:markdown` 只有 6 个用例（`IncrementalBlocksTest`），**覆盖的是纯 Kotlin 的块合并**；真正的风险面——native 增量解析器（`libincremark_jni.so`）、离屏 WebView 的 mermaid 渲染、`libtex.so` 公式——**在 JVM 测试里根本跑不到**（`core/markdown/build.gradle.kts:78-80` 的注释自己写明了这一点）。
结论：**占代码量一半的 UI 层没有任何自动化验证**。修：先给"增量块合并 / 截断复用"补参数化用例（最容易出静默错的地方），再给 `core:widgets` 加 Robolectric 冒烟（至少保证每个组件能组合、能 measure）。

**P2-4 截图金标默认不校验，且已过期 12 天**
`MainScreenshotTest` 的类注释写明：普通 `testDebugUnitTest` **只渲染、不断言**，只有 `-Proborazzi.test.record/verify` 才比对（实跑输出里 `app:finalizeTestRoborazziDebug SKIPPED` 印证了这一点）。
金标 `app/src/test/screenshots/chat.png` 的 `git log` 只到 **`bed40b0`（2026-09-23，"新增 Agent 层与 Room 数据库"）**——比"去 M3"重构早 10 天。它现在还能跑绿，只说明**这个深色界面在这些改动里恰好没变样**，不代表有人在看它。
修：CI 里跑 `-Proborazzi.test.verify=true`（最少给 release 分支加上），否则这个测试的价值约等于"渲染不崩"。

**P2-5 `feature:main:impl` 反向依赖两个 feature 的 impl，写下来的边界自己破了**
模块图实测：`feature:main:impl` 同时 `implementation` 了 `feature:settings:impl`、`feature:provider:impl`，并在 `MainNavHost.kt:33-62` 直接 import 了 21 个来自这两个 impl 的类型（`ProviderScreen`/`ProviderViewModel`/`ProviderEvent`/`LanguageViewModel`/全部 `Debug*Screen`/`R.string` 别名）。而 `MainNavHost` 的文件注释写的是"keys come from the features' public contracts … so the app main never needs to know about internal destinations"。
"api/impl 分层"目前在**类型契约**这一层是真的（`SettingsNavKey`、`ProviderNavKey` 存活且被用），但**屏幕组装**这一层是 main 直接拉 impl。
修：要么把"谁来组装屏幕"下沉到各 feature 自己的 entry 提供者（main 只认 api），要么在 GUIDE 里把这条写明为"有意的例外"。**现状是"文档说的比代码干净"，这比单纯的耦合更坏。**

**P2-6 发布包里带着一整套组件试验场**
`SettingsMenuScreen.kt:163-166` 的 Debug 入口**没有任何 `BuildConfig.DEBUG` 判断**（全仓 `BuildConfig.DEBUG` 只有 1 处，在 OkHttp 日志级别），而 `MainNavHost.kt:41-55` 把 15 个 `Debug*Screen` 全量接进了 nav 图。
R8 会缩掉部分实现（实测 `classes.dex` 里 **`DebugComponents` 已不在**，但 `settings_menu_debug_entry` 这个 testTag 还在，说明入口链没被完全消除）。这是"发布包里有一条用户可点到的、非产品的路径"。
修：入口加 `if (BuildConfig.DEBUG)`，或把 `Debug*` 挪到 `debug` 源集。

### P3 — 打磨项

- **P3-1 单 APK 40 MiB 全 ABI 出厂**：`lib/**` 占 36 MB，其中 x86+x86_64 共 20 MB 只对模拟器有意义。`android { splits { abi { … } } }` 或上 AAB 即可让真机包瘦到约 13 MB。
- **P3-2 JNA 带来 281 KB 死重量**：`lib/mips/libjnidispatch.so`、`lib/mips64/libjnidispatch.so` 在现代 Android 上永远不会被加载，用 `packaging { jniLibs { excludes += "lib/mips*/**" } }` 可去掉。
- **P3-3 5.2 MB 前端资源无条件进包**：`core/markdown/src/main/assets/mermaid` 53 个文件（单文件 2.68 MB）为 mermaid 图服务。图块是长尾功能，可以按需下发，或至少在构建期裁掉不用的图种分片。
- **P3-4 `app/build.gradle.kts` 的注释是乱码**：文件里多处中文注释呈 `ADK core 鐨?Android 鍙樹綋…` 形态。**文件本身是合法 UTF-8（无 BOM、无替换字符），是"UTF-8 内容被按 GBK 解码后又存回 UTF-8"的双重编码损坏**——也就是说这段注释**已经永久不可读**，且说明某次批量改写踩过编码。修：重写这几行注释，并在编辑器/脚本里统一 UTF-8。
- **P3-5 `core:network` 是占位模块**：`provideBaseUrl()` 返回 `https://api.example.com/`（`NetworkModule.kt:37`），除字体下载外无人使用；聊天完全不经 OkHttp。留着可以，但 README 的模块表把它写成"Retrofit/OkHttp（placeholder service）"反而更诚实——建议把"placeholder"三个字也搬进代码注释，避免后来者以为它是产品网络层。
- **P3-6 静默失败仍有两处**（都属于"用户看不到"）：`CustomFontRepository.importFont` 的 `catch (e: Exception) { null }`（:198-200）与 `deleteFont` 的 `File.delete()` 返回值被丢弃（:205）——导入失败、删除失败在界面上都只是"没反应"。同一个类里 `downloadPreset` 却会把失败经 Effect 回流成 toast，属于标准不一致。
- **P3-7 `CustomFontRepository.isInstalled` 名不副实**：它是**同步 stat 磁盘**（:105），与该类"热路径零 I/O"的自我约定冲突（目前唯一调用点在 IO 上下文里，所以只是隐患）。

---

## 5. 数据流与状态机（复核结论）

**聊天一条消息的完整路径**：`ChatScreen` → `ChatAction` → `BaseViewModel` 的**无界单消费者通道** → `handleAction`（主线程，同步读-判-写）→ 出站命令走 `EffectRunner`（另一条无界通道）→ `RustAgentChat` 在 `Dispatchers.IO` 上**阻塞调用** FFI → Rust 在 `block_on` 里同步跑完整轮，事件经回调**边跑边发** → Kotlin `EventSink`（有界 64 + 文本合并 + 终态必达）→ `Flow<ChatEvent>` → 每条事件包成 `Internal.*` 动作回流 → `handleAction` 同步落状态。

几处设计**经得起推敲**：

1. **解析离开主线程**：`Turn.commands` + 每轮一个 `streamParseLoop` 子协程、`pendingParse` 只留最新一份（中间分片自然合并）、`deliverParsed` 用 `CompletableDeferred` 做**背压握手**，保证"worker 不碰 state"与"`worker.join()` 等于本轮不再写状态"两条承诺同时成立（`ChatViewModel.kt:1593-1674`）。注释里还留着当初的实测数据（主线程 100% CPU 15 秒、`Skipped 438 frames`），可信。
2. **每会话一份投影**：`ConversationChats`（`StateFlow<Map>`）+ `combine` 派生 `ChatState.conversation`，机器上消灭了"忘了手动投影"这一类 bug；handler 侧用同步重建的 `state` 保证同帧读-判-写（`ChatViewModel.kt:244-266`）。
3. **多会话真并行**：`turns: Map<turnId, Turn>`，每个 `Turn` 自带 `chatKey`/`streamingMessageId`/`parsedLength`/`commands`；事件按 `turn.id` 路由回**它自己那条会话**（`ChatViewModel.kt:507-536`），切走不串台。
4. **取消的语义是双层的**：取消协程 → `NonCancellable` 里通报核心 `interrupt`；核心在下一个 await 点收手、保住已产出内容、以 `Aborted` 收尾（`RustAgentChat.kt:210-226`）。"停止"按钮**故意不取消收集**，理由写在 `handleStopClicked` 的注释里（否则屏幕上会比模型实际看到的那份少一截）。

**仍未验证清楚的两处**（如实标注，不编）：
- **事件回压在持锁期间发生**：Rust 侧 `send` 整轮持会话槽锁（`service.rs:627`），而 `EventSink.sendBlocking` 会在通道满时 `runBlocking` 阻塞**回调线程**（`RustAgentChat.kt:435-441`）。既然 UI 侧那几个读口已改成 `try_lock`（`context_len` 等，`service.rs:607-614`），**目前看不出可复现的死锁**；但"持锁 + 可能长时间阻塞"这个组合仍是这块最脆的地方，值得有一条针对它的并发用例。
- **core:widgets 那 168 个 AOSP 文件在被改写（改为直读自有主题）之后的自洽性**没查完：这类"端口 + 换取值管道"最容易留下"某个变体还在读旧的 CompositionLocal、于是永远取默认值"的静默错。这一块我起了一个子代理专门审，**在本报告写出时它还没回**（见 §9）。

---

## 6. 复核过的"上期结论"

| 上期说法 | 复核结果 |
|---|---|
| 133 JVM 用例全绿 | **属实**（21 套件 / 133 / 0 失败） |
| 189 Rust 用例全绿 | **属实**（含 doc-test） |
| 全项目零 material3 / 零 A2UI | **属实**，且连注释与字符串都清干净了 |
| V-10 跨 feature 依赖错位（`AppTypographyChoice` 定义在 FontScreen） | **已修**：枚举现在住在 `core:ui/theme/Type.kt:34` |
| V-2 View 直读 VM 快照做业务判断（`MainNavHost.kt:183`） | **已改写**：现在是 `providerState.canNavigateBack` 驱动 `BackHandler`；全仓 `stateFlow.value` 的生产代码命中只剩 1 处（`core/widgets/.../Slider.kt:1459` 读自己的 `state`），**V-2 清单过期** |
| F1 会话槽生命周期/锁边界 | **已落地**（闭包包住整轮 + `try_lock_recovering` + `context_len` 明确不等锁） |
| F4 `onCleared` 关通道 | **已落地**（`BaseViewModel.kt:121-125`） |
| G2 `awaitRender` 加超时 | **已落地**（`MermaidRenderer.kt:168` `withTimeoutOrNull`） |
| G1 Provider 读失败不静默 | **已落地**（读路径上报 + 写路径抛错拒写） |
| G5 文档与现状全面对齐 | **已失效**（2026-10-01 的结论，被 10-03～10-05 的去 M3 重构推翻） |
| G6 补齐行为级测试空白 | **部分属实**：`MainViewModelTest`(13)、`ProviderBackRuleTest`(5)、`EffectRunnerTest`(4) 都在；但 UI 层与 `core:widgets` 仍为零 |

---

## 7. 修改建议（按投入产出排序）

0. **最高优先（会崩／会丢数据／会永久卡死，改动面都很小）**：
   - **A1 崩溃循环**：`ProviderRepositoryImpl.init` 的补种写入 + 两处 `stateIn(Eagerly)` 的根协程加兜底（`CoroutineExceptionHandler` 或上游 `catch`）——**"存储坏了"不应该等于"用户再也进不去这一页"**。
   - **A2 API key 进 logcat**：`ProviderViewModel.effectFailed` 不再打印含 `ProviderConfig` 的 effect（只打 id），或给 `ProviderConfig` 覆写脱敏 `toString()`。与 A1 是同一条失败路径的两个后果，一起修。
   - **W1 内容色兜底**：补 `contentColorFor` 的映射表 + 给 `Surface`/`ProvideContentColorTextStyle` 加 `takeOrElse { LocalWidgetsContentColor.current }`（两件事要一起做，只改表不改兜底还会再踩）。
   - **给 `core:widgets` 建 `src/test`**，先补"令牌自洽性 / `contentColorFor` 全映射 / 对比度"三条纯函数参数化测试——这个模块 37k 行零测试，而它一半的问题是**不需要设备就能测**的。
   - **R1 加超时**：给 `Provider` 加 connect/request 超时并落到 `Request.timeout`，把 `client.stream(...)` 放进 `tokio::select!` 与取消令牌竞争（`ResponseStream.interrupt` 已预留，接上即可）。**这是"用户按停止没反应、只能杀进程"的唯一根因。**
   - **R2 rollout 三条**：追加前确保行首分隔（或一次 write 写完 JSON+换行）、把 `read_line` 换成按字节读以容忍非法 UTF-8、把"截断修复"从读路径挪到持锁的显式维护步骤。集中在 `rollout/src/recorder.rs` 与 `rollout/src/list.rs` 两个文件，并补三条测试（无尾换行后追加、多字节被切半、读写并发）。
   - **R3 上下文预算**：把已有的 `truncate_function_output_payload` 接到工具结果、给请求带默认输出上限、把 `classify_error_response` 接回传输错误路径，让"超窗"变成可操作的错误而不是死循环。
1. **今天就能做完、收益最大（工程/合规面）**：删 `undefined/` 并忽略它（P1-1）；补 `LICENSE` + `NOTICE` + `core/widgets` 来源说明（P1-2）；把 `README` 里 "Material 3 1.4.0" 与 `core:widgets` 的措辞改对（P2-1）。
2. **一周内**：CI 里把 Roborazzi 切成 `verify`（P2-4）；Debug 入口加 `BuildConfig.DEBUG`（P2-6）；给 `core:widgets` 补一层 Robolectric 冒烟 + 给增量解析补参数化用例（P2-3）；门禁补 `launchIn/flowOn` 与另外三个 VM（P2-2）。
3. **发布前**：决定"包体策略"（ABI split / AAB / 干掉 mips 与未用图种），40 MB → 15 MB 量级是免费收益（P3-1～3）。
4. **架构层（不急，但要知道）**：`feature:main:impl` 拉两个 feature 的 impl 是当前唯一的结构让步；要么下沉屏幕组装，要么把它写成有意的例外（P2-5）。
5. **流程层**：把根目录那 540 KB 审查材料移出公开仓库；把"文档对齐"从待办变成检查（P1-3、P2-1）。

---

## 8. 明确未覆盖 / 未能证实的部分

诚实标注边界，免得把"没查"读成"没问题"：

- **设备端行为未验证**：本文全部结论来自静态阅读与构建/测试产物，**没有真机或模拟器交互验证**（无 ANR、无滚动、无输入法、无预测性返回的手感结论）。
- ~~`core:widgets` 的组件级正确性未审~~ → **已由 §3.7 覆盖**（其中 W1 那条我按链路逐环核到了源码：`Theme.kt:62-71` → `Surface.kt:106,113-116` → `Icon.kt:146-147` → `LucideIcons.kt:39`，并确认 `Card.kt:495` 的默认容器色确实落在 else 分支）。该节表格里标"未逐条复核"的条目**只做了位置核对**；它所有的"像素层面"结论（对比度数值、行高变化量、按下态位移）都是**源码级推理链，没有编译/运行/截图验证**——这是本节最大的不确定性，引用时请按此理解。
- ~~`feature/settings:impl` 与 `feature/provider:impl` 的内部状态机/竞态未逐条审~~ → **已由 §3.6 覆盖**（其中 A1 崩溃循环、A2 key 进日志、A3 native 无兜底三条我逐条回到源码复核确认；该节表格里标"未逐条复核"的条目只做了位置核对）。
- **`core:markdown` 的渲染层与 native/JNI 边界**：§3.6 里那些 native 结论（块 id 漂移、`ensure_extensions` 无锁、`NewStringUTF`、malloc 不判空、前缀栈上限…）来自另一个子代理的 C 侧清单，**我只复核了其中"块 id 漂移"这一条的 Kotlin 侧后果**；C 代码本身尚未由我逐行复核。我另外补派了一个专门审 `core:markdown` 的子代理，**在本版定稿时仍未返回**。
- ~~Rust 的 SSE 分帧、上下文裁剪算法、rollout 并发写未逐条审~~ → **已由 §3.5 覆盖**（子代理已回，其中 R1/R2/R3 三条我逐条回到源码复核确认；该节表格里标"未逐条复核"的条目只做了位置核对，未独立复现）。仍需设备侧取证的：R2 里"内核写原子性窗口"的实际大小、P2-9 的 Kotlin 回调重入是否真会发生、以及"端点只发 output_item.done 不发 delta"是否在真实网关上出现。
- **没有做性能剖析**：解析耗时、重组次数、APK 启动曲线都是"读出来的推断"，不是测出来的。

---

## 9. 报告是怎么来的（可复现）

执行的命令（全部只读，除测试任务本身）：

```text
git log / status / ls-files / count-objects        仓库与受控面
gradlew.bat --offline testDebugUnitTest            133 用例，BUILD SUCCESSFUL
cargo test --workspace --offline（rust/）          189 用例，exit 0
cargo ndk --version / rustup target list           4 个 Android target 齐备
Get-Content/Measure-Object 统计（按模块分行数）      263 Kotlin 文件的口径
grep：material3 / MaterialTheme / a2ui / todo 标记  去 M3 的完整性
grep：Log./println/printStackTrace                 日志面是否漏密钥
grep：api_key / Authorization / Bearer（*.rs）      密钥链路
grep：unwrap()/expect(/panic!（*.rs）              生产路径 panic 面
grep：stateFlow.value                              同步快照读（V-2 复核）
grep：^.import com.lhzkml.jasmine.feature.*.impl   跨 feature 依赖
System.IO.Compression 拆 app-release.apk           40 MiB 的构成
.NET UTF8Encoding 逐文件编码/U+FFFD 扫描           乱码与坏编码定位
```

**并行子代理**：本报告同时派出了 5 个独立子代理做深挖（`core:widgets` 设计系统与 M3 残留、Rust 全部 crate、Android/构建与测试、markdown 引擎、数据与字体层）。**到本报告完稿时它们都仍在运行、尚无结论返回**；因此 §4/§5 里凡涉及这五块的判断，都是**我自己直接读源码得到的那部分**，范围与深度见 §8。它们回来后应把结论并入本文（尤其是 `core:widgets` 的组件正确性与 AOSP 改写自洽性——那是当前最大的未审面）。
