#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]
//! 跨语言边界。
//!
//! # 这一层是新增的
//!
//! Android 侧没有对应物 —— 那边核心与界面在同一个运行时里，不存在边界。
//! 一旦核心搬进 Rust，就多出这一层，所以它必须尽量薄：**只做翻译，不做决策**。
//! 任何"顺手在这里处理一下"的逻辑，最后都会变成两个平台各有一份、
//! 且没人知道哪份是对的。
//!
//! # 边界两侧的形状
//!
//! | 方向 | 平台侧 | 这里 | 核心侧 |
//! |---|---|---|---|
//! | 调用进来 | `startConversation` / `send` / `respondToPrompts` / `endConversation` | [`AgentHandle`] | [`jasmine_core::session::AgentChatService`] |
//! | 事件出去 | 界面消费 | [`EventListener`] | [`jasmine_core::session::ChatSink`] |
//! | 数据进来 | 会话索引、时钟 | [`HostConversations`] / [`HostClock`] + 两个适配器 | [`jasmine_core::host`] 的 trait |
//!
//! # 为什么事件是回调
//!
//! 平台侧的核心抽象是"流"，而跨语言边界上 Rust 的流没法直接交给平台侧，
//! 只能反向由 Rust 主动调平台。所以核心内部（[`jasmine_core::session::ChatSink`]）
//! 就已经是回调形态，这里只做一次转发 —— 若核心侧用流、边界再转回调，
//! 就会多出一处"事件可能被缓冲/丢失"的地方。
//!
//! # 绑定
//!
//! 用 UniFFI：这里的注解是导出面的唯一来源，Kotlin 侧由生成物提供。
//!
//! ```text
//! cargo run -p jasmine-ffi --features bindgen-cli --bin uniffi-bindgen -- \
//!     generate --library <cdylib> --language kotlin --out-dir <输出目录>
//! ```
//!
//! 跨边界类型定义在 `jasmine-protocol` / `jasmine-model-provider-info` 里（它们各自在
//! `uniffi` 特性下生成脚手架，这里 reexport），所以注解长在真正的类型上，边界上不做镜像。

uniffi::setup_scaffolding!();
jasmine_protocol::uniffi_reexport_scaffolding!();
jasmine_model_provider_info::uniffi_reexport_scaffolding!();

use std::sync::Arc;

use jasmine_core::host::Clock;
use jasmine_core::session::{AgentChatService, AgentError, ChatSink, SessionError};
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider_info::ModelConfig;
use jasmine_model_provider_info::{ModelProviderInfo, WireApi};
use jasmine_protocol::protocol::AppUsageStats;
use jasmine_protocol::{ChatEvent, SessionId};

/// 平台侧要显示的会话摘要。
///
/// 时间给的是毫秒时间戳：界面自己按设备时区显示（时区属于平台）。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConversationSummary {
    pub session_id: String,
    pub title: String,
    pub provider_id: String,
    pub model_id: String,
    pub updated_at: i64,
}

/// 一条会话上次报的用量：给界面恢复面板用。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ContextUsageSnapshot {
    pub info: jasmine_protocol::protocol::TokenUsageInfo,
    pub breakdown: Vec<jasmine_protocol::protocol::ContextUsageBreakdownItem>,
}

/// 平台侧要显示的一条转写。
///
/// 工具结果带 `tool_call_id`：它是把结果放回发起它的那次调用旁边的唯一线索。
#[derive(Debug, Clone, uniffi::Record)]
pub struct HistoryEntry {
    pub role: jasmine_protocol::Role,
    pub text: String,
    pub tool_call_id: Option<String>,
    /// 这一行代表"被停止的回合"时给出它跑了多久（毫秒），否则为空。
    pub stopped_after_ms: Option<u64>,
    /// 记录这条的时刻（epoch 毫秒）；0 表示文件里没有时间信息。
    pub recorded_at: i64,
    /// 这条属于的那个回合用的模型名 —— 也就是用户那条消息发给的模型。
    pub model_label: Option<String>,
    /// 这一行代表一次工具调用时给出它的名字 / 参数 / 结果（调用与结果合成一行）。
    pub tool_name: Option<String>,
    pub tool_detail: Option<String>,
    pub tool_result: Option<String>,
    /// 这次工具调用走到哪一步了（与 ZCode 的 `chat.toolCall.status.*` 一一对应）：
    /// `pending` / `running` / `completed` / `failed` / `denied` / `stopped`；非工具行为空串。
    pub tool_status: String,
    /// 这一轮回答之前模型「想过」的内容（深度思考）；空串表示没有。
    pub thinking: String,
}

