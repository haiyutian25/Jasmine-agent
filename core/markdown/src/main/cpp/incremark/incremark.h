/*
 * incremark.h —— 增量 Markdown 引擎的 C 层（自研）
 * ============================================================================
 * 这个头文件的**导出契约**逐条对齐腾讯 ima `libincremark_jni.so` 的
 * `incremark_*` 符号（见逆向包 `native_incremark_api.h`，该文件是根据反汇编
 * 反推的「推测性声明」）。
 *
 * 架构对照：
 *
 *   ima                                       本实现
 *   ─────────────────────────────────────     ─────────────────────────────────
 *   cmark-gfm（静态内联，v0.29.0.gfm.13）      third_party/cmark-gfm（原样 vendor）
 *   + 5 个上游扩展                             + 同上
 *   + highlight/underline/fomula 三个自研扩展   + 同（见 extensions/）
 *   + incremark 增量层（本文件）                + 同（本文件自研实现）
 *
 * 增量策略（与 ima 一致）：
 *   1. 累积原文到内部 buffer；
 *   2. `incremark_find_stable_boundary` 求出「已定型的顶层块」的结束偏移；
 *   3. 只对边界之后的尾部重新跑 cmark-gfm 解析 —— 前缀不重解析；
 *   4. `incremark_flatten_nodes` 把 AST 拍平成 Block/Inline 树；
 *   5. 组装 Update { index, advanced, newlyCompletedCount, blocks }，
 *      由 JNI 层构造成 Java 对象。
 *
 * `apply(Update, List<Block>)` 的「截断 + 追加」语义在 Java 侧
 * （`IncrementalMarkdownParser.apply`），与 ima 相同 —— index 之前的块
 * 完全复用。
 * ============================================================================
 */
#ifndef INCREMARK_H
#define INCREMARK_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * 导出可见性。
 *
 * 与 ima 保持一致：那边的 libincremark_jni.so 把 38 个 incremark_* 全部导出
 * （见 verification/01_dynsym_exports.txt），只有 9 个 Java_* 是 JNI 入口。
 * 编译时开了 -fvisibility=hidden，所以这里显式标注默认可见。
 */
#ifndef INCREMARK_API
#if defined(_WIN32)
#define INCREMARK_API
#else
#define INCREMARK_API __attribute__((visibility("default")))
#endif
#endif

/* ==========================================================================
 * 块 / 行内的扁平模型
 *
 * 与 Java 侧 com.lhzkml.jasmine.core.markdown.model 的 data class 一一对应；
 * JNI 层按这里的字段构造 Java 对象。
 * ========================================================================== */

/* 与 Java MarkdownBlockType 的 code 完全一致。 */
typedef enum {
  INCREMARK_BLOCK_PARAGRAPH = 0,
  INCREMARK_BLOCK_HEADING = 1,
  INCREMARK_BLOCK_CODE_BLOCK = 2,
  INCREMARK_BLOCK_MATH_BLOCK = 3,
  INCREMARK_BLOCK_THEMATIC_BREAK = 4,
  INCREMARK_BLOCK_TABLE = 5,
  INCREMARK_BLOCK_HTML_BLOCK = 6,
  INCREMARK_BLOCK_IMAGE = 7,
  INCREMARK_BLOCK_OTHER = 8
} incremark_block_type;

/* 与 Java MarkdownInlineType 的 code 完全一致。 */
typedef enum {
  INCREMARK_INLINE_TEXT = 0,
  INCREMARK_INLINE_SOFT_BREAK = 1,
  INCREMARK_INLINE_LINE_BREAK = 2,
  INCREMARK_INLINE_CODE = 3,
  INCREMARK_INLINE_HTML = 4,
  INCREMARK_INLINE_EMPHASIS = 5,
  INCREMARK_INLINE_STRONG = 6,
  INCREMARK_INLINE_STRIKETHROUGH = 7,
  INCREMARK_INLINE_HIGHLIGHT = 8, /* [腾讯扩展] */
  INCREMARK_INLINE_UNDERLINE = 9, /* [腾讯扩展] */
  INCREMARK_INLINE_LINK = 10,
  INCREMARK_INLINE_IMAGE = 11,
  INCREMARK_INLINE_FORMULA = 12, /* [腾讯扩展] */
  INCREMARK_INLINE_OTHER = 13
} incremark_inline_type;

/* 与 Java MarkdownContainerType 的 code 完全一致。 */
typedef enum {
  INCREMARK_CONTAINER_QUOTE = 0,
  INCREMARK_CONTAINER_NUMBERED_LIST = 1,
  INCREMARK_CONTAINER_TASK_LIST = 2,
  INCREMARK_CONTAINER_BULLETED_LIST_1 = 3,
  INCREMARK_CONTAINER_BULLETED_LIST_2 = 4,
  INCREMARK_CONTAINER_BULLETED_LIST_3 = 5,
  INCREMARK_CONTAINER_BULLETED_LIST_4 = 6
} incremark_container_type;

typedef struct incremark_inline incremark_inline;
typedef struct incremark_block incremark_block;
typedef struct incremark_prefix incremark_prefix;
typedef struct incremark_row incremark_row;
typedef struct incremark_cell incremark_cell;
typedef struct incremark_update incremark_update;
typedef struct incremark_parser incremark_parser;

struct incremark_inline {
  int type;             /* incremark_inline_type */
  char *literal;        /* 可为 NULL */
  char *url;            /* 可为 NULL */
  char *title;          /* 可为 NULL */
  incremark_inline **children;
  int n_children;
};

