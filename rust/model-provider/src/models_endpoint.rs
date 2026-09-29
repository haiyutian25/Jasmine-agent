//! Fetches the model catalog of an OpenAI-protocol provider.
//!
//! Providers carry their own base URL, so this rides a plain HTTP client call rather than the
//! streaming API client. `GET {base_url}/v1/models` with a Bearer key; the body is read through the
//! shape table, because more than one shape is in the wild and each provider declares its own:
//! `presets::model_list_shapes` — the generic OpenAI one, then one per preset. Nothing here names a
//! provider.
//!
//! A base URL already ending in `/v1` is not doubled.

use crate::provider::ResolvedProvider;
use jasmine_http_client::HttpClient;
use jasmine_http_client::HttpError;
use serde_json::Value;

#[derive(Debug, thiserror::Error)]
pub enum ModelListError {
    #[error("HTTP {0}")]
    Status(u16),
    #[error("malformed model list JSON")]
    Malformed,
    #[error(transparent)]
    Transport(#[from] HttpError),
}

/// Returns the distinct model ids advertised by the provider.
pub async fn fetch_model_ids(
    client: &HttpClient,
    provider: &ResolvedProvider,
) -> Result<Vec<String>, ModelListError> {
    let response = client
        .get(models_url(&provider.info().base_url))
        .header(
            http::header::AUTHORIZATION,
            format!("Bearer {}", provider.api_key()),
        )
        .header(http::header::ACCEPT, "application/json")
        .send()
        .await?;

    let status = response.status();
    if !status.is_success() {
        return Err(ModelListError::Status(status.as_u16()));
    }
    let body = response.text().await?;
    parse_model_ids(&body)
}

fn models_url(base_url: &str) -> String {
    let trimmed = base_url.trim().trim_end_matches('/');
    if trimmed.ends_with("/v1") {
        format!("{trimmed}/models")
    } else {
        format!("{trimmed}/v1/models")
    }
}

fn parse_model_ids(body: &str) -> Result<Vec<String>, ModelListError> {
    let root: Value = serde_json::from_str(body).map_err(|_| ModelListError::Malformed)?;
    let shapes = jasmine_model_provider_info::presets::model_list_shapes();
    let entries = shapes
        .iter()
        .flat_map(|shape| shape.list_keys.iter())
        .find_map(|key| root.get(*key))
        .and_then(Value::as_array)
        .ok_or(ModelListError::Malformed)?;

    let mut ids: Vec<String> = Vec::new();
    for entry in entries {
        let Some(object) = entry.as_object() else {
            continue;
        };
        let id = shapes
            .iter()
            .flat_map(|shape| shape.id_keys.iter())
            .find_map(|key| object.get(*key))
            .and_then(Value::as_str)
            .filter(|id| !id.trim().is_empty());
        if let Some(id) = id
            && !ids.iter().any(|existing| existing == id)
        {
            ids.push(id.to_string());
        }
    }
    Ok(ids)
}

#[cfg(test)]
#[path = "models_endpoint_tests.rs"]
mod tests;