/// 平台侧实现的时钟。
#[uniffi::export(with_foreign)]
pub trait HostClock: Send + Sync {
    /// 形如 `2026-09-27 10:31:05 GMT+08:00`。
    fn now(&self) -> String;

    /// 同一形状，但针对一个已经记录下来的时刻：列表要按设备时区读。
    fn format(&self, timestamp: String) -> String;
}

/// 核心往平台推的事件。
#[uniffi::export(with_foreign)]
pub trait EventListener: Send + Sync {
    fn on_event(&self, event: ChatEvent);
}

/// 会话存储变更的推送口（D5）：核心每次落盘（创建 / 删除 / 追加）都会发一次信号。
///
/// 与 [EventListener] 不同，它是**常驻**的（构造后注册一次，见 `set_store_listener`），
/// 因为它描述的是"存储这个事实变了"，不属于某一次调用。它只发信号不带数据 —— 平台收到后
/// 自己去 `conversations()` 刷新，并且要自己做防抖：回合中途每写一行都会触发。
#[uniffi::export(with_foreign)]
pub trait ConversationStoreListener: Send + Sync {
    fn on_store_changed(&self);
}

/// 把平台的推送口接成核心要的形状。
struct StoreListenerAdapter {
    listener: Arc<dyn ConversationStoreListener>,
}

impl jasmine_core::session::StoreListener for StoreListenerAdapter {
    fn on_store_changed(&self) {
        self.listener.on_store_changed();
    }
}

/// 平台侧配置进来的 provider。
///
/// 密钥在最后一刻才拼进来（[`ResolvedProvider`]），这样"元信息"可以被界面随便传阅，
/// 而密钥只在必要的那一层出现。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ProviderInput {
    pub id: String,
    pub name: String,
    pub base_url: String,
    pub wire_api: WireApi,
    pub api_key: String,
    /// 这个 provider 下配置的模型（界面填的 token 预算就挂在这里）。
    pub models: Vec<ModelInput>,
}

/// Agent 行为设置：界面只给**值**，规则在核心（见 `jasmine_core::agent_settings`）。
///
/// 眼下只有回复语言；往后别的 Agent 控制项也往这里加 —— 界面那边只是选择器 + 一个值。
#[derive(Debug, Clone, uniffi::Record)]
pub struct AgentSettings {
    /// 模型回复语言：`auto`（跟随输入）/ `app`（跟随应用语言）/ `en` / `zh-Hans` / `zh-Hant`。
    pub output_language: String,
    /// 界面当前的语言（BCP-47，如 `zh-CN` / `zh-TW`）；"跟随应用语言"时用它定语言。
    pub app_language: String,
}

/// 平台侧配置进来的一个模型。
///
/// 只带核心用得上的数：名字（出厂目录给的，空 = 显示 model_id）、界面上那两栏 token 预算（0 表示
/// "没设置"），以及**默认推理档**（目录外的模型才用得上）。档位**表**不在这里 —— 那由核心的模型
/// 目录说了算（见 [`provider_catalog`]），会话的起点档也是先问目录。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ModelInput {
    pub id: String,
    pub model_id: String,
    pub name: String,
    pub context_length: u32,
    pub max_output_length: u32,
    /// 这个模型的默认推理档（线上取值）；空 = 未设置。
    pub reasoning_effort: String,
}

