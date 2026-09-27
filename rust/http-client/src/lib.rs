#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]
mod client;
mod client_builder;
mod error;
mod request;
mod request_builder;
mod request_draft;
mod response;
mod retry_after;
mod transport;

pub use crate::client::HttpClient;
pub use crate::client::HttpError;
pub use crate::client_builder::HttpClientBuilder;
pub use crate::error::StreamError;
pub use crate::error::TransportError;
pub use crate::request::EncodedJsonBody;
pub use crate::request::PreparedRequestBody;
pub use crate::request::Request;
pub use crate::request::RequestBody;
pub use crate::request::Response;
pub use crate::request_builder::RequestBuilder;
pub use crate::response::HttpResponse;
pub use crate::retry_after::RetryAfter;
pub use crate::transport::ByteStream;
pub use crate::transport::HttpTransport;
pub use crate::transport::ReqwestTransport;
pub use crate::transport::StreamResponse;
