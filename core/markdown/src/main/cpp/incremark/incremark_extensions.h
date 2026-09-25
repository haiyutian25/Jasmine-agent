/*
 * incremark_extensions.h —— ima `incremark` 自研语法扩展的对外声明（逆向重建）
 * ============================================================================
 * 三个腾讯自研扩展的节点类型全局变量 + 工厂函数。
 * 与上游 5 个扩展（table/strikethrough/autolink/tagfilter/tasklist）并列，
 * 共同由 cmark_gfm_core_extensions_ensure_registered() 注册 —— 实测注册例程
 * （0x28ca0）依次调用 8 个工厂，与 8 个 create_*_extension 导出符号一一对应。
 * ============================================================================
 */
#ifndef INCREMARK_EXTENSIONS_H
#define INCREMARK_EXTENSIONS_H

#include <cmark-gfm.h>
#include <cmark-gfm-extension_api.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- 腾讯自研节点类型（在上游枚举之外动态注册）---- */
extern cmark_node_type CMARK_NODE_HIGHLIGHT;   /* ==高亮==   */
extern cmark_node_type CMARK_NODE_UNDERLINE;   /* ~下划线~   */
extern cmark_node_type CMARK_NODE_FOMULA;      /* $数学$     */

/* ---- 工厂函数（.dynsym 中的真实导出符号）---- */
cmark_syntax_extension *create_highlight_extension(void);  /* @0x2c0f4 */
cmark_syntax_extension *create_underline_extension(void);  /* @0x2c590 */
cmark_syntax_extension *create_fomula_extension(void);     /* @0x2b654 */

/*
 * ---- 定界符对照（实测自各扩展 match 的字符判定与 push_delimiter 调用）----
 *
 *   highlight    '='   delims == 2  →  ==text==
 *   underline    '~'   delims == 1  →  ~text~        （与 strikethrough 共享 '~'）
 *   strikethrough'~'   delims == 2  →  ~~text~~      （腾讯已改为强制双波浪线）
 *   fomula       '$'   delims == 2 →  $$display$$ ;  delims == 1 → $inline$
 *                '\'   \(+ \) 或 \[+ \]  →  LaTeX 风格行内/行间公式
 *
 * ---- 渲染目标对照（字符串均自 .rodata 精确 dump）----
 *
 *   node            commonmark   latex        man        html        plaintext
 *   HIGHLIGHT       "=="         "\hl{"       ".HL \""   <mark>      "="
 *   UNDERLINE       "~"          "\uline{"    ".UL \""   <u>         "_"
 *   FOMULA          —            —            —          —           "$"
 *   STRIKETHROUGH   "~~"         "\sout{"     ".ST \""   <del>       "~"
 *
 *   （latex 的退出串统一为 "}"；man 的退出串统一为 "\""）
 *   FOMULA 未注册除 plaintext 外的任何渲染器 —— 数学排版由 incremark 的
 *   math_promote_block_dup / math_preprocess_dup / math_normalize_indent_dup
 *   在更上层处理。
 */

#ifdef __cplusplus
}
#endif

#endif /* INCREMARK_EXTENSIONS_H */