impl ProviderInput {
    pub fn into_resolved(self) -> ResolvedProvider {
        let info = ModelProviderInfo {
            id: self.id,
            name: self.name,
            base_url: self.base_url,
            wire_api: self.wire_api,
            is_built_in: false,
            models: self
                .models
                .into_iter()
                .map(|model| jasmine_model_provider_info::ModelConfig {
                    id: model.id,
                    model_id: model.model_id,
                    name: model.name,
                    context_length: model.context_length,
                    max_output_length: model.max_output_length,
                    reasoning_effort: model.reasoning_effort,
                    ..Default::default()
                })
                .collect(),
            // 重试次数与空闲超时用统一默认值：界面不该让用户配这个，配错只会更难排查。
            request_max_retries: None,
            stream_idle_timeout_ms: None,
        };
        ResolvedProvider::from_config(info, self.api_key)
    }
}

/// 把平台的时钟接成核心要的形状。
pub struct ClockAdapter {
    host: Arc<dyn HostClock>,
}

impl ClockAdapter {
    pub fn new(host: Arc<dyn HostClock>) -> Self {
        Self { host }
    }
}

impl Clock for ClockAdapter {
    fn now_formatted(&self) -> String {
        self.host.now()
    }

    fn format(&self, timestamp: &str) -> String {
        self.host.format(timestamp.to_string())
    }
}

/// 把核心的事件出口接到平台的事件监听。
struct ListenerSink {
    listener: Arc<dyn EventListener>,
}

impl ChatSink for ListenerSink {
    fn emit(&mut self, event: ChatEvent) {
        self.listener.on_event(event);
    }
}

/// 暴露给平台的会话句柄。
///
/// 方法名与 Android 侧现有的会话接口保持一致（`startConversation` / `send` /
/// `respondToPrompts` / `endConversation`）—— 这样界面层的改动只在于"实现换了一个"，
/// 调用点不用重写。
/// 跨边界的失败：**分型** + 一句可显示的原因。
///
/// 分型不是装饰：界面要据它决定给用户的说法与"重试有没有意义"（网络问题 vs 这条会话在核心侧
/// 已经没有了 vs 本地会话文件坏了），以前只能去匹配那句中文/英文文本 —— 文案一改就 silently 失效。
/// 每个变体都带 `detail`（HTTP 状态、服务端错误文本、调用顺序错误…），界面直接显示它。
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum AgentFailure {
    /// 调用到来时这条会话没有附着（调用顺序错），或它已经被释放 / 删除。
    #[error("{detail}")]
    NoSession { detail: String },
    /// 网络 / 传输层失败（连不上、超时、TLS、服务端报错…）—— 重试有意义。
    #[error("{detail}")]
    Transport { detail: String },
    /// 本地会话文件读写失败（损坏、权限、磁盘…）—— 重试通常没用。
    #[error("{detail}")]
    Transcript { detail: String },
    /// 核心内部状态或运行时问题（互斥量中毒、runtime 起不来、调用时序不对…）。
    #[error("{detail}")]
    Internal { detail: String },
}

impl From<AgentError> for AgentFailure {
    fn from(error: AgentError) -> Self {
        let detail = error.detail();
        match error {
            AgentError::NoSession => Self::NoSession { detail },
            // 传输层的两类：建客户端失败，与请求/解析失败（都在网络那一侧）。
            AgentError::Transport(_) | AgentError::Session(SessionError::Api(_)) => {
                Self::Transport { detail }
            }
            AgentError::Transcript(_) => Self::Transcript { detail },
            // 中毒 / runtime / 调用时序：都不是网络与文件的问题。
            AgentError::Poisoned
            | AgentError::Runtime(_)
            | AgentError::Session(
                SessionError::NoPromptWaiting
                | SessionError::AnswerCountMismatch { .. }
                | SessionError::TurnAborted,
            ) => Self::Internal { detail },
        }
    }
}

#[derive(uniffi::Object)]
pub struct AgentHandle {
    inner: AgentChatService,
}

