# DeepSeek + Chat Completions：一轮多工具调用的两个 400

> 记于 2026-09-28。触发场景：把供应商预设切到 **Chat Completions** 后，让模型「测试你全部的工具」（一轮里发两个 `tool_calls`），
> 连续撞上两个 400。这里是完整的排查过程、实测结论与修法 —— 下次再遇到同类报错，先照 **§2 的方法**做，不要改代码猜。

---

## 1. 症状（两个报错，前后脚出现）

**① 调用被拆开**

```
http 400 Bad Request: An assistant message with 'tool_calls' must be followed by tool messages
responding to each 'tool_call_id'. (insufficient tool messages following tool_calls message)
```

**② 思考没回传**

```
http 400 Bad Request: The `reasoning_content` in the thinking mode must be passed back to the API.
```

两个报错都只出现在「一轮里有 ≥2 个工具调用」时；单个调用一切正常。

---

## 2. 方法：对端点跑「形状矩阵」，别猜

先用设备上的真实会话文件还原出**那一次的完整历史**（`/data/data/<pkg>/files/sessions/<y>/<m>/<d>/rollout-*.jsonl`，
每一行是一条 `response_item`），再用供应商密钥对 `https://api.deepseek.com/chat/completions` **只改形状、逐条重放**：

```powershell
# 把 messages 按形状拼成 JSON 落盘，再逐条打端点，只看 HTTP 码与报错原文
curl.exe -s -o resp.json -w "%{http_code}" `
  -H "Content-Type: application/json" -H "Authorization: Bearer <key>" `
  -d "@shape.json" https://api.deepseek.com/chat/completions
```

> 关键：**用真实历史文本**，不要用自造的短历史 —— 下面第二次矩阵就是靠这一点才定准了。

---

## 3. 实测矩阵

### 3.1 最小历史（只发一条 user + 两个调用 + 两个结果）

| # | 形状 | 结果 |
|---|---|---|
| A | 思考前缀 → 调用1 → 调用2 → 结果1 → 结果2 | ✅ 200 |
| B | 调用1 → 结果1 → 调用2 → 结果2 | ❌ 400（顺序错） |
| C | 每条调用各配一个"思考 + 单调用" | ❌ 400 |
| D | 调用与结果之间夹一条 assistant 文本 | ❌ 400 |

（A 成立 → 同一轮的调用必须**相邻**、且都在该轮思考之后。这条已经通过 `session/turn.rs` 的写入顺序满足。）

### 3.2 真实历史（从会话文件还原，两轮）

| # | 形状 | 结果 |
|---|---|---|
| **X** | **每条调用各一条 assistant 消息**（当时应用的行为） | ❌ 400 —— **与症状①逐字一致** |
| Y | 两个调用合并成一条 assistant 消息 | ✅ 200 |
| Z | 合并 + 把该轮思考放在这条消息上 | ✅ 200 |

→ 症状①的根因：**Chat 协议要的是"一条 assistant 消息带全部调用"**，而我们把每条调用翻译成了各自的一条消息。

### 3.3 真实历史 + 上一轮的纯文本回答（最终定位）

| # | 形状 | 结果 |
|---|---|---|
| **P** | 思考只挂在"带调用"的那条消息上，**纯文本回答的思考丢掉**（X 修好后的行为） | ❌ 400 —— **与症状②逐字一致** |
| **Q** | **每一轮 assistant 消息都带上它自己那轮的思考** | ✅ 200 |

→ 症状②的根因：思考必须**逐轮**回传，**不只是**带调用的那一轮。纯文本回答同样是"思考模式下的一轮"，它的
`reasoning_content` 也得原样送回去。

---

## 4. 三处根因与修法

| # | 症状 | 根因 | 修法（文件） |
|---|---|---|---|
| ① | 调用/结果不配对 | 并行执行时任务**没回来就静默丢掉**（`let Ok(outcome) = joined else { continue }`），历史里留下"有 `tool_call` 没有 `tool`"；且结果按**完成顺序**返回 | `core/src/tools/parallel.rs`：按槽位归位（**调用顺序**返回）；任何没回来的调用补一条 `The tool call did not finish.`，保证**每条调用必有结果** |
| ② | `insufficient tool messages` | Chat 翻译把同一轮的每条 `FunctionCall` 翻成**各自一条** assistant 消息 | `core/src/client.rs`：`push_chat_message` 把**连续的调用折叠到同一条** assistant 消息上 |
| ③ | `reasoning_content must be passed back` | Chat 侧**完全不解析** `reasoning_content`（全仓只有 `api/src/sse/responses.rs` 处理思考）；且翻译只把它挂给带调用的那条 | `api/src/endpoint/chat_completions.rs`（delta 加字段）、`api/src/sse/chat_completions.rs`（累积并产出 `ResponseItem::Reasoning`，位于调用之前）、`core/src/client.rs`（思考挂到**紧随其后的第一条** assistant 消息上） |

