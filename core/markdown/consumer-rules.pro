# ---------------------------------------------------------------------------
# incremark JNI 回调契约 —— 不保留这些名字，release 包必然闪退
#
# native 侧（src/main/cpp/incremark/incremark_jni.c）在 JNI_OnLoad 里就调用
# ensure_cache()，用 FindClass / GetStaticMethodID 按**原始类名与签名**查找：
#
#   com/lhzkml/jasmine/core/markdown/IncremarkBridge              ← 回调入口
#   com/lhzkml/jasmine/core/markdown/model/MarkdownInline         ┐
#   com/lhzkml/jasmine/core/markdown/model/MarkdownBlock          │ 既被 FindClass 查找，
#   com/lhzkml/jasmine/core/markdown/model/MarkdownTableRow       │ 名字也硬编码在
#   com/lhzkml/jasmine/core/markdown/model/MarkdownTableCell      │ GetStaticMethodID
#   com/lhzkml/jasmine/core/markdown/model/MarkdownPrefixContext  ┘ 的签名串里
#
# R8 看不到这类引用。实测（app-release 的 mapping.txt / usage.txt）后果是：
#   · IncremarkBridge、MarkdownTableRow、MarkdownTableCell、
#     MarkdownPrefixContext —— 整个类被当死代码删除
#   · MarkdownBlock -> ua2、MarkdownInline -> za2 —— 被改名，签名串失配
#
# 任一处失配 → ensure_cache 返回 0 → JNI_OnLoad 带着挂起的
# ClassNotFoundException 返回 → System.loadLibrary 抛出它 →
# NativeBridge.<clinit> 抛 ExceptionInInitializerError → 首个流式 chunk 到达时闪退。
#
# 注意 MarkdownUpdate 不在 FindClass 列表里：它是 NativeBridge 的 native 方法
# 返回类型，R8 已自动保留其名字，无需在此列出（列出也无害）。
# ---------------------------------------------------------------------------

# 回调入口：整个类连同 6 个 @JvmStatic 工厂方法
# （makeUpdate / makeBlock / makeInline / makePrefix / makeTableRow / makeTableCell）
# 都要保名保留。
-keep class com.lhzkml.jasmine.core.markdown.IncremarkBridge { *; }

# 回调参数/返回值模型：类名必须稳定（签名串里用到），成员也保留。
-keep class com.lhzkml.jasmine.core.markdown.model.** { *; }