#[uniffi::export]
impl AgentHandle {
    /// 装配。平台给出会话目录与自己的时钟。
    #[uniffi::constructor]
    pub fn new(sessions_dir: String, clock: Arc<dyn HostClock>) -> Self {
        Self {
            inner: AgentChatService::new(
                std::path::PathBuf::from(sessions_dir),
                Arc::new(ClockAdapter::new(clock)),
            ),
        }
    }

    /// 注册存储变更监听（D5）：核心每次落盘都会回调它。重复注册替换旧的。
    pub fn set_store_listener(&self, listener: Arc<dyn ConversationStoreListener>) {
        self.inner
            .set_store_listener(Arc::new(StoreListenerAdapter { listener }));
    }

    /// 附着会话。失败原因是给界面看的字符串（跨边界不做错误类型学）。
    ///
    /// [instruction] 是平台给的**人格**；[settings] 是 Agent 行为设置（回复语言那类）——
    /// 系统指令由**核心**拼（人格 + 语言规则），界面只传值。
    pub fn start_conversation(
        &self,
        session_id: String,
        provider: ProviderInput,
        model_id: String,
        instruction: String,
        settings: AgentSettings,
    ) -> Result<(), AgentFailure> {
        // 界面配的模型参数在这里落到核心：按 id 找到那个模型，带上它的 token 预算
        // （上下文窗口等）；没配过就退回默认值（0 = 未设置）。
        let model = provider
            .models
            .iter()
            .find(|candidate| candidate.model_id == model_id)
            .map(|candidate| ModelConfig {
                id: candidate.id.clone(),
                model_id: candidate.model_id.clone(),
                name: candidate.name.clone(),
                context_length: candidate.context_length,
                max_output_length: candidate.max_output_length,
                reasoning_effort: candidate.reasoning_effort.clone(),
                ..ModelConfig::default()
            })
            .unwrap_or_else(|| ModelConfig {
                id: model_id.clone(),
                model_id,
                ..ModelConfig::default()
            });
        self.inner
            .start_conversation(
                &SessionId::new(session_id),
                provider.into_resolved(),
                &model,
                &instruction,
                &jasmine_core::agent_settings::AgentSettings {
                    output_language: settings.output_language,
                    app_language: settings.app_language,
                },
            )
            .map_err(AgentFailure::from)
    }

    /// 建会话：平台的"新建对话"调它，标题/provider/model 由平台给。
    pub fn create_conversation(
        &self,
        session_id: String,
        provider_id: String,
        model_id: String,
        title: String,
    ) -> Result<(), AgentFailure> {
        self.inner
            .create_conversation(&SessionId::new(session_id), &provider_id, &model_id, &title)
            .map_err(AgentFailure::from)
    }

    /// 删会话：连同它的会话文件一起删掉。
    pub fn delete_conversation(&self, session_id: String) -> Result<(), AgentFailure> {
        self.inner
            .delete_conversation(&SessionId::new(session_id))
            .map_err(AgentFailure::from)
    }

    /// 某条会话的上下文里当前有多少条消息。
    ///
    /// 给平台做诊断用（也可以在附着会话后自检"历史是否装载成功"）。
    pub fn context_len(&self, session_id: String) -> u64 {
        self.inner.context_len(&session_id) as u64
    }

    /// 某条会话的上下文窗口（token 数）。
    ///
    /// 附着会话后读它，就能知道这个会话现在按多大的窗口算 —— 这个数在会话第一次附着时就定下
    /// 了（当时选的模型的上下文长度，没配则默认 200K），会话中途换模型不会变。还没附着会话时
    /// 没有可报的值。
    pub fn context_window(&self, session_id: String) -> Option<i64> {
        self.inner.context_window(&session_id)
    }

    /// 某条会话自己记录的上下文窗口；没记录过就为 `None`。
    ///
    /// 不需要先附着 —— 界面刚打开一条会话、还没发消息时，用它把窗口设置显示成这条会话的值，
    /// 而不是界面自己的默认值。
    pub fn conversation_context_window(
        &self,
        session_id: String,
    ) -> Result<Option<u64>, AgentFailure> {
        self.inner
            .conversation_context_window(&SessionId::new(session_id))
            .map_err(AgentFailure::from)
    }

