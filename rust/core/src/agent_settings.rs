//! Agent 的行为设置：**界面只传值，规则在核心**。
//!
//! 眼下只有一件事 —— 模型回复语言：取值（[`output_language`]）与那条系统指令规则
//! （[`AgentSettings::language_rule`]）。规则形态照 qwen-code 的 `output-language.md`
//! （`packages/cli/src/i18n/languageUtils.ts`）抄：Rule → Exception → **不改技术产物** → 工具输出。
//!
//! 往后再加别的 Agent 控制项，也放这里：界面那边只是选择器 + 一个值。

/// 模型回复语言的取值（与界面那张表一一对应）。
pub mod output_language {
    /// 跟随输入（默认）：用户用什么语言问，就用什么语言答。
    pub const FOLLOW_INPUT: &str = "auto";
    /// 跟随应用显示的语言。
    pub const FOLLOW_APP: &str = "app";
    pub const ENGLISH: &str = "en";
    pub const SIMPLIFIED_CHINESE: &str = "zh-Hans";
    pub const TRADITIONAL_CHINESE: &str = "zh-Hant";
}

/// 界面传进来的 Agent 设置。
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct AgentSettings {
    /// 模型回复语言（取值见 [`output_language`]）；空、或者认不出的取值，都按"跟随输入"处理。
    pub output_language: String,
    /// 界面当前的语言（BCP-47，如 `zh-CN` / `zh-TW` / `en-US`）；"跟随应用语言"时用它定语言。
    pub app_language: String,
}

impl AgentSettings {
    /// 这次要用哪种语言回答；`None` = 跟随输入（不点名语言，交给规则里"跟用户输入同语言"那条）。
    pub fn language_name(&self) -> Option<&'static str> {
        match self.output_language.as_str() {
            output_language::FOLLOW_APP => Some(language_name_for_tag(&self.app_language)),
            output_language::ENGLISH => Some("English"),
            output_language::SIMPLIFIED_CHINESE => Some("Simplified Chinese"),
            output_language::TRADITIONAL_CHINESE => Some("Traditional Chinese"),
            _ => None,
        }
    }

    /// 拼在人格后面的**输出语言规则**。
    ///
    /// 后两段与语言无关、但一直都要有：它们防的是"模型顺手把代码、命令或 JSON 键翻译掉"。
    pub fn language_rule(&self) -> String {
        let language_name = self.language_name();
        let rule = match language_name {
            None => "## Rule\n\
                     Respond in the same language as the user's input.\n\
                     \n\
                     ## Mixed-language input\n\
                     If the user mixes languages, use the language that best matches their main request."
                .to_string(),
            Some(name) => format!(
                "## Rule\n\
                 You MUST always respond in **{name}** regardless of the user's input language. \
                 This is a mandatory requirement, not a preference."
            ),
        };
        let explanation_language = language_name.unwrap_or("the user's language");
        let heading = language_name.unwrap_or("auto");

        [
            format!("# Output language preference: {heading}"),
            rule,
            "## Exception\n\
             If the user **explicitly** asks for another language, switch to the requested one for \
             the rest of the conversation."
                .to_string(),
            "## Keep technical artifacts unchanged\n\
             Do **not** translate or rewrite: code blocks, CLI commands, file paths, stack traces, \
             logs, JSON keys, identifiers, or the user's exact quoted text."
                .to_string(),
            format!(
                "## Tool / system outputs\n\
                 Raw tool/system outputs may use fixed-format English. Keep them verbatim and, when \
                 an explanation helps, add it in {explanation_language}."
            ),
        ]
        .join("\n\n")
    }

    /// 交给模型的**系统指令**：平台给的人格 + 这里的语言规则。
    ///
    /// 人格由平台给（它是产品文案），语言那部分由核心拼 —— 界面不参与。
    pub fn system_instruction(&self, persona: &str) -> String {
        let persona = persona.trim();
        if persona.is_empty() {
            return self.language_rule();
        }
        format!("{}\n\n{}", persona, self.language_rule())
    }
}

/// `zh-TW` / `zh-Hant` → 繁体；其余 `zh*` → 简体；都不是 → 英文。
fn language_name_for_tag(tag: &str) -> &'static str {
    let normalized = tag.replace('_', "-").to_lowercase();
    let traditional = normalized.contains("hant")
        || normalized.contains("-tw")
        || normalized.contains("-hk")
        || normalized.contains("-mo");
    if !normalized.starts_with("zh") {
        return "English";
    }
    if traditional {
        "Traditional Chinese"
    } else {
        "Simplified Chinese"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn settings(output_language: &str, app_language: &str) -> AgentSettings {
        AgentSettings {
            output_language: output_language.to_string(),
            app_language: app_language.to_string(),
        }
    }

    /// 固定某语言：点名 + 强制语气；跟随输入：不点名。
    #[test]
    fn an_explicit_language_is_named_and_enforced() {
        let rule = settings(output_language::SIMPLIFIED_CHINESE, "en-US").language_rule();
        assert!(rule.contains("You MUST always respond in **Simplified Chinese**"));
        assert!(rule.contains("# Output language preference: Simplified Chinese"));

        let auto = settings(output_language::FOLLOW_INPUT, "zh-CN").language_rule();
        assert!(auto.contains("Respond in the same language as the user's input."));
        assert!(!auto.contains("MUST always respond in"));
        assert!(auto.contains("# Output language preference: auto"));
    }

    /// "跟随应用语言"按界面语言定：繁体界面用繁体，简体用简体，其余英文。
    #[test]
    fn following_the_app_language_reads_the_app_tag() {
        assert_eq!(
            settings(output_language::FOLLOW_APP, "zh-TW").language_name(),
            Some("Traditional Chinese")
        );
        assert_eq!(
            settings(output_language::FOLLOW_APP, "zh-Hant-HK").language_name(),
            Some("Traditional Chinese")
        );
        assert_eq!(
            settings(output_language::FOLLOW_APP, "zh-CN").language_name(),
            Some("Simplified Chinese")
        );
        assert_eq!(
            settings(output_language::FOLLOW_APP, "en-US").language_name(),
            Some("English")
        );
    }

    /// 与语言无关的两段一直都在（防模型翻译代码/命令），且系统指令 = 人格 + 规则。
    #[test]
    fn the_instruction_always_keeps_the_artifacts_rule() {
        for setting in [
            output_language::FOLLOW_INPUT,
            output_language::FOLLOW_APP,
            output_language::ENGLISH,
            output_language::TRADITIONAL_CHINESE,
            "who-knows",
        ] {
            let instruction = settings(setting, "zh-TW").system_instruction("You are X.");
            assert!(instruction.starts_with("You are X."));
            assert!(instruction.contains("## Keep technical artifacts unchanged"));
            assert!(instruction.contains("## Exception"));
            assert!(instruction.contains("## Tool / system outputs"));
        }

        // 认不出的取值按"跟随输入"处理，不真编一种语言出来。
        assert_eq!(settings("who-knows", "zh-TW").language_name(), None);
    }
}
