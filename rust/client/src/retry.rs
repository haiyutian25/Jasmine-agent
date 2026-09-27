use jasmine_http_client::Request;
use jasmine_http_client::RetryAfter;
use jasmine_http_client::TransportError;
use rand::Rng;
use std::future::Future;
use std::time::Duration;

#[derive(Debug, Clone)]
pub struct RetryPolicy {
    pub max_attempts: u64,
    pub base_delay: Duration,
    pub retry_on: RetryOn,
}

#[derive(Debug, Clone)]
pub struct RetryOn {
    pub retry_429: bool,
    pub retry_5xx: bool,
    pub retry_transport: bool,
}

impl RetryOn {
    pub fn should_retry(&self, err: &TransportError, attempt: u64, max_attempts: u64) -> bool {
        if attempt >= max_attempts {
            return false;
        }
        match err {
            TransportError::Http { status, .. } => {
                (self.retry_429 && status.as_u16() == 429)
                    || (self.retry_5xx && status.is_server_error())
            }
            TransportError::Timeout
            | TransportError::Connection(_)
            | TransportError::Network(_) => self.retry_transport,
            TransportError::Build(_)
            | TransportError::RetryLimit
            | TransportError::ResponseTooLarge { .. } => false,
        }
    }
}

pub fn backoff(base: Duration, attempt: u64) -> Duration {
    if attempt == 0 {
        return base;
    }
    let exp = 2u64.saturating_pow(attempt as u32 - 1);
    let millis = base.as_millis() as u64;
    let raw = millis.saturating_mul(exp);
    let jitter: f64 = rand::rng().random_range(0.9..1.1);
    Duration::from_millis((raw as f64 * jitter) as u64)
}

pub async fn run_with_retry<T, F, Fut>(
    policy: RetryPolicy,
    mut make_req: impl FnMut() -> Request,
    op: F,
) -> Result<T, TransportError>
where
    F: Fn(Request, u64) -> Fut,
    Fut: Future<Output = Result<T, TransportError>>,
{
    for attempt in 0..=policy.max_attempts {
        let req = make_req();
        match op(req, attempt).await {
            Ok(resp) => return Ok(resp),
            Err(err)
                if policy
                    .retry_on
                    .should_retry(&err, attempt, policy.max_attempts) =>
            {
                let retry_after = err.retry_after();
                let delay = retry_after
                    .map(RetryAfter::remaining_delay)
                    .unwrap_or_else(|| backoff(policy.base_delay, attempt + 1));
                if let Some(retry_after) = retry_after {
                    tokio::time::sleep_until(retry_after.deadline()).await;
                } else {
                    tokio::time::sleep(delay).await;
                }
            }
            Err(err) => return Err(err),
        }
    }
    Err(TransportError::RetryLimit)
}
