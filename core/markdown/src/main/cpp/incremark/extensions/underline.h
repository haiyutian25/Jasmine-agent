/*
 * underline.h —— ima `incremark` 自研扩展「~下划线~」声明（逆向重建）
 *
 * ★ 与 strikethrough 共享 '~' 字符，靠 delims 数量区分：
 *   delims == 1 → UNDERLINE；delims == 2 → STRIKETHROUGH。
 *   上游原本允许单个 '~' 表示删除线（CMARK_OPT_STRIKETHROUGH_DOUBLE_TILDE 控制），
 *   腾讯改为强制双波浪线，把单个 '~' 让给了下划线。
 */
#ifndef INCREMARK_UNDERLINE_H
#define INCREMARK_UNDERLINE_H

#include <cmark-gfm-core-extensions.h>

extern cmark_node_type CMARK_NODE_UNDERLINE;

cmark_syntax_extension *create_underline_extension(void);

#endif /* INCREMARK_UNDERLINE_H */
