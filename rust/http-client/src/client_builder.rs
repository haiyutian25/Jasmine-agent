//! HTTP client construction without exposing the underlying HTTP implementation.

use http::HeaderMap;
use std::time::Duration;

use crate::HttpClient;
use crate::client::RequestLogging;

/// Configures an [`HttpClient`] without exposing the underlying HTTP implementation.
#[derive(Clone)]
pub struct HttpClientBuilder {
    default_headers: Option<HeaderMap>,
    follow_redirects: bool,
    connect_timeout: Option<Duration>,
    request_logging: RequestLogging,
}

impl HttpClientBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    /// Applies every configured value unless the request sets that header explicitly.
    pub fn default_headers(mut self, headers: HeaderMap) -> Self {
        self.default_headers = Some(headers);
        self
    }

    pub fn without_redirects(mut self) -> Self {
        self.follow_redirects = false;
        self
    }

    /// Limits only connection establishment, not the request as a whole.
    pub fn connect_timeout(mut self, timeout: Duration) -> Self {
        self.connect_timeout = Some(timeout);
        self
    }

    /// Suppresses request URL and response-header diagnostics.
    pub fn without_request_logging(mut self) -> Self {
        self.request_logging = RequestLogging::Disabled;
        self
    }

    pub fn build(self) -> Result<HttpClient, reqwest::Error> {
        let request_logging = self.request_logging;
        let default_headers = self.default_headers.clone().unwrap_or_default();
        let client = self.base_reqwest_builder().build()?;
        Ok(HttpClient::from_parts(
            client,
            request_logging,
            default_headers,
        ))
    }

    fn base_reqwest_builder(self) -> reqwest::ClientBuilder {
        let mut builder = reqwest::Client::builder();
        if !self.follow_redirects {
            builder = builder.redirect(reqwest::redirect::Policy::none());
        }
        if let Some(connect_timeout) = self.connect_timeout {
            builder = builder.connect_timeout(connect_timeout);
        }
        builder
    }
}

impl Default for HttpClientBuilder {
    fn default() -> Self {
        Self {
            default_headers: None,
            follow_redirects: true,
            connect_timeout: None,
            request_logging: RequestLogging::Enabled,
        }
    }
}