    /// 某条会话上次报的用量（累计 + 最近一次 + 窗口，以及那轮请求的构成）；没记录过就为 `None`。
    ///
    /// 不需要先附着 —— 界面打开一条会话、还没发消息时用它把面板里的数补回来（进程重启后
    /// 这是唯一来源）。
    pub fn conversation_usage(
        &self,
        session_id: String,
    ) -> Result<Option<ContextUsageSnapshot>, AgentFailure> {
        self.inner
            .conversation_usage(&SessionId::new(session_id))
            .map(|usage| usage.map(|(info, breakdown)| ContextUsageSnapshot { info, breakdown }))
            .map_err(AgentFailure::from)
    }

    /// 整个 App **本月**的用量：累计 token、当前/最长连续天数、逐日用量、按模型的花销。
    ///
    /// 不需要先附着 —— 它把每个会话文件里的逐轮用量差出来加总，与界面当前打开哪条会话无关。
    /// **只算本月**：比本月 1 号早的记录不进统计，所以上个月的数字会自己消失（旧记录仍留在会话
    /// 文件里，那本来就是转写）。
    pub fn usage_stats(&self) -> Result<AppUsageStats, AgentFailure> {
        self.inner.usage_stats().map_err(AgentFailure::from)
    }

    /// 设定当前会话的上下文窗口，落进它的会话文件。
    ///
    /// 选择权在平台。写完立刻回一条用量事件（如果这一轮已经报过用量），界面不必等到下一轮
    /// 回答才更新。
    pub fn set_context_window(
        &self,
        session_id: String,
        tokens: u64,
        listener: Arc<dyn EventListener>,
    ) -> Result<(), AgentFailure> {
        let mut sink = ListenerSink { listener };
        self.inner
            .set_context_window(&session_id, tokens, &mut sink)
            .map_err(AgentFailure::from)
    }

    /// 某条会话的推理档位（空串 = 未设置）；它没附着时为 `None`。
    pub fn reasoning_effort(&self, session_id: String) -> Option<String> {
        self.inner.reasoning_effort(&session_id)
    }

    /// 某条会话自己记录的推理档位；没记录过就为 `None`。
    ///
    /// 不需要先附着 —— 界面刚打开一条会话、还没发消息时，用它把输入框那边的档位显示成这条会话自己的
    /// 值，而不是界面或模型的默认值。
    pub fn conversation_reasoning_effort(
        &self,
        session_id: String,
    ) -> Result<Option<String>, AgentFailure> {
        self.inner
            .conversation_reasoning_effort(&SessionId::new(session_id))
            .map_err(AgentFailure::from)
    }

    /// 改这个会话的推理档位，并把这次改动**追加**进它的文件（历史一条不删）。
    pub fn set_reasoning_effort(
        &self,
        session_id: String,
        value: String,
    ) -> Result<(), AgentFailure> {
        self.inner
            .set_reasoning_effort(&session_id, &value)
            .map_err(AgentFailure::from)
    }

    /// 平台侧栏要的会话列表（最新的在前）。
    ///
    /// 读不出来是 `Err`（D5），不是空表：平台据此保留上一次成功的列表，而不是把界面清空。
    pub fn conversations(&self) -> Result<Vec<ConversationSummary>, AgentFailure> {
        self.inner
            .conversations()
            .map(|summaries| {
                summaries
                    .into_iter()
                    .map(|summary| ConversationSummary {
                        session_id: summary.session_id,
                        title: summary.title,
                        provider_id: summary.provider_id,
                        model_id: summary.model_id,
                        updated_at: summary.updated_at,
                    })
                    .collect()
            })
            .map_err(AgentFailure::from)
    }

