//! Owns the reqwest client, request diagnostics and default headers.

use http::HeaderMap;
use reqwest::IntoUrl;
use reqwest::Method;
use std::sync::Arc;

use crate::RequestBuilder;

#[derive(Debug, thiserror::Error)]
pub enum HttpError {
    #[error(transparent)]
    Request(#[from] reqwest::Error),
    #[error("{0}")]
    Build(String),
}

impl HttpError {
    pub(crate) fn without_url(self) -> Self {
        match self {
            Self::Request(error) => Self::Request(error.without_url()),
            other => other,
        }
    }

    pub(crate) fn is_connect(&self) -> bool {
        matches!(self, Self::Request(error) if error.is_connect())
    }

    pub(crate) fn is_timeout(&self) -> bool {
        matches!(self, Self::Request(error) if error.is_timeout())
    }
}

/// A resolved transport used by direct clients.
#[derive(Clone)]
pub(crate) struct TransportClient {
    pub(crate) inner: reqwest::Client,
    pub(crate) request_logging: RequestLogging,
    default_headers: Arc<HeaderMap>,
}

impl std::fmt::Debug for TransportClient {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("TransportClient")
            .field("request_logging", &self.request_logging)
            .finish_non_exhaustive()
    }
}

/// Reusable HTTP client with shared request diagnostics.
#[derive(Clone, Debug)]
pub struct HttpClient {
    pub(crate) transport: TransportClient,
}

impl HttpClient {
    pub fn new(inner: reqwest::Client) -> Self {
        Self::from_parts(inner, RequestLogging::Enabled, HeaderMap::new())
    }

    /// Suppresses diagnostics for endpoints whose URLs or headers may contain credentials.
    pub fn new_without_request_logging(inner: reqwest::Client) -> Self {
        Self::from_parts(inner, RequestLogging::Disabled, HeaderMap::new())
    }

    /// Suppresses URL and response-header diagnostics.
    pub fn without_request_logging(mut self) -> Self {
        self.transport.request_logging = RequestLogging::Disabled;
        self
    }

    pub(crate) fn from_parts(
        inner: reqwest::Client,
        request_logging: RequestLogging,
        default_headers: HeaderMap,
    ) -> Self {
        Self {
            transport: TransportClient::new(inner, request_logging, default_headers),
        }
    }

    pub(crate) fn transport(&self) -> &TransportClient {
        &self.transport
    }

    pub fn get<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.request(Method::GET, url)
    }

    pub fn head<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.request(Method::HEAD, url)
    }

    pub fn post<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.request(Method::POST, url)
    }

    pub fn delete<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.request(Method::DELETE, url)
    }

    pub fn request<U: IntoUrl>(&self, method: Method, url: U) -> RequestBuilder {
        RequestBuilder::direct(&self.transport, method, url)
    }

    pub(crate) fn request_logging_enabled(&self) -> bool {
        self.transport.request_logging == RequestLogging::Enabled
    }
}

pub(crate) fn apply_default_headers(headers: &mut HeaderMap, defaults: &HeaderMap) {
    for name in defaults.keys() {
        if !headers.contains_key(name) {
            for value in defaults.get_all(name) {
                headers.append(name.clone(), value.clone());
            }
        }
    }
}

impl TransportClient {
    pub(crate) fn new(
        inner: reqwest::Client,
        request_logging: RequestLogging,
        default_headers: HeaderMap,
    ) -> Self {
        Self {
            inner,
            request_logging,
            default_headers: Arc::new(default_headers),
        }
    }

    pub(crate) async fn execute(
        &self,
        request: reqwest::Request,
    ) -> Result<reqwest::Response, reqwest::Error> {
        let method = request.method().clone();
        let url = request.url().to_string();

        match self.execute_without_request_logging(request).await {
            Ok(response) => {
                self.log_response(&method, &url, &response);
                Ok(response)
            }
            Err(error) => {
                self.log_error(&method, &url, &error);
                Err(error)
            }
        }
    }

    pub(crate) async fn execute_without_request_logging(
        &self,
        mut request: reqwest::Request,
    ) -> Result<reqwest::Response, reqwest::Error> {
        apply_default_headers(request.headers_mut(), &self.default_headers);
        self.inner.execute(request).await
    }

    pub(crate) fn log_response(&self, method: &Method, url: &str, response: &reqwest::Response) {
        if self.request_logging == RequestLogging::Enabled {
            tracing::debug!(
                method = %method,
                url = %url,
                status = %response.status(),
                headers = ?response.headers(),
                version = ?response.version(),
                "Request completed"
            );
        }
    }

    pub(crate) fn log_error(&self, method: &Method, url: &str, error: &reqwest::Error) {
        if self.request_logging == RequestLogging::Enabled {
            tracing::debug!(
                method = %method,
                url = %url,
                status = error.status().map(|status| status.as_u16()),
                error = %error,
                "Request failed"
            );
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum RequestLogging {
    #[default]
    Enabled,
    Disabled,
}
