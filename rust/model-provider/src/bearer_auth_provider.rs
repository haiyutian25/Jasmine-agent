use http::HeaderMap;
use http::HeaderValue;
use jasmine_api::AuthProvider;

/// Bearer-token auth provider for OpenAI-compatible model-provider requests.
#[derive(Clone, Default)]
pub struct BearerAuthProvider {
    pub token: Option<String>,
}

impl BearerAuthProvider {
    pub fn new(token: String) -> Self {
        Self { token: Some(token) }
    }
}

impl AuthProvider for BearerAuthProvider {
    fn add_auth_headers(&self, headers: &mut HeaderMap) {
        if let Some(token) = self.token.as_ref()
            && let Ok(header) = HeaderValue::from_str(&format!("Bearer {token}"))
        {
            let _ = headers.insert(http::header::AUTHORIZATION, header);
        }
    }
}