    /// 某个会话的转写（按发生顺序）。读不出来是 `Err`（D5）；会话不存在是空表。
    pub fn transcript(&self, session_id: String) -> Result<Vec<HistoryEntry>, AgentFailure> {
        self.inner
            .transcript(&SessionId::new(session_id))
            .map(|entries| {
                entries
                    .into_iter()
                    .map(|entry| HistoryEntry {
                        role: entry.role,
                        text: entry.text,
                        tool_call_id: entry.tool_call_id,
                        stopped_after_ms: entry.stopped_after_ms,
                        recorded_at: entry.recorded_at,
                        model_label: entry.model_label,
                        tool_name: entry.tool_name,
                        tool_detail: entry.tool_detail,
                        tool_result: entry.tool_result,
                        tool_status: entry.tool_status,
                        thinking: entry.thinking,
                    })
                    .collect()
            })
            .map_err(AgentFailure::from)
    }

    /// 往**这一条**会话发一轮并流式回调事件。
    ///
    /// 一轮只占它自己那条会话：别的会话照常附着着、照常跑（见 `AgentChatService`）。
    pub fn send(
        &self,
        session_id: String,
        text: String,
        listener: Arc<dyn EventListener>,
    ) -> Result<(), AgentFailure> {
        let mut sink = ListenerSink { listener };
        self.inner
            .send(&session_id, &text, &mut sink)
            .map_err(AgentFailure::from)
    }

    /// 提交提问的答案（按提问顺序收齐后一起提交）。
    pub fn respond_to_prompts(
        &self,
        session_id: String,
        answers: Vec<String>,
        listener: Arc<dyn EventListener>,
    ) -> Result<(), AgentFailure> {
        let mut sink = ListenerSink { listener };
        self.inner
            .respond_to_prompts(&session_id, &answers, &mut sink)
            .map_err(AgentFailure::from)
    }

    /// 某个会话还没写完的回合（有值就说明可以继续）。答的是文件里的事实，不需要先附着。
    pub fn interrupted_turn(&self, session_id: String) -> Result<Option<String>, AgentFailure> {
        self.inner
            .interrupted_turn(&session_id)
            .map_err(AgentFailure::from)
    }

    /// 继续被中断的那一回合：不加用户消息，在同一个回合里接着采样。
    pub fn recover_turn(
        &self,
        session_id: String,
        listener: Arc<dyn EventListener>,
    ) -> Result<(), AgentFailure> {
        let mut sink = ListenerSink { listener };
        self.inner
            .recover_turn(&session_id, &mut sink)
            .map_err(AgentFailure::from)
    }

    /// 停掉**这条会话**正在跑的那一轮。不是硬中断：回合在下一个等待点收手，已经产出的条目照旧落盘，
    /// 并以「被中断」收尾。别的会话那一轮不受影响。
    pub fn interrupt(&self, session_id: String) -> Result<(), AgentFailure> {
        self.inner
            .interrupt(&session_id)
            .map_err(AgentFailure::from)
    }

    /// 把取消时留下的半段回复写回会话。
    pub fn persist_interrupted_reply(
        &self,
        session_id: String,
        text: String,
    ) -> Result<(), AgentFailure> {
        self.inner
            .persist_interrupted_reply(&session_id, &text)
            .map_err(AgentFailure::from)
    }

    /// 释放**这一条**会话（历史不动，别的会话也不动）。
    pub fn end_conversation(&self, session_id: String) {
        self.inner.end_conversation(&session_id);
    }

    /// 交还这个 handle 持有的全部资源：所有会话脱离、存储监听清空、runtime 交还。
    ///
    /// 给**生命周期边界**用（测试，以及将来一个进程里多个 handle 的场景）。Android 上 handle 是
    /// 进程级单例、进程结束即回收，所以生产路径不必须调用；**幂等**，重复调用无副作用。
    ///
    /// 名字不是 `close`：UniFFI 生成的 `AgentHandle` 已经带 `AutoCloseable.close()`（= 释放这个
    /// Rust 对象本身），两者语义不同，重名会撞上。
    ///
    /// 调用之后这个 handle 只读可用（`conversations` / `transcript` 走文件系统），任何要跑回合的
    /// 调用会以"runtime 起不来"明确报错 —— 这正是"资源已交还"的意思。
    pub fn shutdown(&self) {
        self.inner.shutdown();
    }
}

