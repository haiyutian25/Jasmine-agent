#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]
mod provider;
mod retry;
mod telemetry;

pub use crate::provider::Provider;
pub use crate::provider::RetryConfig;
pub use crate::retry::RetryOn;
pub use crate::retry::RetryPolicy;
pub use crate::retry::backoff;
pub use crate::retry::run_with_retry;
pub use crate::telemetry::RequestTelemetry;
pub use jasmine_http_client::HttpClient as JasmineHttpClient;
pub use jasmine_http_client::RequestBuilder as JasmineRequestBuilder;
pub use jasmine_http_client::*;
