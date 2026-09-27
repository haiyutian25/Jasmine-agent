use std::fmt::Display;

#[derive(Debug)]
pub struct RateLimitError {
    pub message: String,
}

impl Display for RateLimitError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}
