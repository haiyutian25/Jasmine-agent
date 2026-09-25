/*
 * fomula.h —— ima `incremark` 自研数学扩展声明（逆向重建）
 *
 * ⚠️ 文件名与函数名保留上游的**拼写错误** `fomula`（正确拼写为 formula）。
 *    这是实测结论：.dynsym 中的导出符号就是 `create_fomula_extension`，
 *    .rodata 中的扩展名就是 "fomula"，本文照抄以保持一致。
 */
#ifndef INCREMARK_FOMULA_H
#define INCREMARK_FOMULA_H

#include <cmark-gfm-core-extensions.h>

extern cmark_node_type CMARK_NODE_FOMULA;

cmark_syntax_extension *create_fomula_extension(void);

#endif /* INCREMARK_FOMULA_H */
