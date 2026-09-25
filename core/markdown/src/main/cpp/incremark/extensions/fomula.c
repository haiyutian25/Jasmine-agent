/*
 * fomula.c —— ima `incremark` 自研数学扩展逆向重建
 * ============================================================================
 * 来源：libincremark_jni.so 的 create_fomula_extension @ 0x2b654
 *       及其 5 个回调（0x2b72c..0x2bb8c）
 *
 * 扩展名就是 "fomula"（拼写错误原样保留在 .dynsym 与 .rodata 中）。
 *
 * 置信度：中高。
 *   高：扩展名、特殊字符集、节点全局变量、注册的 setter 集合、
 *       定界符条件、字符串字面量 —— 均有字节级证据。
 *   中：`\` 分支中 peek/advance 的循环边界表达，以及
 *       cmark_inline_parser_scan_delimiters 的地址与 highlight 所用不同
 *       （0x136ed4 vs 0x13710c），疑似本地变体。
 *
 * ★ 与 highlight/underline 的三个显著差异：
 *   1. 只注册 5 个回调 —— **不设 commonmark/latex/man/html 渲染**，
 *      只有 plaintext_render；
 *   2. 特殊字符是 3 个：'$'、')'、']'；
 *   3. match 同时处理 `$`（GFM 风格）与 `\(` `\)` `\[` `\]`（LaTeX 风格）。
 * ============================================================================
 */
#include "fomula.h"
#include <parser.h>
#include <render.h>

cmark_node_type CMARK_NODE_FOMULA;

/* @0x2b7a0  size=632 */
static cmark_node *match(cmark_syntax_extension *self, cmark_parser *parser,
                         cmark_node *parent, unsigned char character,
                         cmark_inline_parser *inline_parser) {
  cmark_node *res = NULL;
  int left_flanking, right_flanking, punct_before, punct_after;
  char buffer[101];
  int delims;

  if (character == '\\') {
    /*
     * LaTeX 风格定界符：\( \) \[ \]
     * 反斜杠自身是定界符的一部分，故需先数连续反斜杠，
     * 再看其后紧跟的字符是否为 ()[] 之一。
     */
    int offset = cmark_inline_parser_get_offset(inline_parser);
    int nback = 0;
    unsigned char c;

    do {
      nback++;
      c = cmark_inline_parser_peek_at(inline_parser, offset + nback);
    } while (c == '\\');

    /* 此时 nback = 连续反斜杠个数；c = 其后第一个非反斜杠字符 */
    c = cmark_inline_parser_peek_at(inline_parser, offset + nback);

    if (c == '(' || c == ')' || c == '[' || c == ']') {
      if (nback > 0x3d)
        nback = 0x3e;

      memset(buffer, '\\', nback);
      buffer[nback] = (char)c;
      buffer[nback + 1] = 0;

      res = cmark_node_new_with_mem(CMARK_NODE_TEXT, parser->mem);
      cmark_node_set_literal(res, buffer);
      res->start_line = res->end_line = cmark_inline_parser_get_line(inline_parser);
      res->start_column = cmark_inline_parser_get_column(inline_parser);

      /* 消耗 nback+1 个字符 */
      for (int i = nback + 1; i != 0; i--)
        cmark_inline_parser_advance_offset(inline_parser);

      /*
       * ')' 与 ']' 是闭合定界符，'(' 与 '[' 是开启定界符。
       * （左/右 flanking 由字符身份直接决定，不依赖前后文。）
       */
      cmark_inline_parser_push_delimiter(inline_parser, c,
                                         c != ']' && c != ')',
                                         c == ']' || c == ')',
                                         res);
      return res;
    }

    return NULL;
  }

  if (character == '$') {
    delims = cmark_inline_parser_scan_delimiters(
        inline_parser, sizeof(buffer) - 1, '$',
        &left_flanking, &right_flanking, &punct_before, &punct_after);

    memset(buffer, '$', delims);
    buffer[delims] = 0;

    res = cmark_node_new_with_mem(CMARK_NODE_TEXT, parser->mem);
    cmark_node_set_literal(res, buffer);
    res->start_line = res->end_line = cmark_inline_parser_get_line(inline_parser);
    res->start_column = cmark_inline_parser_get_column(inline_parser) - delims;

    /*
     * `$$` → 强制双向可开可闭（行间公式）
     * `$`  → 沿用扫描出的 flanking；若两者皆假则放弃
     * 其余 → 放弃
     */
    if (delims == 2) {
      left_flanking = 1;
      right_flanking = 1;
    } else if (delims != 1 || (left_flanking == 0 && right_flanking == 0)) {
      return NULL;
    }

    cmark_inline_parser_push_delimiter(inline_parser, '$', left_flanking,
                                       right_flanking, res);
    return res;
  }

  return NULL;
}

