//! Shared HTTP transport with optional response limits.
//!
//! Each request's limit applies to observed body bytes, including unsuccessful responses;
//! declared content lengths are only an additional early-rejection check.

use crate::HttpResponse;
use crate::client::HttpClient;
use crate::error::TransportError;
use crate::request::Request;
use crate::request::RequestBody;
use crate::request::Response;
use crate::request_draft::RequestDraft;
use crate::retry_after::RetryAfter;
use bytes::Bytes;
use bytes::BytesMut;
use futures::StreamExt;
use futures::stream::BoxStream;
use http::HeaderMap;
use http::Method;
use http::StatusCode;
use tracing::Level;
use tracing::enabled;
use tracing::trace;

pub type ByteStream = BoxStream<'static, Result<Bytes, TransportError>>;

pub struct StreamResponse {
    pub status: StatusCode,
    pub headers: HeaderMap,
    pub bytes: ByteStream,
}

pub trait HttpTransport: Send + Sync {
    fn execute(
        &self,
        req: Request,
    ) -> impl std::future::Future<Output = Result<Response, TransportError>> + Send;
    fn stream(
        &self,
        req: Request,
    ) -> impl std::future::Future<Output = Result<StreamResponse, TransportError>> + Send;
}

#[derive(Clone, Debug)]
pub struct ReqwestTransport {
    client: HttpClient,
}

impl ReqwestTransport {
    pub fn new(client: reqwest::Client) -> Self {
        Self {
            client: HttpClient::new(client),
        }
    }

    pub fn from_http_client(client: HttpClient) -> Self {
        Self { client }
    }

    async fn send(&self, req: Request) -> Result<HttpResponse, TransportError> {
        let prepared = req.prepare_body_for_send().map_err(TransportError::Build)?;

        let Request {
            method,
            url,
            headers: _,
            body: _,
            timeout,
            response_body_limit_bytes: _,
        } = req;

        let method = Method::from_bytes(method.as_str().as_bytes()).unwrap_or(Method::GET);
        let mut request =
            RequestDraft::new(method, url).map_err(|error| Self::map_error(error.into()))?;
        request.extend_headers(prepared.headers);
        *request.request.timeout_mut() = timeout;
        *request.request.body_mut() = prepared.body.map(Into::into);

        let client = self.client.transport();
        let request = request
            .build(client)
            .map_err(|error| Self::map_error(error.into()))?;
        client
            .execute(request)
            .await
            .map(HttpResponse::from)
            .map_err(|error| Self::map_error(error.into()))
    }

    fn map_error(err: crate::HttpError) -> TransportError {
        let err = err.without_url();
        if err.is_connect() {
            TransportError::Connection(err)
        } else if err.is_timeout() {
            TransportError::Timeout
        } else {
            TransportError::Network(err.to_string())
        }
    }

    fn trace_request(&self, req: &Request) {
        if self.client.request_logging_enabled() && enabled!(Level::TRACE) {
            trace!(
                "{} to {}: {}",
                req.method,
                req.url,
                request_body_for_trace(req)
            );
        }
    }
}

fn request_body_for_trace(req: &Request) -> String {
    match req.body.as_ref() {
        Some(RequestBody::Json(body)) => body.to_string(),
        Some(RequestBody::EncodedJson(body)) => {
            String::from_utf8_lossy(body.as_bytes()).into_owned()
        }
        Some(RequestBody::Raw(body)) => format!("<raw body: {} bytes>", body.len()),
        None => String::new(),
    }
}

impl HttpTransport for ReqwestTransport {
    async fn execute(&self, req: Request) -> Result<Response, TransportError> {
        self.trace_request(&req);

        let url = req.url.clone();
        let response_body_limit_bytes = req.response_body_limit_bytes;
        let resp = self.send(req).await?;
        let status = resp.status();
        let headers = resp.headers().clone();
        let retry_after = RetryAfter::from_headers(&headers);
        let bytes = match response_body_limit_bytes {
            Some(max_bytes) => bounded_response_bytes(resp, max_bytes).await,
            None => resp.bytes().await.map_err(Self::map_error),
        };
        if !status.is_success() {
            let body = match bytes {
                Ok(bytes) => String::from_utf8(bytes.to_vec()).ok(),
                Err(error @ TransportError::ResponseTooLarge { .. }) => return Err(error),
                // Keep bounded diagnostic-body failures from hiding HTTP auth/retry status.
                Err(_) if response_body_limit_bytes.is_some() => None,
                Err(error) => return Err(error),
            };
            return Err(TransportError::Http {
                status,
                url: Some(url),
                headers: Some(headers),
                body,
                retry_after,
            });
        }
        Ok(Response {
            status,
            headers,
            body: bytes?,
        })
    }

    async fn stream(&self, req: Request) -> Result<StreamResponse, TransportError> {
        self.trace_request(&req);

        let url = req.url.clone();
        let response_body_limit_bytes = req.response_body_limit_bytes;
        let resp = self.send(req).await?;
        let status = resp.status();
        let headers = resp.headers().clone();
        if !status.is_success() {
            let retry_after = RetryAfter::from_headers(&headers);
            let body = match response_body_limit_bytes {
                Some(max_bytes) => match bounded_response_bytes(resp, max_bytes).await {
                    Ok(bytes) => {
                        // Reuse the unbounded path's charset, BOM and replacement decoding,
                        // but only after the body has passed the byte limit.
                        let mut buffered = http::Response::new(bytes);
                        *buffered.headers_mut() = headers.clone();
                        reqwest::Response::from(buffered).text().await.ok()
                    }
                    Err(error @ TransportError::ResponseTooLarge { .. }) => return Err(error),
                    // A failed diagnostic body must not hide HTTP auth or retry semantics.
                    Err(_) => None,
                },
                None => resp.text().await.ok(),
            };
            return Err(TransportError::Http {
                status,
                url: Some(url),
                headers: Some(headers),
                body,
                retry_after,
            });
        }
        let bytes = match response_body_limit_bytes {
            Some(max_bytes) => bounded_response_stream(resp, max_bytes)?,
            None => Box::pin(
                resp.bytes_stream()
                    .map(|result| result.map_err(Self::map_error)),
            ),
        };
        Ok(StreamResponse {
            status,
            headers,
            bytes,
        })
    }
}

fn bounded_response_stream(
    response: HttpResponse,
    max_bytes: usize,
) -> Result<ByteStream, TransportError> {
    if response
        .content_length()
        .is_some_and(|length| length > max_bytes as u64)
    {
        return Err(TransportError::ResponseTooLarge { max_bytes });
    }

    let stream = futures::stream::try_unfold(
        (response, max_bytes),
        move |(mut response, remaining)| async move {
            match response
                .chunk()
                .await
                .map_err(|error| ReqwestTransport::map_error(error.without_url()))?
            {
                Some(chunk) if chunk.len() <= remaining => {
                    let remaining = remaining - chunk.len();
                    Ok(Some((chunk, (response, remaining))))
                }
                Some(_) => Err(TransportError::ResponseTooLarge { max_bytes }),
                None => Ok(None),
            }
        },
    );
    Ok(Box::pin(stream))
}

async fn bounded_response_bytes(
    response: HttpResponse,
    max_bytes: usize,
) -> Result<Bytes, TransportError> {
    let mut stream = bounded_response_stream(response, max_bytes)?;
    let mut body = BytesMut::new();
    while let Some(chunk) = stream.next().await {
        body.extend_from_slice(&chunk?);
    }
    Ok(body.freeze())
}
