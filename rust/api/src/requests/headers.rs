use http::HeaderMap;
use http::HeaderValue;

pub fn build_session_headers(session_id: Option<String>, thread_id: Option<String>) -> HeaderMap {
    let mut headers = HeaderMap::new();
    if let Some(id) = session_id {
        insert_header(&mut headers, "session-id", &id);
    }
    if let Some(id) = thread_id {
        insert_header(&mut headers, "thread-id", &id);
    }
    headers
}

pub(crate) fn insert_header(headers: &mut HeaderMap, name: &str, value: &str) {
    if let (Ok(header_name), Ok(header_value)) = (
        name.parse::<http::HeaderName>(),
        HeaderValue::from_str(value),
    ) {
        headers.insert(header_name, header_value);
    }
}
