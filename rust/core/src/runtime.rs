//! 一次性边界调用共用的 tokio 运行时。
//!
//! 探测与列模型都是"界面点一下、问一句、拿结果"的边界调用，没有跨调用的连接池要复用。
//! 以前它们各自 `new_current_thread()` 现场建一个运行时再丢掉 —— 每次点击都多一次建/销。
//! 这里建一次的代价是常驻一个 worker 线程，换来的是不必每调一次就起一个。
//!
//! 用 `multi_thread` 而不是 `current_thread`：这两个入口可能被不同线程并发调用
//! （两个 IO 线程各点了一下"测试连接"），而 `current_thread` 的运行时同一时刻只能由一个线程驱动。

use std::sync::OnceLock;
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

static SHARED: OnceLock<Result<Runtime, String>> = OnceLock::new();

/// 进程内共用的运行时；建不起来时回一句能显示的原因（与边界上其他失败同一种形态）。
pub(crate) fn shared() -> Result<&'static Runtime, String> {
    match SHARED.get_or_init(|| {
        Builder::new_multi_thread()
            .worker_threads(1)
            .enable_all()
            .build()
            .map_err(|error| error.to_string())
    }) {
        Ok(runtime) => Ok(runtime),
        // 建失败的结果会被缓存下来：再调一次也是同一个原因，不必重试建一个永远建不起来的东西。
        Err(detail) => Err(detail.clone()),
    }
}
