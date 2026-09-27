use crate::FreeformTool;
use crate::JsonSchema;
use crate::LoadableToolSpec;
use crate::ResponsesApiNamespace;
use crate::ResponsesApiNamespaceTool;
use crate::ResponsesApiTool;
use crate::default_namespace_description;
use jasmine_protocol::DEFAULT_FUNCTION_NAMESPACE;
use serde::Serialize;
use serde_json::Value;
use serde_json::value::RawValue;
use std::sync::Arc;

/// When serialized as JSON, this produces a valid "Tool" in the OpenAI
/// Responses API.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(tag = "type")]
pub enum ToolSpec {
    #[serde(rename = "function")]
    Function(ResponsesApiTool),
    #[serde(rename = "namespace")]
    Namespace(ResponsesApiNamespace),
    #[serde(rename = "tool_search")]
    ToolSearch {
        execution: String,
        description: String,
        parameters: JsonSchema,
    },
    #[serde(rename = "custom")]
    Freeform(FreeformTool),
}

impl ToolSpec {
    pub fn name(&self) -> &str {
        match self {
            ToolSpec::Function(tool) => tool.name.as_str(),
            ToolSpec::Namespace(namespace) => namespace.name.as_str(),
            ToolSpec::ToolSearch { .. } => "tool_search",
            ToolSpec::Freeform(tool) => tool.name.as_str(),
        }
    }
}

impl From<LoadableToolSpec> for ToolSpec {
    fn from(value: LoadableToolSpec) -> Self {
        match value {
            LoadableToolSpec::Function(tool) => ToolSpec::Function(tool),
            LoadableToolSpec::Namespace(namespace) => ToolSpec::Namespace(namespace),
        }
    }
}

/// Returns JSON values that are compatible with Function Calling in the
/// Responses API:
/// https://platform.openai.com/docs/guides/function-calling?api-mode=responses
pub fn create_tools_json_for_responses_api(
    tools: &[ToolSpec],
) -> Result<Vec<Value>, serde_json::Error> {
    let mut tools_json = Vec::new();

    for tool in tools {
        let json = serde_json::to_value(tool)?;
        tools_json.push(json);
    }

    Ok(tools_json)
}

pub fn create_tools_json_for_responses_lite(
    tools: &[ToolSpec],
) -> Result<Vec<Value>, serde_json::Error> {
    let mut functions = ResponsesApiNamespace {
        name: DEFAULT_FUNCTION_NAMESPACE.to_string(),
        description: default_namespace_description(DEFAULT_FUNCTION_NAMESPACE),
        tools: Vec::new(),
    };
    let mut functions_index = None;
    let mut tools_json = Vec::new();

    for tool in tools {
        match tool {
            ToolSpec::Function(tool) => {
                functions
                    .tools
                    .push(ResponsesApiNamespaceTool::Function(tool.clone()));
            }
            ToolSpec::Freeform(tool) => {
                functions
                    .tools
                    .push(ResponsesApiNamespaceTool::Custom(tool.clone()));
            }
            ToolSpec::Namespace(namespace) if namespace.name == DEFAULT_FUNCTION_NAMESPACE => {
                if !namespace.description.trim().is_empty() {
                    functions.description = namespace.description.clone();
                }
                functions.tools.extend(namespace.tools.clone());
            }
            tool => {
                tools_json.push(serde_json::to_value(tool)?);
                continue;
            }
        }
        functions_index.get_or_insert(tools_json.len());
    }

    if let Some(functions_index) = functions_index
        && !functions.tools.is_empty()
    {
        tools_json.insert(
            functions_index,
            serde_json::to_value(ToolSpec::Namespace(functions))?,
        );
    }

    Ok(tools_json)
}

/// Returns raw JSON that can be embedded directly in a Responses API request.
pub fn create_tools_raw_json_for_responses_api(
    tools: &[ToolSpec],
) -> Result<Arc<RawValue>, serde_json::Error> {
    serde_json::value::to_raw_value(tools).map(Arc::from)
}

#[cfg(test)]
#[path = "tool_spec_tests.rs"]
mod tests;
