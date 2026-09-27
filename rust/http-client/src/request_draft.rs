//! Request drafts awaiting construction through reqwest.
//!
//! Replay header operations in order so reqwest owns URL authentication and header precedence.
//! Header operations are the sole header state until build; the draft's request holds no headers.

use http::HeaderMap;
use http::HeaderName;
use http::HeaderValue;
use http::Method;
use reqwest::IntoUrl;

use crate::client::TransportClient;

/// Request inputs awaiting construction through reqwest with a selected HTTP client.
pub(crate) struct RequestDraft {
    pub(crate) request: reqwest::Request,
    pub(crate) headers: Vec<HeaderUpdate>,
}

pub(crate) enum HeaderUpdate {
    Replace(HeaderMap),
    Append(HeaderName, HeaderValue),
}

impl From<reqwest::Request> for RequestDraft {
    fn from(mut request: reqwest::Request) -> Self {
        let headers = vec![HeaderUpdate::Replace(std::mem::take(request.headers_mut()))];
        Self { request, headers }
    }
}

impl RequestDraft {
    pub(crate) fn new<U: IntoUrl>(method: Method, url: U) -> Result<Self, reqwest::Error> {
        url.into_url()
            .map(|url| Self::from(reqwest::Request::new(method, url)))
    }

    pub(crate) fn extend_headers(&mut self, headers: HeaderMap) {
        self.headers.push(HeaderUpdate::Replace(headers));
    }

    pub(crate) fn build(
        self,
        client: &TransportClient,
    ) -> Result<reqwest::Request, reqwest::Error> {
        let mut request = self.request;
        let mut builder = client
            .inner
            .request(request.method().clone(), request.url().clone());
        for update in self.headers {
            builder = match update {
                HeaderUpdate::Replace(headers) => builder.headers(headers),
                HeaderUpdate::Append(name, value) => builder.header(name, value),
            };
        }
        let mut built = builder.build()?;
        *built.body_mut() = request.body_mut().take();
        *built.timeout_mut() = request.timeout().copied();
        *built.version_mut() = request.version();
        Ok(built)
    }
}