### 最终形状（实测 200）

```
system      { content: <instruction> }
user        { content: … }
assistant   { content: <上一轮的文本回答>, reasoning_content: <上一轮的思考> }   ← 纯文本轮也要带
user        { content: … }
assistant   { content: <本轮的文本>,      reasoning_content: <本轮的思考> }   ← 思考挂在这条（紧随其后）
assistant   { tool_calls: [call-1, call-2] }                                     ← 全部调用合并成一条
tool        { tool_call_id: call-1, content: … }
tool        { tool_call_id: call-2, content: … }
```

要点：**思考与它所属的那一轮绑定**；**同一轮的调用不拆**；**每条调用都有结果**。

---

## 5. 防回归

`core/src/client.rs` 里的 `every_assistant_turn_carries_its_own_thinking`（不依赖网络）钉住了 §4 的形状：
两轮历史（一轮纯文本、一轮带两个调用）→ 断言每条 assistant 消息都带自己那轮的思考、两个调用合并成一条、每条调用各有一条 `tool` 消息。

`core/src/tools/parallel.rs` 里的 `answers_come_back_in_the_order_the_calls_were_asked` 钉住执行侧：
先问的慢工具 30ms、后问的快工具立即返回 → 断言结果仍按**调用顺序**返回、且**每条调用都回报过一次**。

---

## 6. 怎么验证（装到设备上）

```powershell
.\gradlew.bat :app:assembleRelease --console=plain `
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 `
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897
adb connect 106.55.13.206:6000
adb -s 106.55.13.206:6000 install -r app\build\outputs\apk\release\app-release.apk
```

1. 供应商预设：**apiType = Chat Completions**；
2. 新开一条会话，发一句「测试你全部的工具」→ 应当看到**两张工具卡片 + 一条最终回答**（不再是 400）；
3. 再发一句普通问题（纯文本轮）→ 同样不应报 `reasoning_content`；
4. 会话文件自检：`reasoning` 条目出现在每轮 `turn_started` 之后、`function_call` 之前，且每条 `function_call` 都能配到同 `call_id` 的 `function_call_output`。

### 本次验证记录（2026-09-28 17:54，真机 Redmi 6 Pro / Android 9）

- 装机自检：设备 `base.apk` 里 `arm64-v8a/libjasmine_ffi.so` 的 SHA256 与本次构建产物**一致** → 确认跑的是新核心；
- 会话文件 `rollout-2026-09-28T17-54-49-103fbf1a-…jsonl` 的完整形状：

```
turn_started → user → reasoning → assistant(文本) → token_usage_record → turn_complete
turn_started → user → reasoning → assistant(文本)
             → function_call(current_time) → function_call(list_past_conversations)
             → function_call_output ×2
             → reasoning → assistant(文本)          ← 续轮自带思考
             → token_usage_record → turn_complete    ← 整轮正常收尾 ✅
```

- 界面：两张工具卡片 + 「两个工具都测试成功」的最终回答，**无 400 提示**；发送键从「继续」回到 ▶（回合闭合）；
- logcat：无 `400` / `reasoning_content` / `insufficient tool` 记录。


---

## 7. 教训（写给下一次）

- **先测形状，再改代码。** 两次 400 都是"对端点重放真实历史、只改形状"一次就定位的；凭直觉改代码只会换来换一个报错。
- 两个协议的形状要求**不同**：Responses 把思考与调用当成**各自独立的 item**（相邻即可），Chat 要求**思考与调用挂在同一条 assistant 消息上**。
  同一件事，两套形状。
- `parallel_tool_calls: false` 我们两套协议都在发，但**服务端不一定听**：实测里模型照样一轮发两个调用。
  也就是说**形状必须自己扛住**，不能指望请求参数把并行调用关掉。
