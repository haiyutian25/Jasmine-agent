# DeepSeek 思考模式下「一轮多个工具调用」必然 400 —— 定位与修法

> 记录一次真实踩坑：模型在一轮里并发发出两个工具调用时，回填结果后的续轮必然 400，
> 错误文本把矛头指向"思考没有带回来"，但真正的根因是**条目顺序**。
> 涉及模块：`core/src/session/turn.rs`、`protocol/src/models.rs`、`core/src/client.rs`。

## 1. 现象

应用的「测试你全部的工具」这类提问，会让模型在**同一轮**里并发发出两个工具调用
（`current_time` 与 `list_past_conversations`）。此时：

- 两个工具**都执行成功**，界面两个卡片都显示出了真实结果；
- 但紧接着的**续轮请求被拒**，界面报：

```
http 400 Bad Request: {"error":{"message":"The `reasoning_content` in the thinking mode
must be passed back to the API.","type":"invalid_request_error"}}
```

（切到 Responses 协议后，同一件事的报错换了个词：
``The `reasoning_text` in the thinking mode must be passed back to the API``）

**只调用一个工具时一切正常**。这个"单调用正常、双调用必崩"的差异，是本问题的关键线索。

## 2. 定位：对着 `api.deepseek.com/responses` 跑形状矩阵

拿着同一段上下文，只改"历史条目的顺序/内容"，直接对端点发第二轮请求，得到如下结果：

| 形状 | 结果 |
|---|---|
| 思考 → 调用1 → 结果1（**单调用**） | ✅ 200，产出最终回答 |
| 思考 → 调用1 → 结果1 → 调用2 → 结果2（**修复前的我们**） | ❌ 400 `reasoning_text must be passed back` |
| 思考 → 调用1 → **调用2** → 结果1 → 结果2（**调用相邻**） | ✅ 200 |
| 思考 → 调用1 → 结果1 → **思考** → 调用2 → 结果2（思考重复一份） | ✅ 200 |
| 不带思考条目 | ❌ 400（这一条是对的，说明思考确实必须回传） |

**结论（根因）**：DeepSeek 要求**同一轮里的所有工具调用必须相邻出现，并且都排在该轮的
`reasoning` 条目之后**。一旦在两次调用之间插进了第一条调用**结果**，第二个调用就被判定为
"没有带着思考回来"，于是整轮请求被拒 —— 报错文本只说 `reasoning_text`，容易把人引去查
"思考字段有没有带"，而真正的原因是**顺序**。

### 附带确认的两条事实

- **明文思考必须回传**：把 `content`（`reasoning_text`）原样带回即可，
  `encrypted_content` **不是必需**（实测"带 encrypted"与"不带 encrypted"两种都能过）。
- **空的 `encrypted_content` 不能发**：如果序列化时把它写成 `"encrypted_content": null`，
  服务端会直接反序列化失败：
  `Failed to deserialize the JSON body into the target type: input: invalid type: null,
  expected a string`。所以该字段必须"有值才发"（`skip_serializing_if = "Option::is_none"`）。

## 3. 根因代码

`core/src/session/turn.rs` 里，旧的回合循环把"调用 → 执行 → 结果"挤在同一个循环体里：

```rust
for call in round.tool_calls {
    let ResponseItem::FunctionCall { name, arguments, call_id, .. } = call.clone() else { continue };
    turn.history.push(call);                       // 调用 1
    let output = turn.registry.execute(&name, &arguments).await...;
    emit(ChatEvent::tool_result(name, &output));
    turn.history.push(ResponseItem::FunctionCallOutput { ... });   // 结果 1
}                                                  // 下一轮：调用 2 排在结果 1 之后 ✗
```

于是写进历史（也就是下一轮请求体）的顺序是
`…, reasoning, 调用1, 结果1, 调用2, 结果2` —— 正是矩阵里被判 400 的那一行。

## 4. 修法

把这一轮**先全部写入调用、再逐个执行写结果**（矩阵里的"调用相邻"形状）：

