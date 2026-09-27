//! HTTP responses retain application authorization while their bodies are consumed.

use bytes::Bytes;
use futures::Stream;
use futures::StreamExt;
use serde::de::DeserializeOwned;
use std::ops::Deref;

use crate::HttpError;

#[derive(Debug)]
pub struct HttpResponse {
    inner: reqwest::Response,
}

impl HttpResponse {
    pub async fn bytes(self) -> Result<Bytes, HttpError> {
        Ok(self.inner.bytes().await?)
    }

    pub async fn text(self) -> Result<String, HttpError> {
        Ok(self.inner.text().await?)
    }

    pub async fn json<T: DeserializeOwned>(self) -> Result<T, HttpError> {
        Ok(self.inner.json().await?)
    }

    pub async fn chunk(&mut self) -> Result<Option<Bytes>, HttpError> {
        Ok(self.inner.chunk().await?)
    }

    pub fn error_for_status(self) -> Result<Self, HttpError> {
        Ok(Self {
            inner: self.inner.error_for_status()?,
        })
    }

    pub fn error_for_status_ref(&self) -> Result<&Self, HttpError> {
        self.inner.error_for_status_ref()?;
        Ok(self)
    }

    pub fn bytes_stream(self) -> impl Stream<Item = Result<Bytes, HttpError>> + Send + Unpin {
        Box::pin(
            self.inner
                .bytes_stream()
                .map(|result| result.map_err(HttpError::from)),
        )
    }
}

impl Deref for HttpResponse {
    type Target = reqwest::Response;

    fn deref(&self) -> &Self::Target {
        &self.inner
    }
}

impl From<reqwest::Response> for HttpResponse {
    fn from(inner: reqwest::Response) -> Self {
        Self { inner }
    }
}

impl<T: Into<reqwest::Body>> From<http::Response<T>> for HttpResponse {
    fn from(response: http::Response<T>) -> Self {
        reqwest::Response::from(response).into()
    }
}
