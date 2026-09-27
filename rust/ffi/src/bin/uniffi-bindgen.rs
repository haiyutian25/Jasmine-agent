//! 生成平台侧绑定的命令行入口。
//!
//! 用法（在 `rust/` 目录）：
//!
//! ```text
//! cargo run -p jasmine-ffi --features bindgen-cli --bin uniffi-bindgen -- \
//!     generate --library target/debug/jasmine_ffi.dll --language kotlin --out-dir <输出目录>
//! ```

fn main() {
    uniffi::uniffi_bindgen_main()
}