struct incremark_prefix {
  int container_type;   /* incremark_container_type */
  bool show_list_marker;
  bool show_quote_marker;
  bool is_end_block;
  int number_list_index;
  bool task_list_checked;
};

struct incremark_cell {
  incremark_inline **content;
  int n_content;
  int alignment; /* 0 none / 1 left / 2 center / 3 right */
};

struct incremark_row {
  bool is_header;
  incremark_cell **cells;
  int n_cells;
};

struct incremark_block {
  char *id;             /* 稳定标识："<起始行>:<type code>" */
  int type;             /* incremark_block_type */
  int heading_level;
  char *fence_info;     /* 可为 NULL */
  char *literal;        /* 可为 NULL */
  char *url;            /* 可为 NULL */
  char *title;          /* 可为 NULL */
  bool is_closed;
  incremark_inline **content;
  int n_content;
  incremark_row **table;
  int n_table;
  incremark_prefix **prefix;
  int n_prefix;
};

struct incremark_update {
  int index;              /* 稳定前缀的块数 —— apply 的截断点 */
  bool advanced;
  int newly_completed_count;
  incremark_block **blocks;
  int n_blocks;           /* 本次增量块数（含未闭合尾块） */
  int tail_count;         /* 尾部未闭合块数（0 或 1） */
};

/* ==========================================================================
 * parser 生命周期（对应 Java IncrementalMarkdownParser / NativeBridge）
 * ========================================================================== */

/* 新建解析器。返回 NULL 表示分配失败。 */
INCREMARK_API incremark_parser * incremark_parser_new(void);

/* 释放解析器及其内部状态。 */
INCREMARK_API void incremark_parser_free(incremark_parser *p);

/* 清空解析状态（buffer + 稳定边界 + 计数），保留实例。 */
INCREMARK_API void incremark_parser_reset(incremark_parser *p);

/*
 * 追加一段 UTF-8 delta，返回本次增量结果。
 *
 * 调用方负责用 incremark_update_free 释放返回值；返回 NULL 表示失败。
 * 空 delta 返回一个 index 不变的空 Update（与 ima 的 Update.EMPTY 语义一致）。
 */
INCREMARK_API incremark_update * incremark_parser_append(incremark_parser *p, const char *utf8,
                                          size_t len);

/* 流结束：把尾部未闭合的块收尾，返回最终增量。 */
INCREMARK_API incremark_update * incremark_parser_finalize(incremark_parser *p);

/* 取回累积的完整原文（新分配的 C 字符串，调用方 free）。 */
INCREMARK_API char * incremark_parser_copy_buffer(incremark_parser *p);

/* 整篇渲染为 Markdown / 纯文本（新分配，调用方 free）。 */
INCREMARK_API char * incremark_parser_render_markdown(incremark_parser *p);
INCREMARK_API char * incremark_parser_render_plaintext(incremark_parser *p);

/* 一次性纯文本渲染（无 handle）。 */
INCREMARK_API char * incremark_render_plaintext(const char *utf8, size_t len);

/* 释放上面返回的字符串。 */
INCREMARK_API void incremark_string_free(char *s);

/* ==========================================================================
 * Update 读取（JNI 层构造成 Java 对象后调用 incremark_update_free）
 * ========================================================================== */

INCREMARK_API int incremark_update_index(const incremark_update *u);
INCREMARK_API bool incremark_update_advanced(const incremark_update *u);
INCREMARK_API int incremark_update_newly_completed_count(const incremark_update *u);
INCREMARK_API int incremark_update_block_count(const incremark_update *u);
INCREMARK_API incremark_block * incremark_update_block(const incremark_update *u, int i);
INCREMARK_API int incremark_update_tail_count(const incremark_update *u);
INCREMARK_API incremark_block * incremark_update_tail_block(const incremark_update *u);
INCREMARK_API void incremark_update_free(incremark_update *u);

/* ==========================================================================
 * 增量解析核心（自研，不在 cmark-gfm 里）
 * ========================================================================== */

/*
 * 求「稳定边界」：从 text 中返回已定型顶层内容的结束字节偏移。
 *
 * 稳定 = 该位置之前的顶层块不会再被后续文本改写。判定依据：
 *   - 块之后出现空行（Markdown 里唯一可靠的硬边界），或
 *   - 块是自终止的（ATX 标题 / 分隔线 / 已闭合围栏 / 已闭合 $$ 块），
 *   - 且该块处于顶层（容器栈为空）。
 *
 * @param from 续扫起点（避免每次从头扫）。
 */
INCREMARK_API size_t incremark_find_stable_boundary(const char *text, size_t len, size_t from);

/* 行级谓词（供稳定边界扫描使用，也被渲染层复用）。 */
INCREMARK_API size_t incremark_leading_whitespace_count(const char *s, size_t len);
INCREMARK_API bool incremark_is_empty_line(const char *s, size_t len);
INCREMARK_API bool incremark_is_heading(const char *s, size_t len);
INCREMARK_API bool incremark_is_blockquote_start(const char *s, size_t len);
INCREMARK_API bool incremark_is_thematic_break(const char *s, size_t len);
INCREMARK_API bool incremark_detect_list_item_start(const char *s, size_t len);
INCREMARK_API bool incremark_detect_fence_start(const char *s, size_t len);
INCREMARK_API bool incremark_detect_fence_end(const char *s, size_t len);

/* 调试用类型名。 */
INCREMARK_API const char * incremark_block_type_name(int type);
INCREMARK_API const char * incremark_inline_type_name(int type);

#ifdef __cplusplus
}
#endif

#endif /* INCREMARK_H */
