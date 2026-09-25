/*
 * highlight.h —— ima `incremark` 自研扩展「==高亮==」声明（逆向重建）
 *
 * 形态对齐上游 extensions/strikethrough.h：
 * 暴露一个节点类型全局变量 + 一个工厂函数。
 */
#ifndef INCREMARK_HIGHLIGHT_H
#define INCREMARK_HIGHLIGHT_H

#include <cmark-gfm-core-extensions.h>

extern cmark_node_type CMARK_NODE_HIGHLIGHT;

cmark_syntax_extension *create_highlight_extension(void);

#endif /* INCREMARK_HIGHLIGHT_H */