/// 探测：这条配置能不能答话。
#[uniffi::export]
pub fn probe(provider: ProviderInput, model_id: String) -> jasmine_protocol::ProbeResult {
    jasmine_core::probe::probe(&provider.into_resolved(), &model_id)
}

/// 列出这家端点提供的模型（`GET {base_url}/v1/models`）。界面那边的"拉取模型"走这里 ——
/// 各家响应形状的适配在核心（见 `model-provider-info` 的 `presets`）。
#[uniffi::export]
pub fn list_models(provider: ProviderInput) -> Result<Vec<String>, AgentFailure> {
    // 这一步是纯网络往返（拉端点自己报的模型列表），所以失败归到传输那一类。
    jasmine_core::models::list_models(&provider.into_resolved())
        .map_err(|detail| AgentFailure::Transport { detail })
}

/// 核心目录里的一个模型：认得它，界面就能把表单填好（名字、上下文容量、档位表）。
#[derive(Debug, Clone, uniffi::Record)]
pub struct ProviderCatalogModel {
    pub model_id: String,
    pub name: String,
    pub context_length: u32,
    /// 目录声明这个模型支持哪些档（线上取值）；空 = 没声明，界面按"不限制"处理。
    pub levels: Vec<String>,
}

/// 这家供应商要用的**目录知识**（核心认得的模型）。
///
/// 界面拿它干三件事：填表单（名字、上下文容量）、判"这个 id 是不是目录里的模型"（是的话档位就不在
/// 配置页填），以及把**档位表**交给聊天页那张档位面板 —— 目录只在核心，这里出去的是**值**。
///
/// 这家**自己没目录**时（OpenRouter 那种聚合网关、用户自己加的端点）给的是现有那几家的并集：按模型
/// id 认，所以聚合网关上的 `deepseek-v4-pro`、`gpt-5.5` 也照样认得。
#[uniffi::export]
pub fn provider_catalog(provider_id: String) -> Vec<ProviderCatalogModel> {
    jasmine_model_provider_info::presets::catalog_for(&provider_id)
        .into_iter()
        .map(|entry| ProviderCatalogModel {
            model_id: entry.model_id.to_string(),
            name: entry.name.to_string(),
            context_length: entry.context_window,
            levels: entry
                .levels
                .iter()
                .map(|level| level.as_str().to_string())
                .collect(),
        })
        .collect()
}

/// 出厂内置的供应商：界面首次启动拿它当种子。模型与它们的能力都来自核心的**模型目录**。
///
/// 清单的真源是 `model-provider-info` 的 `presets`，界面不再各自写死一份；这里出去的每条按定义都是
/// 内置的，所以不带 `is_built_in`，也没有密钥。
#[uniffi::export]
pub fn built_in_providers() -> Vec<ProviderInput> {
    jasmine_model_provider_info::presets::built_in()
        .into_iter()
        .map(|provider| ProviderInput {
            id: provider.id,
            name: provider.name,
            base_url: provider.base_url,
            wire_api: provider.wire_api,
            api_key: String::new(),
            models: provider
                .models
                .into_iter()
                .map(|model| ModelInput {
                    id: model.id,
                    model_id: model.model_id,
                    name: model.name,
                    context_length: model.context_length,
                    max_output_length: model.max_output_length,
                    reasoning_effort: model.reasoning_effort,
                })
                .collect(),
        })
        .collect()
}

/// [`AgentError`] 在边界上的呈现方式：只给一句原因，不带类型。
///
/// 跨语言传递错误类型需要两边同步维护一套枚举，而界面真正需要的只是一句能显示的话；
/// 需要区分时再按原因文本判断即可。
#[allow(dead_code)]
fn error_text(error: AgentError) -> String {
    error.detail()
}

#[cfg(test)]
#[path = "ffi_tests.rs"]
mod tests;
