/*
 * underline.c —— ima `incremark` 自研语法扩展「~下划线~」逆向重建
 * ============================================================================
 * 来源：libincremark_jni.so 的 create_underline_extension @ 0x2c590
 *       及其 9 个回调（0x2c684..0x2ca2c）
 * 模板：上游 cmark-gfm extensions/strikethrough.c
 *
 * 置信度：高。归一化后与 highlight.c 各 161 行、仅 36 行不同，
 *         全部差异为：扩展名、节点全局变量、字符串字面量、
 *         定界符字符（'~' vs '='）、delims 条件（1 vs 2）。
 *
 * ★ 关键语义：underline 用**单个** `~`，strikethrough 用**双个** `~~`。
 *   腾讯以此把同一个 `~` 字符拆成两种语法。
 * ============================================================================
 */
#include "underline.h"
#include <parser.h>
#include <render.h>

cmark_node_type CMARK_NODE_UNDERLINE;

/* @0x2c7f4  size=280 */
static cmark_node *match(cmark_syntax_extension *self, cmark_parser *parser,
                         cmark_node *parent, unsigned char character,
                         cmark_inline_parser *inline_parser) {
  cmark_node *res = NULL;
  int left_flanking, right_flanking, punct_before, punct_after, delims;
  char buffer[101];

  if (character != '~')
    return NULL;

  delims = cmark_inline_parser_scan_delimiters(
      inline_parser, sizeof(buffer) - 1, '~',
      &left_flanking, &right_flanking, &punct_before, &punct_after);

  memset(buffer, '~', delims);
  buffer[delims] = 0;

  res = cmark_node_new_with_mem(CMARK_NODE_TEXT, parser->mem);
  cmark_node_set_literal(res, buffer);
  res->start_line = res->end_line = cmark_inline_parser_get_line(inline_parser);
  res->start_column = cmark_inline_parser_get_column(inline_parser) - delims;

  /* 只接受单个 '~'；'~~' 留给 strikethrough 扩展 */
  if (delims == 1 && (left_flanking || right_flanking)) {
    cmark_inline_parser_push_delimiter(inline_parser, character, left_flanking,
                                       right_flanking, res);
  }

  return res;
}

/* @0x2c90c  size=288 */
static delimiter *insert(cmark_syntax_extension *self, cmark_parser *parser,
                         cmark_inline_parser *inline_parser, delimiter *opener,
                         delimiter *closer) {
  cmark_node *underline;
  cmark_node *tmp, *next;
  delimiter *delim, *tmp_delim;
  delimiter *res = closer->next;

  underline = opener->inl_text;

  if (opener->inl_text->as.literal.len != closer->inl_text->as.literal.len)
    goto done;

  if (!cmark_node_set_type(underline, CMARK_NODE_UNDERLINE))
    goto done;

  cmark_node_set_syntax_extension(underline, self);

  tmp = cmark_node_next(opener->inl_text);

  while (tmp) {
    if (tmp == closer->inl_text)
      break;
    next = cmark_node_next(tmp);
    cmark_node_append_child(underline, tmp);
    tmp = next;
  }

  underline->end_column = closer->inl_text->start_column + closer->inl_text->as.literal.len - 1;
  cmark_node_free(closer->inl_text);

done:
  delim = closer;
  while (delim != NULL && delim != opener) {
    tmp_delim = delim->previous;
    cmark_inline_parser_remove_delimiter(inline_parser, delim);
    delim = tmp_delim;
  }

  cmark_inline_parser_remove_delimiter(inline_parser, opener);

  return res;
}

/* @0x2c684  size=44 */
static const char *get_type_string(cmark_syntax_extension *extension,
                                   cmark_node *node) {
  return node->type == CMARK_NODE_UNDERLINE ? "underline" : "<unknown>";
}

/* @0x2c6b0  size=36 */
static int can_contain(cmark_syntax_extension *extension, cmark_node *node,
                       cmark_node_type child_type) {
  if (node->type != CMARK_NODE_UNDERLINE)
    return false;

  return CMARK_NODE_TYPE_INLINE_P(child_type);
}

/* @0x2c6d4  size=36 —— "~" */
static void commonmark_render(cmark_syntax_extension *extension,
                              cmark_renderer *renderer, cmark_node *node,
                              cmark_event_type ev_type, int options) {
  renderer->out(renderer, node, "~", false, LITERAL);
}

/* @0x2c6f8  size=52 —— "\uline{"（ulem 宏包） / "}" */
static void latex_render(cmark_syntax_extension *extension,
                         cmark_renderer *renderer, cmark_node *node,
                         cmark_event_type ev_type, int options) {
  bool entering = (ev_type == CMARK_EVENT_ENTER);
  if (entering) {
    renderer->out(renderer, node, "\\uline{", false, LITERAL);
  } else {
    renderer->out(renderer, node, "}", false, LITERAL);
  }
}

/* @0x2c72c  size=132 —— ".UL \"" */
static void man_render(cmark_syntax_extension *extension,
                       cmark_renderer *renderer, cmark_node *node,
                       cmark_event_type ev_type, int options) {
  bool entering = (ev_type == CMARK_EVENT_ENTER);
  if (entering) {
    renderer->cr(renderer);
    renderer->out(renderer, node, ".UL \"", false, LITERAL);
  } else {
    renderer->out(renderer, node, "\"", false, LITERAL);
    renderer->cr(renderer);
  }
}

/* @0x2c7b0  size=32 —— "<u>" / "</u>"
 * ★ 与 web 端白名单「有 mark 但无 u」形成对照：native 支持，web 不支持 */
static void html_render(cmark_syntax_extension *extension,
                        cmark_html_renderer *renderer, cmark_node *node,
                        cmark_event_type ev_type, int options) {
  bool entering = (ev_type == CMARK_EVENT_ENTER);
  if (entering) {
    cmark_strbuf_puts(renderer->html, "<u>");
  } else {
    cmark_strbuf_puts(renderer->html, "</u>");
  }
}

/* @0x2c7d0  size=36 —— "_" */
static void plaintext_render(cmark_syntax_extension *extension,
                             cmark_renderer *renderer, cmark_node *node,
                             cmark_event_type ev_type, int options) {
  renderer->out(renderer, node, "_", false, LITERAL);
}

/* @0x2c590  size=244 */
cmark_syntax_extension *create_underline_extension(void) {
  cmark_syntax_extension *ext = cmark_syntax_extension_new("underline");
  cmark_llist *special_chars = NULL;

  cmark_syntax_extension_set_get_type_string_func(ext, get_type_string);
  cmark_syntax_extension_set_can_contain_func(ext, can_contain);
  cmark_syntax_extension_set_commonmark_render_func(ext, commonmark_render);
  cmark_syntax_extension_set_latex_render_func(ext, latex_render);
  cmark_syntax_extension_set_man_render_func(ext, man_render);
  cmark_syntax_extension_set_html_render_func(ext, html_render);
  cmark_syntax_extension_set_plaintext_render_func(ext, plaintext_render);
  CMARK_NODE_UNDERLINE = cmark_syntax_extension_add_node(1);

  cmark_syntax_extension_set_match_inline_func(ext, match);
  cmark_syntax_extension_set_inline_from_delim_func(ext, insert);

  cmark_mem *mem = cmark_get_default_mem_allocator();
  special_chars = cmark_llist_append(mem, special_chars, (void *)'~');
  cmark_syntax_extension_set_special_inline_chars(ext, special_chars);

  cmark_syntax_extension_set_emphasis(ext, 1);

  return ext;
}
