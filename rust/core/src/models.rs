//! 列出一家端点提供的模型。
//!
//! 与探测同一种形态：界面那边一次调用的边界，与它共用一个模块级运行时（见 `crate::runtime`）；
//! 失败只回一句能显示的原因。

use jasmine_http_client::HttpClientBuilder;
use jasmine_model_provider::ResolvedProvider;
use jasmine_model_provider::fetch_model_ids;

/// `GET {base_url}/v1/models`，返回去重后的模型 id。
pub fn list_models(provider: &ResolvedProvider) -> Result<Vec<String>, String> {
    crate::runtime::shared()?.block_on(async {
        let http = HttpClientBuilder::new()
            .without_request_logging()
            .build()
            .map_err(|error| error.to_string())?;
        fetch_model_ids(&http, provider)
            .await
            .map_err(|error| error.to_string())
    })
}