```rust
if round.tool_calls.is_empty() {
    break;
}

// Every call of one answer goes in before any of its results: a provider in thinking mode
// reads a call that follows a result as one whose reasoning was dropped, and refuses the
// whole request.
for call in &round.tool_calls {
    turn.history.push(call.clone());
}

for call in round.tool_calls {
    let ResponseItem::FunctionCall { name, arguments, call_id, .. } = call else { continue };
    let output = match turn.registry.execute(&name, &arguments).await {
        Ok(output) => output,
        Err(error) => error.to_string(),
    };
    emit(ChatEvent::tool_result(name, &output));
    turn.history.push(ResponseItem::FunctionCallOutput {
        id: None,
        call_id: Some(call_id),
        name: None,
        namespace: None,
        output: FunctionCallOutputPayload::from_text(output),
    });
}
```

配套（同一次修复里）：

- `protocol/src/models.rs` 的 `ResponseItem::Reasoning`：
  - `content` 照常回传（明文是必须的）；
  - `encrypted_content` 加 `skip_serializing_if = "Option::is_none"`，避免发出 `null`。
- `core/src/client.rs` 的 `responses_request`：`include: ["reasoning.encrypted_content"]`，
  向服务端索取加密态（可选，拿到就一起回传）。

## 5. 为什么绕了远路（背景）

1. **协议选错**：一开始应用里的 DeepSeek 预设是 `CHAT_COMPLETIONS`，而思考回传是
   Responses 协议的规矩。按 DeepSeek 官方与 **cc-switch**《codex ↔ DeepSeek 路由指南》的口径
   （自 v3.19.1 起 DeepSeek 预设为 `上游格式 = Responses（原生）` 直连，
   `base_url = https://api.deepseek.com`，`wire_api = responses`），我们把预设改成
   **Responses 直连** —— 这条路上思考是**一等的历史条目**（`reasoning`），不需要任何自造字段。
2. **误判成"字段缺失"**：chat 协议时代的 `reasoning_content` 报错让人以为要继续加字段，
   于是先后试过"省掉明文"和"补 encrypted"，报错只是换了个词。真正有效的做法是
   **用形状矩阵直接问服务端**（十几分钟跑完十几种顺序），而不是每改一版就重打 APK 手测。
3. 顺带修掉一个语义错误：`ChatEvent::Completed` 原先每采样一轮就发一次，而平台把它当
   "整个回合结束"，导致工具调用那一轮一结束就被掐断（工具结果与最终回答全丢）。

## 6. 验证

- **形状矩阵**：上表 5 种形状（curl 直发 `https://api.deepseek.com/responses`）。
- **设备实测**：一轮"两个并行工具调用"的会话文件（每会话一个 append-only JSONL）形如
  （省略字段）：

```
1  session_meta
2  response_item: message(user)
3  response_item: message(assistant)
4  response_item: message(user)                 «测试你全部的工具»
5  response_item: reasoning                      ← 本轮思考
6  response_item: function_call     current_time
7  response_item: function_call     list_past_conversations   ← 两次调用相邻（关键）
8  response_item: function_call_output  → 2026-09-27 18:46:05 GMT+08:00
9  response_item: function_call_output  → - 2026-09-27 18:46 — 列出你全部的工具
10 response_item: reasoning                      ← 续轮的第二段思考
11 response_item: message(assistant)             ← 最终回答（不再 400）
```

界面同样能看到两个工具卡片 + 最终回答。

## 7. 防回归建议

回合循环目前没有覆盖"一轮多调用"的测试。建议补一条**不依赖网络**的用例：用一个假的
`HttpTransport` 驱动一轮（第一轮返回两个 `function_call`，第二轮返回文本），断言写进历史的
顺序是 `reasoning, 调用1, 调用2, 结果1, 结果2` —— 这个顺序正是本问题的全部分量所在。

## 8. 参考

- cc-switch《codex ↔ DeepSeek 路由指南（中文）》：DeepSeek 预设为 Responses 原生直连
- DeepSeek API 文档：Integrate with Codex / Using the Responses API
- 同类报错的社区记录：claude-code-router #1378、opencode #28945（均为此 400 的变体）
