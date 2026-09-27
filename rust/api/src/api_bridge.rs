use crate::error::ApiError;
use http::HeaderMap;
use http::StatusCode;
use serde::Deserialize;

const REQUEST_ID_HEADER: &str = "x-request-id";
const OAI_REQUEST_ID_HEADER: &str = "x-oai-request-id";
const CLOUDFLARE_BLOCKED_MESSAGE: &str =
    "Access blocked by Cloudflare. This usually happens when connecting from a restricted region";

#[derive(Debug, Deserialize)]
struct ErrorEnvelope {
    error: ErrorBody,
}

#[derive(Debug, Deserialize)]
struct ErrorBody {
    code: Option<String>,
    message: Option<String>,
    #[serde(rename = "type")]
    error_type: Option<String>,
}

pub fn classify_error_response(status: StatusCode, body: &str) -> ApiError {
    let envelope = serde_json::from_str::<ErrorEnvelope>(body).ok();
    let code = envelope
        .as_ref()
        .and_then(|envelope| envelope.error.code.as_deref());
    let error_type = envelope
        .as_ref()
        .and_then(|envelope| envelope.error.error_type.as_deref());
    let message = envelope
        .as_ref()
        .and_then(|envelope| envelope.error.message.clone())
        .unwrap_or_else(|| body.to_string());

    if status == StatusCode::SERVICE_UNAVAILABLE {
        match code {
            Some("server_is_overloaded") => {
                return ApiError::ServerOverloaded { retry_after: None };
            }
            Some("slow_down") => {
                return ApiError::RateLimitExceeded {
                    message,
                    retry_after: None,
                };
            }
            _ => {}
        }
    }

    if status == StatusCode::TOO_MANY_REQUESTS {
        if error_type == Some("insufficient_quota") || is_quota_exhausted_code(code) {
            return ApiError::QuotaExceeded;
        }
        return ApiError::RateLimitExceeded {
            message,
            retry_after: None,
        };
    }

    if status == StatusCode::BAD_REQUEST {
        if code == Some("invalid_prompt") {
            return ApiError::InvalidPrompt { message };
        }
        return ApiError::InvalidRequest { message };
    }

    if status == StatusCode::INTERNAL_SERVER_ERROR {
        return ApiError::Retryable {
            message,
            retry_after: None,
        };
    }

    if status == StatusCode::FORBIDDEN && body.contains("Cloudflare") && body.contains("blocked") {
        return ApiError::Api {
            status,
            message: format!("{CLOUDFLARE_BLOCKED_MESSAGE} (status {status})"),
        };
    }

    ApiError::Api { status, message }
}

fn is_quota_exhausted_code(code: Option<&str>) -> bool {
    matches!(
        code,
        Some(
            "insufficient_quota"
                | "credit_balance_exhausted"
                | "organization_spend_limit_exceeded"
                | "project_spend_limit_exceeded"
                | "organization_usage_limit_exceeded"
        )
    )
}

pub fn extract_request_id(headers: Option<&HeaderMap>) -> Option<String> {
    extract_header(headers, REQUEST_ID_HEADER)
        .or_else(|| extract_header(headers, OAI_REQUEST_ID_HEADER))
}

fn extract_header(headers: Option<&HeaderMap>, name: &str) -> Option<String> {
    headers.and_then(|map| {
        map.get(name)
            .and_then(|value| value.to_str().ok())
            .map(str::to_string)
    })
}
