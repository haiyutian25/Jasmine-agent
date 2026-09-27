#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]
//! Shared tool definitions and Responses API tool primitives that can live
//! outside `jasmine-core`.

mod current_time;
mod function_call_error;
mod image_detail;
mod json_schema;
mod list_past_conversations;
mod output_schema;
mod response_history;
mod responses_api;
mod tool;
mod tool_definition;
mod tool_payload;
mod tool_search;
mod tool_spec;

pub use current_time::Clock;
pub use current_time::CurrentTimeTool;
pub use function_call_error::FunctionCallError;
pub use image_detail::normalize_output_image_detail;
pub use image_detail::sanitize_original_image_detail;
pub use json_schema::AdditionalProperties;
pub use json_schema::JsonSchema;
pub use json_schema::JsonSchemaPrimitiveType;
pub use json_schema::JsonSchemaType;
pub use json_schema::parse_tool_input_schema;
pub use json_schema::parse_tool_input_schema_without_compaction;
pub use list_past_conversations::ConversationSummary;
pub use list_past_conversations::ConversationTitles;
pub use list_past_conversations::ListPastConversationsTool;
pub use output_schema::ToolOutputSchema;
pub use response_history::retain_tail_from_last_n_user_messages;
pub use response_history::truncate_assistant_output_text_to_token_budget;
pub use responses_api::FreeformTool;
pub use responses_api::FreeformToolFormat;
pub use responses_api::LoadableToolSpec;
pub use responses_api::ResponsesApiNamespace;
pub use responses_api::ResponsesApiNamespaceTool;
pub use responses_api::ResponsesApiTool;
pub use responses_api::coalesce_loadable_tool_specs;
pub use responses_api::default_namespace_description;
pub use responses_api::tool_definition_to_responses_api_tool;
pub use tool::Tool;
pub use tool::ToolError;
pub use tool::ToolFuture;
pub use tool_definition::ToolDefinition;
pub use tool_payload::ToolPayload;
pub use tool_search::ToolSearchEntry;
pub use tool_search::ToolSearchInfo;
pub use tool_spec::ToolSpec;
pub use tool_spec::create_tools_json_for_responses_api;
pub use tool_spec::create_tools_json_for_responses_lite;
pub use tool_spec::create_tools_raw_json_for_responses_api;