/* @0x2ba18  size=372 */
static delimiter *insert(cmark_syntax_extension *self, cmark_parser *parser,
                         cmark_inline_parser *inline_parser, delimiter *opener,
                         delimiter *closer) {
  cmark_node *fomula;
  cmark_node *tmp, *next;
  delimiter *delim, *tmp_delim;
  delimiter *res = closer->next;
  unsigned char opener_char = opener->delim_char;

  fomula = opener->inl_text;

  /*
   * 配对校验：不同风格的定界符必须成对，且（对 \( \[ 而言）
   * 开启与闭合必须处于同一列。
   */
  if (opener_char == '[') {
    if (closer->delim_char != ']')
      goto done;
    if (opener->inl_text->start_column != closer->inl_text->start_column)
      goto done;
  } else if (opener_char == '(') {
    if (closer->delim_char != ')')
      goto done;
    if (opener->inl_text->start_column != closer->inl_text->start_column)
      goto done;
  } else if (opener_char == '$') {
    /* $...$ 只要求两侧定界符长度相同 */
    if (opener->inl_text->as.literal.len != closer->inl_text->as.literal.len)
      goto done;
  }

  if (!cmark_node_set_type(fomula, CMARK_NODE_FOMULA))
    goto done;

  cmark_node_set_syntax_extension(fomula, self);

  tmp = cmark_node_next(opener->inl_text);

  while (tmp) {
    if (tmp == closer->inl_text)
      break;
    next = cmark_node_next(tmp);
    cmark_node_append_child(fomula, tmp);
    tmp = next;
  }

  fomula->end_column = closer->inl_text->start_column + closer->inl_text->as.literal.len - 1;
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

/* @0x2b72c  size=44 */
static const char *get_type_string(cmark_syntax_extension *extension,
                                   cmark_node *node) {
  return node->type == CMARK_NODE_FOMULA ? "fomula" : "<unknown>";
}

/* @0x2b758  size=36 */
static int can_contain(cmark_syntax_extension *extension, cmark_node *node,
                       cmark_node_type child_type) {
  if (node->type != CMARK_NODE_FOMULA)
    return false;

  return CMARK_NODE_TYPE_INLINE_P(child_type);
}

/* @0x2b77c  size=36 —— 唯一的渲染回调，输出 "$" */
static void plaintext_render(cmark_syntax_extension *extension,
                             cmark_renderer *renderer, cmark_node *node,
                             cmark_event_type ev_type, int options) {
  renderer->out(renderer, node, "$", false, LITERAL);
}

/* @0x2b654  size=216 —— 注意比 highlight/underline 少 28 字节（少 2 个 setter） */
cmark_syntax_extension *create_fomula_extension(void) {
  cmark_syntax_extension *ext = cmark_syntax_extension_new("fomula");
  cmark_llist *special_chars = NULL;

  cmark_syntax_extension_set_get_type_string_func(ext, get_type_string);
  cmark_syntax_extension_set_can_contain_func(ext, can_contain);
  cmark_syntax_extension_set_plaintext_render_func(ext, plaintext_render);
  CMARK_NODE_FOMULA = cmark_syntax_extension_add_node(1);

  cmark_syntax_extension_set_match_inline_func(ext, match);
  cmark_syntax_extension_set_inline_from_delim_func(ext, insert);

  cmark_mem *mem = cmark_get_default_mem_allocator();
  special_chars = cmark_llist_append(mem, special_chars, (void *)'$');
  special_chars = cmark_llist_append(mem, special_chars, (void *)')');
  special_chars = cmark_llist_append(mem, special_chars, (void *)']');
  cmark_syntax_extension_set_special_inline_chars(ext, special_chars);

  cmark_syntax_extension_set_emphasis(ext, 1);

  return ext;
}
