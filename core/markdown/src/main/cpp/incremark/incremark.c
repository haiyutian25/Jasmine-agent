/*
 * incremark.c —— 增量 Markdown 引擎的 C 层实现（自研）
 * ============================================================================
 * 见 incremark.h 顶部的架构对照说明。这里只记实现要点：
 *
 * 【增量流程】append(delta)
 *   1. delta 追加进 p->buf；
 *   2. 只把 p->buf + p->stable_off 起的**尾部**喂给 cmark-gfm —— 前缀不重解析；
 *   3. flatten 尾部 AST 成 Block 树；
 *   4. 在尾部文本里求稳定边界，据此决定本轮有几个块「定型」，
 *      把 stable_off / stable_count 前移；
 *   5. 返回 Update { index = 本轮的 stable_count, blocks = 尾部块 }。
 *
 * 【为什么稳定判定按行、块划分按 cmark】
 * cmark 的 AST 不暴露字节偏移，只给 start_line/end_line。所以稳定边界用行级
 * 扫描独立算出（零分配、纯字符串比较 —— 与 ima 的 find_stable_boundary 同性质），
 * 再用「行 -> 字节偏移」表把两侧对齐。这样即使 cmark 的块划分与我预期不同，
 * 也不会错位。
 *
 * 【稳定性规则】一个顶层块稳定 <=> 它之后出现空行（Markdown 唯一可靠的硬边界），
 * 或它是自终止块（ATX 标题 / 分隔线 / 已闭合围栏 / 已闭合 $$）。列表/引用内的块
 * 一律等其容器整体收尾后才稳定。
 * ============================================================================
 */
#include "incremark.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cmark-gfm.h"
#include "cmark-gfm-core-extensions.h"
#include "buffer.h"
#include "node.h"
#include "parser.h"

/* 上游扩展的头（位于 third_party/cmark-gfm/extensions/，已在 include 路径中）。 */
#include "strikethrough.h"
#include "table.h"

/* 三个自研扩展的头。 */
#include "extensions/fomula.h"
#include "extensions/highlight.h"
#include "extensions/underline.h"

/* 表格对齐访问器：定义在 extensions/table.c，但上游 table.h 未声明。 */
extern uint16_t cmark_gfm_extensions_get_table_columns(cmark_node *node);
extern uint8_t *cmark_gfm_extensions_get_table_alignments(cmark_node *node);

/* 上游扩展工厂（extensions/ 各自导出）。 */
extern cmark_syntax_extension *create_strikethrough_extension(void);
extern cmark_syntax_extension *create_autolink_extension(void);
extern cmark_syntax_extension *create_tagfilter_extension(void);
extern cmark_syntax_extension *create_tasklist_extension(void);

/* ==========================================================================
 * 内存助手
 * ========================================================================== */

static void *im_alloc(size_t n) { return calloc(1, n == 0 ? 1 : n); }

static char *im_strdup(const char *s) {
  if (s == NULL) return NULL;
  size_t n = strlen(s) + 1;
  char *out = (char *)malloc(n);
  if (out != NULL) memcpy(out, s, n);
  return out;
}

static char *im_strndup(const char *s, size_t n) {
  char *out = (char *)malloc(n + 1);
  if (out == NULL) return NULL;
  if (n > 0) memcpy(out, s, n);
  out[n] = '\0';
  return out;
}

/*
 * 公式里的换行是否要算一个字符（空格）。
 *
 * ⚠️ 必须算。SOFT_BREAK / LINE_BREAK 节点的 literal 是 NULL，直接跳过的话
 *    换行会被无声吞掉、把前后两段粘连起来：
 *
 *        \qquad\nf(x)   →   \qquadf(x)      ← \qquadf 是不存在的命令，整段变红字
 *
 *    LaTeX 里普通换行本来就等价于一个空格，所以这里补一个空格正是它的语义。
 */
static bool is_break_node(cmark_node *n) {
  return n->type == CMARK_NODE_SOFTBREAK || n->type == CMARK_NODE_LINEBREAK;
}

/*
 * 取公式节点的正文。
 *
 * ⚠️ 不能直接 cmark_node_get_literal()：它只认 TEXT/CODE 等标准类型，自定义的
 * CMARK_NODE_FOMULA 会返回 NULL，结果就是块渲染成空白。
 * 公式正文其实在**子节点**里 —— fomula.c 的 insert 回调把定界符之间的内容
 * append 成了 children，节点自身只留着开定界符。所以这里把子节点文本拼起来。
 */
static char *fomula_text(cmark_node *node) {
  size_t need = 0;
  cmark_node *c;
  for (c = cmark_node_first_child(node); c != NULL; c = cmark_node_next(c)) {
    if (is_break_node(c)) {
      need += 1;
      continue;
    }
    const char *lit = cmark_node_get_literal(c);
    if (lit != NULL) need += strlen(lit);
  }
  if (need == 0) return NULL;
  char *out = (char *)malloc(need + 1);
  if (out == NULL) return NULL;
  size_t n = 0;
  for (c = cmark_node_first_child(node); c != NULL; c = cmark_node_next(c)) {
    if (is_break_node(c)) {
      out[n++] = ' ';
      continue;
    }
    const char *lit = cmark_node_get_literal(c);
    if (lit != NULL) {
      size_t l = strlen(lit);
      memcpy(out + n, lit, l);
      n += l;
    }
  }
  out[n] = '\0';
  return out;
}

/* ==========================================================================
 * 行级谓词（稳定边界扫描与渲染层共用）
 * ========================================================================== */

size_t incremark_leading_whitespace_count(const char *s, size_t len) {
  size_t i = 0;
  while (i < len && (s[i] == ' ' || s[i] == '\t')) i++;
  return i;
}

bool incremark_is_empty_line(const char *s, size_t len) {
  for (size_t i = 0; i < len; i++) {
    if (s[i] != ' ' && s[i] != '\t' && s[i] != '\r') return false;
  }
  return true;
}

bool incremark_is_heading(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  if (i >= len || s[i] != '#') return false;
  int n = 0;
  while (i < len && s[i] == '#') {
    n++;
    i++;
  }
  if (n < 1 || n > 6) return false;
  return i >= len || s[i] == ' ' || s[i] == '\t';
}

bool incremark_is_blockquote_start(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  return i < len && s[i] == '>';
}

bool incremark_is_thematic_break(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  char c = 0;
  int n = 0;
  for (; i < len; i++) {
    char ch = s[i];
    if (ch == ' ' || ch == '\t' || ch == '\r') continue;
    if (ch != '-' && ch != '*' && ch != '_') return false;
    if (c == 0) {
      c = ch;
    } else if (c != ch) {
      return false;
    }
    n++;
  }
  return n >= 3;
}

bool incremark_detect_list_item_start(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  if (i >= len) return false;
  char c = s[i];
  if (c == '-' || c == '*' || c == '+') {
    return (i + 1 < len) && (s[i + 1] == ' ' || s[i + 1] == '\t');
  }
  if (c >= '0' && c <= '9') {
    size_t j = i;
    while (j < len && s[j] >= '0' && s[j] <= '9') j++;
    return (j + 1 < len) && (s[j] == '.' || s[j] == ')') &&
           (s[j + 1] == ' ' || s[j + 1] == '\t');
  }
  return false;
}

/* 围栏起始：>=3 个 ` 或 ~；返回围栏字符，不是围栏返回 0。 */
static char fence_char(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  if (i >= len) return 0;
  char c = s[i];
  if (c != '`' && c != '~') return 0;
  size_t n = 0;
  while (i + n < len && s[i + n] == c) n++;
  if (n < 3) return 0;
  /* 反引号围栏的信息串不能含反引号（CommonMark 规则）。 */
  if (c == '`') {
    for (size_t j = i + n; j < len; j++) {
      if (s[j] == '`') return 0;
    }
  }
  return c;
}

bool incremark_detect_fence_start(const char *s, size_t len) {
  return fence_char(s, len) != 0;
}

bool incremark_detect_fence_end(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  if (i >= len) return false;
  char c = s[i];
  if (c != '`' && c != '~') return false;
  size_t n = 0;
  while (i + n < len && s[i + n] == c) n++;
  if (n < 3) return false;
  return incremark_is_empty_line(s + i + n, len - i - n);
}

static bool is_math_fence(const char *s, size_t len) {
  size_t i = incremark_leading_whitespace_count(s, len);
  return (i + 1 < len) && s[i] == '$' && s[i + 1] == '$';
}

/* ==========================================================================
 * 稳定边界
 *
 * 返回「仍在增长的尾部」的起始字节偏移 —— 该偏移之前的内容可以认为不再变化。
 * 与 ima 的 incremark_find_stable_boundary 同名同职；ima 该函数带一个不透明
 * BlockCtx（反汇编显示其字段是布尔/计数/缩进混合），本实现用显式局部状态代替。
 * ========================================================================== */
size_t incremark_find_stable_boundary(const char *text, size_t len, size_t from) {
  if (text == NULL || len == 0) return 0;
  if (from > len) from = 0;

  /* 从 from 所在行的行首开始，避免切在半行中间。 */
  size_t line_start = from;
  while (line_start > 0 && text[line_start - 1] != '\n') line_start--;

  size_t stable = line_start; /* 已定型内容的结束偏移 */
  size_t i = line_start;
  int in_fence = 0;
  char fence_c = 0;
  size_t fence_open = line_start;

  while (i < len) {
    size_t e = i;
    while (e < len && text[e] != '\n') e++;
    size_t line_len = e - i;
    const char *line = text + i;
    bool has_newline = (e < len);

    if (in_fence) {
      /* 闭合围栏：其后的一行（空行更佳）才算定型。 */
      if (incremark_detect_fence_end(line, line_len) &&
          (i < len) && (text[i] == fence_c || incremark_leading_whitespace_count(line, line_len) < line_len)) {
        /* 形态与开启字符一致即认为闭合。 */
        bool same = false;
        for (size_t k = 0; k < line_len; k++) {
          if (line[k] == fence_c) {
            same = true;
            break;
          }
        }
        if (same) {
          in_fence = 0;
          if (has_newline) stable = e + 1;
        }
      }
    } else {
      char fc = fence_char(line, line_len);
      if (fc != 0) {
        in_fence = 1;
        fence_c = fc;
        fence_open = i;
      } else if (incremark_is_empty_line(line, line_len)) {
        /* 空行：它之前的内容全部定型。 */
        stable = i;
      } else if (incremark_detect_list_item_start(line, line_len) ||
                 incremark_is_blockquote_start(line, line_len)) {
        /* 容器内：等整体收尾，保持上一个稳定点。 */
      } else if (incremark_is_heading(line, line_len) ||
                 incremark_is_thematic_break(line, line_len) ||
                 is_math_fence(line, line_len)) {
        /* 自终止块：行尾即候选稳定点。 */
        if (has_newline && e + 1 > stable) stable = e + 1;
      }
    }

    if (!has_newline) break;
    i = e + 1;
  }

  if (in_fence) stable = fence_open; /* 围栏未闭合，退回其起始处 */
  if (stable > len) stable = len;
  return stable;
}

/* ==========================================================================
 * 扩展注册（单例）
 *
 * ★ create_*_extension() 内部会调 cmark_syntax_extension_add_node()，每次都分配
 *   **新的节点类型编号**。重复调用会让同一扩展拿到不同的 CMARK_NODE_* 值，
 *   导致所有类型判定失效 —— 所以必须只注册一次。
 *
 * ima 的注册例程（@0x28ca0）依次调用 8 个工厂，与这里一致。
 * ========================================================================== */

static cmark_syntax_extension *g_table, *g_strike, *g_autolink, *g_tagfilter,
    *g_tasklist, *g_highlight, *g_underline, *g_fomula;
static bool g_registered = false;

static void ensure_extensions(void) {
  if (g_registered) return;
  g_table = create_table_extension();
  g_strike = create_strikethrough_extension();
  g_autolink = create_autolink_extension();
  g_tagfilter = create_tagfilter_extension();
  g_tasklist = create_tasklist_extension();
  g_highlight = create_highlight_extension();
  g_underline = create_underline_extension();
  g_fomula = create_fomula_extension();
  g_registered = true;
}

/*
 * ==========================================================================
 * 反斜杠转义规则（LaTeX 修正）
 *
 * cmark 默认把 `\` + ASCII 标点当作「markdown 转义」：吃掉反斜杠、只留标点。
 * 但 LaTeX 里有一批命令**以标点开头**，被这样吃掉后语法就散了：
 *
 *     \begin{pmatrix} a & b \\ c & d \end{pmatrix}   →  a & b \ c & d   矩阵结构崩塌
 *     \int f(x)\,dx                                  →  \int f(x),dx    细空格变逗号
 *     \begin{cases} 1, & x>0 \\ 0, & x\le 0 \end{cases}  →  分段函数崩掉
 *
 * 根因在 cmark 的 handle_backslash（inlines.c:837）：它对 `\x` 的处理不区分上下文，
 * 而 `\` 走的是 switch 的独立分支，**根本不经过语法扩展**，所以扩展层拦不住。
 * cmark 为此留了 cmark_parser_set_backslash_ispunct_func 这个钩子 —— 这里就是用它。
 *
 * 保护集只收「LaTeX 需要、而 markdown 里没有含义」的标点，因此不改动任何
 * 正常 markdown 转义行为（`\*` 抑制强调、`\-` 等一概不受影响）。
 *
 * 刻意**不**保护的：
 *   \$ — 正文里 "价格 \$100" 是常见写法，要让 `$` 正常出现
 *   \~ — 用来抑制 GFM 删除线
 * 这两个在 LaTeX 里出现频率低，取舍上让 markdown 优先。
 * ==========================================================================
 */
static int latex_backslash_ispunct(char c) {
  switch (c) {
    case '\\':  /* \\ 换行 */
    case ',':   /* \, 细空格 */
    case ';':   /* \; 粗空格 */
    case ':':   /* \: 中等空格 */
    case '!':   /* \! 负空格 */
    case '{':   /* \{ */
    case '}':   /* \} */
    case '_':   /* \_ 下划线 */
    case '%':   /* \% */
    case '&':   /* \& */
    case '#':   /* \# */
    case '^':   /* \^ 抑扬符 */
    case '@':   /* \@ */
      return 0;
    default:
      return cmark_ispunct(c);
  }
}

static cmark_parser *make_parser(void) {
  ensure_extensions();
  cmark_parser *parser = cmark_parser_new_with_mem(
      CMARK_OPT_DEFAULT, cmark_get_default_mem_allocator());
  /* 见上方 latex_backslash_ispunct 的说明。 */
  cmark_parser_set_backslash_ispunct_func(parser, latex_backslash_ispunct);
  cmark_parser_attach_syntax_extension(parser, g_table);
  cmark_parser_attach_syntax_extension(parser, g_strike);
  cmark_parser_attach_syntax_extension(parser, g_autolink);
  cmark_parser_attach_syntax_extension(parser, g_tagfilter);
  cmark_parser_attach_syntax_extension(parser, g_tasklist);
  cmark_parser_attach_syntax_extension(parser, g_highlight);
  cmark_parser_attach_syntax_extension(parser, g_underline);
  cmark_parser_attach_syntax_extension(parser, g_fomula);
  return parser;
}

/* ==========================================================================
 * 扁平模型容器
 * ========================================================================== */

typedef struct {
  incremark_block **items;
  int count;
  int cap;
} block_vec;

static void bv_push(block_vec *v, incremark_block *b) {
  if (v->count == v->cap) {
    v->cap = v->cap ? v->cap * 2 : 8;
    v->items = (incremark_block **)realloc(v->items, sizeof(void *) * (size_t)v->cap);
  }
  v->items[v->count++] = b;
}

typedef struct {
  incremark_inline **items;
  int count;
  int cap;
} inline_vec;

static void iv_push(inline_vec *v, incremark_inline *x) {
  if (v->count == v->cap) {
    v->cap = v->cap ? v->cap * 2 : 8;
    v->items = (incremark_inline **)realloc(v->items, sizeof(void *) * (size_t)v->cap);
  }
  v->items[v->count++] = x;
}

/* 前缀栈：定长即可 —— Markdown 嵌套不会无界。 */
#define IM_PREFIX_MAX 32
typedef struct {
  incremark_prefix *items[IM_PREFIX_MAX];
  int count;
} prefix_stack;

/* 前向声明（互相递归 + 定义顺序） */
static void flatten_block(cmark_node *node, prefix_stack *prefix, block_vec *out);
static void flatten_inlines(cmark_node *node, inline_vec *out);
static void free_inline(incremark_inline *x);
static void free_block(incremark_block *b);
static void assign_prefix(incremark_block *b, prefix_stack *st);

/* ---------- 行内 ---------- */

static incremark_inline *new_inline(int type) {
  incremark_inline *x = (incremark_inline *)im_alloc(sizeof(incremark_inline));
  x->type = type;
  return x;
}

static void flatten_inline_children(cmark_node *parent, incremark_inline *target) {
  inline_vec v = {0};
  for (cmark_node *c = cmark_node_first_child(parent); c != NULL;
       c = cmark_node_next(c)) {
    flatten_inlines(c, &v);
  }
  target->children = v.items;
  target->n_children = v.count;
}

static void flatten_inlines(cmark_node *node, inline_vec *out) {
  cmark_node_type t = cmark_node_get_type(node);

  switch (t) {
    case CMARK_NODE_TEXT: {
      const char *lit = cmark_node_get_literal(node);
      if (lit != NULL && lit[0] != '\0') {
        incremark_inline *x = new_inline(INCREMARK_INLINE_TEXT);
        x->literal = im_strdup(lit);
        iv_push(out, x);
      }
      return;
    }
    case CMARK_NODE_SOFTBREAK:
      iv_push(out, new_inline(INCREMARK_INLINE_SOFT_BREAK));
      return;
    case CMARK_NODE_LINEBREAK:
      iv_push(out, new_inline(INCREMARK_INLINE_LINE_BREAK));
      return;
    case CMARK_NODE_CODE: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_CODE);
      x->literal = im_strdup(cmark_node_get_literal(node));
      iv_push(out, x);
      return;
    }
    case CMARK_NODE_HTML_INLINE: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_HTML);
      x->literal = im_strdup(cmark_node_get_literal(node));
      iv_push(out, x);
      return;
    }
    case CMARK_NODE_STRONG: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_STRONG);
      flatten_inline_children(node, x);
      iv_push(out, x);
      return;
    }
    case CMARK_NODE_EMPH: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_EMPHASIS);
      flatten_inline_children(node, x);
      iv_push(out, x);
      return;
    }
    case CMARK_NODE_LINK: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_LINK);
      x->url = im_strdup(cmark_node_get_url(node));
      x->title = im_strdup(cmark_node_get_title(node));
      flatten_inline_children(node, x);
      iv_push(out, x);
      return;
    }
    case CMARK_NODE_IMAGE: {
      incremark_inline *x = new_inline(INCREMARK_INLINE_IMAGE);
      x->url = im_strdup(cmark_node_get_url(node));
      x->title = im_strdup(cmark_node_get_title(node));
      flatten_inline_children(node, x);
      iv_push(out, x);
      return;
    }
    default:
      break;
  }

  /* 三个腾讯扩展的节点类型是运行时注册的，不能放进 switch 的 case（不是常量）。 */
  if (g_strike != NULL && t == CMARK_NODE_STRIKETHROUGH) {
    incremark_inline *x = new_inline(INCREMARK_INLINE_STRIKETHROUGH);
    flatten_inline_children(node, x);
    iv_push(out, x);
    return;
  }
  if (g_highlight != NULL && t == CMARK_NODE_HIGHLIGHT) {
    incremark_inline *x = new_inline(INCREMARK_INLINE_HIGHLIGHT);
    flatten_inline_children(node, x);
    iv_push(out, x);
    return;
  }
  if (g_underline != NULL && t == CMARK_NODE_UNDERLINE) {
    incremark_inline *x = new_inline(INCREMARK_INLINE_UNDERLINE);
    flatten_inline_children(node, x);
    iv_push(out, x);
    return;
  }
  if (g_fomula != NULL && t == CMARK_NODE_FOMULA) {
    incremark_inline *x = new_inline(INCREMARK_INLINE_FORMULA);
    x->literal = fomula_text(node);
    iv_push(out, x);
    return;
  }

  /* 未知行内节点：展开子节点，尽量不丢内容。 */
  for (cmark_node *c = cmark_node_first_child(node); c != NULL;
       c = cmark_node_next(c)) {
    flatten_inlines(c, out);
  }
}

/* ---------- 块 ---------- */

static incremark_block *new_block(int type, cmark_node *node) {
  incremark_block *b = (incremark_block *)im_alloc(sizeof(incremark_block));
  b->type = type;
  int line = (node != NULL) ? cmark_node_get_start_line(node) : 0;
  char idbuf[64];
  snprintf(idbuf, sizeof(idbuf), "%d:%d", line, type);
  b->id = im_strdup(idbuf);
  b->is_closed = true;
  return b;
}

static void assign_prefix(incremark_block *b, prefix_stack *st) {
  if (st == NULL || st->count == 0) return;
  b->prefix = (incremark_prefix **)im_alloc(sizeof(void *) * (size_t)st->count);
  for (int i = 0; i < st->count; i++) {
    incremark_prefix *cp = (incremark_prefix *)im_alloc(sizeof(incremark_prefix));
    *cp = *st->items[i];
    b->prefix[i] = cp;
  }
  b->n_prefix = st->count;
}

static void collect_inlines(cmark_node *parent, inline_vec *iv) {
  for (cmark_node *c = cmark_node_first_child(parent); c != NULL;
       c = cmark_node_next(c)) {
    flatten_inlines(c, iv);
  }
}

static bool inlines_are_blank(inline_vec *iv) {
  for (int i = 0; i < iv->count; i++) {
    incremark_inline *x = iv->items[i];
    if (x->type == INCREMARK_INLINE_TEXT || x->type == INCREMARK_INLINE_CODE) {
      if (x->literal != NULL && x->literal[0] != '\0') return false;
    } else if (x->type != INCREMARK_INLINE_SOFT_BREAK &&
               x->type != INCREMARK_INLINE_LINE_BREAK) {
      return false;
    }
  }
  return true;
}

/* 段落：整段只有一个公式 -> MATH_BLOCK；只有一张图 -> IMAGE；否则 PARAGRAPH。 */
static void flatten_paragraph(cmark_node *node, prefix_stack *prefix, block_vec *out) {
  inline_vec iv = {0};
  collect_inlines(node, &iv);

  if (inlines_are_blank(&iv)) {
    for (int i = 0; i < iv.count; i++) free_inline(iv.items[i]);
    free(iv.items);
    return;
  }

  /* ★ ima 的 math_promote_block_dup 等价物：单个公式提升为块级数学块。 */
  if (iv.count == 1 && iv.items[0]->type == INCREMARK_INLINE_FORMULA) {
    incremark_block *b = new_block(INCREMARK_BLOCK_MATH_BLOCK, node);
    b->literal = im_strdup(iv.items[0]->literal);
    assign_prefix(b, prefix);
    bv_push(out, b);
    free_inline(iv.items[0]);
    free(iv.items);
    return;
  }
  if (iv.count == 1 && iv.items[0]->type == INCREMARK_INLINE_IMAGE) {
    incremark_block *b = new_block(INCREMARK_BLOCK_IMAGE, node);
    b->url = im_strdup(iv.items[0]->url);
    b->title = im_strdup(iv.items[0]->title);
    b->content = iv.items;
    b->n_content = iv.count;
    assign_prefix(b, prefix);
    bv_push(out, b);
    return;
  }

  incremark_block *b = new_block(INCREMARK_BLOCK_PARAGRAPH, node);
  b->content = iv.items;
  b->n_content = iv.count;
  assign_prefix(b, prefix);
  bv_push(out, b);
}

static void flatten_table(cmark_node *node, prefix_stack *prefix, block_vec *out) {
  incremark_block *b = new_block(INCREMARK_BLOCK_TABLE, node);
  uint16_t ncols = cmark_gfm_extensions_get_table_columns(node);
  uint8_t *aligns = cmark_gfm_extensions_get_table_alignments(node);

  int row_count = 0;
  for (cmark_node *r = cmark_node_first_child(node); r != NULL;
       r = cmark_node_next(r)) {
    row_count++;
  }
  b->table = (incremark_row **)im_alloc(sizeof(void *) * (size_t)(row_count ? row_count : 1));

  int r = 0;
  for (cmark_node *row_node = cmark_node_first_child(node); row_node != NULL;
       row_node = cmark_node_next(row_node), r++) {
    incremark_row *row = (incremark_row *)im_alloc(sizeof(incremark_row));
    row->is_header = (r == 0); /* 分隔行不在 AST 里，第 0 行即表头 */

    int cell_count = 0;
    for (cmark_node *c = cmark_node_first_child(row_node); c != NULL;
         c = cmark_node_next(c)) {
      cell_count++;
    }
    row->cells = (incremark_cell **)im_alloc(sizeof(void *) * (size_t)(cell_count ? cell_count : 1));

    int ci = 0;
    for (cmark_node *cell_node = cmark_node_first_child(row_node); cell_node != NULL;
         cell_node = cmark_node_next(cell_node), ci++) {
      incremark_cell *cell = (incremark_cell *)im_alloc(sizeof(incremark_cell));
      inline_vec iv = {0};
      collect_inlines(cell_node, &iv);
      cell->content = iv.items;
      cell->n_content = iv.count;
      int align = 0;
      if (aligns != NULL && ci < (int)ncols) {
        switch (aligns[ci]) {
          case 'l': align = 1; break;
          case 'c': align = 2; break;
          case 'r': align = 3; break;
          default: align = 0; break;
        }
      }
      cell->alignment = align;
      row->cells[ci] = cell;
    }
    row->n_cells = cell_count;
    b->table[r] = row;
  }
  b->n_table = row_count;
  assign_prefix(b, prefix);
  bv_push(out, b);
}

static void flatten_block(cmark_node *node, prefix_stack *prefix, block_vec *out) {
  cmark_node_type t = cmark_node_get_type(node);

  switch (t) {
    case CMARK_NODE_PARAGRAPH:
      flatten_paragraph(node, prefix, out);
      return;
    case CMARK_NODE_HEADING: {
      incremark_block *b = new_block(INCREMARK_BLOCK_HEADING, node);
      b->heading_level = cmark_node_get_heading_level(node);
      inline_vec iv = {0};
      collect_inlines(node, &iv);
      b->content = iv.items;
      b->n_content = iv.count;
      assign_prefix(b, prefix);
      bv_push(out, b);
      return;
    }
    case CMARK_NODE_CODE_BLOCK: {
      incremark_block *b = new_block(INCREMARK_BLOCK_CODE_BLOCK, node);
      const char *info = cmark_node_get_fence_info(node);
      b->fence_info = im_strdup(info != NULL ? info : "");
      b->literal = im_strdup(cmark_node_get_literal(node));
      assign_prefix(b, prefix);
      bv_push(out, b);
      return;
    }
    case CMARK_NODE_THEMATIC_BREAK: {
      incremark_block *b = new_block(INCREMARK_BLOCK_THEMATIC_BREAK, node);
      assign_prefix(b, prefix);
      bv_push(out, b);
      return;
    }
    case CMARK_NODE_HTML_BLOCK: {
      incremark_block *b = new_block(INCREMARK_BLOCK_HTML_BLOCK, node);
      b->literal = im_strdup(cmark_node_get_literal(node));
      assign_prefix(b, prefix);
      bv_push(out, b);
      return;
    }
    case CMARK_NODE_BLOCK_QUOTE: {
      if (prefix->count >= IM_PREFIX_MAX) return;
      incremark_prefix q;
      memset(&q, 0, sizeof(q));
      q.container_type = INCREMARK_CONTAINER_QUOTE;
      q.show_quote_marker = true;
      prefix->items[prefix->count++] = &q;
      for (cmark_node *c = cmark_node_first_child(node); c != NULL;
           c = cmark_node_next(c)) {
        flatten_block(c, prefix, out);
      }
      prefix->count--;
      return;
    }
    case CMARK_NODE_LIST: {
      if (prefix->count >= IM_PREFIX_MAX) return;
      cmark_list_type lt = cmark_node_get_list_type(node);
      int start = (lt == CMARK_ORDERED_LIST) ? cmark_node_get_list_start(node) : 0;

      int item_total = 0;
      for (cmark_node *c = cmark_node_first_child(node); c != NULL;
           c = cmark_node_next(c)) {
        item_total++;
      }

      int item_no = 0;
      for (cmark_node *item = cmark_node_first_child(node); item != NULL;
           item = cmark_node_next(item)) {
        item_no++;
        incremark_prefix lp;
        memset(&lp, 0, sizeof(lp));
        if (lt == CMARK_ORDERED_LIST) {
          lp.container_type = INCREMARK_CONTAINER_NUMBERED_LIST;
          lp.number_list_index = start + item_no - 1;
        } else {
          /* 无序列表按嵌套深度分四档 —— 扁平模型没有父子关系，缩进只能这样编码。 */
          int depth = 0;
          for (int i = 0; i < prefix->count; i++) {
            int ct = prefix->items[i]->container_type;
            if (ct >= INCREMARK_CONTAINER_BULLETED_LIST_1 &&
                ct <= INCREMARK_CONTAINER_BULLETED_LIST_4) {
              depth++;
            }
          }
          if (depth > 3) depth = 3;
          lp.container_type = INCREMARK_CONTAINER_BULLETED_LIST_1 + depth;
        }
        lp.show_list_marker = true;
        lp.is_end_block = (item_no == item_total);

        prefix->items[prefix->count++] = &lp;
        int before = out->count;
        for (cmark_node *c = cmark_node_first_child(item); c != NULL;
             c = cmark_node_next(c)) {
          flatten_block(c, prefix, out);
        }
        /* 列表标记只显示在本条目的第一个块上（续写行不重复画圆点）。 */
        for (int k = before + 1; k < out->count; k++) {
          for (int m = 0; m < out->items[k]->n_prefix; m++) {
            if (out->items[k]->prefix[m]->container_type == lp.container_type) {
              out->items[k]->prefix[m]->show_list_marker = false;
            }
          }
        }
        prefix->count--;
      }
      return;
    }
    case CMARK_NODE_ITEM:
      for (cmark_node *c = cmark_node_first_child(node); c != NULL;
           c = cmark_node_next(c)) {
        flatten_block(c, prefix, out);
      }
      return;
    default:
      break;
  }

  if (g_table != NULL && t == CMARK_NODE_TABLE) {
    flatten_table(node, prefix, out);
    return;
  }
  if (g_fomula != NULL && t == CMARK_NODE_FOMULA) {
    incremark_block *b = new_block(INCREMARK_BLOCK_MATH_BLOCK, node);
    b->literal = fomula_text(node);
    assign_prefix(b, prefix);
    bv_push(out, b);
    return;
  }

  /* 未知块：展开子节点。 */
  for (cmark_node *c = cmark_node_first_child(node); c != NULL;
       c = cmark_node_next(c)) {
    flatten_block(c, prefix, out);
  }
}

/* 把一棵文档树拍平成块列表。调用方负责 free_block 每一项。 */
static void flatten_document(cmark_node *doc, block_vec *out) {
  prefix_stack prefix;
  memset(&prefix, 0, sizeof(prefix));
  for (cmark_node *n = cmark_node_first_child(doc); n != NULL;
       n = cmark_node_next(n)) {
    flatten_block(n, &prefix, out);
  }
}

/* ==========================================================================
 * parser 生命周期
 * ========================================================================== */

struct incremark_parser {
  char *buf;
  size_t len;
  size_t cap;
  size_t stable_off; /* 尾部起点（字节偏移） */
  int stable_count;  /* stable_off 之前的块数，已交给 Java */
};

incremark_parser *incremark_parser_new(void) {
  ensure_extensions();
  incremark_parser *p = (incremark_parser *)im_alloc(sizeof(incremark_parser));
  p->cap = 4096;
  p->buf = (char *)malloc(p->cap);
  if (p->buf == NULL) {
    free(p);
    return NULL;
  }
  p->buf[0] = '\0';
  p->len = 0;
  p->stable_off = 0;
  p->stable_count = 0;
  return p;
}

void incremark_parser_free(incremark_parser *p) {
  if (p == NULL) return;
  free(p->buf);
  free(p);
}

void incremark_parser_reset(incremark_parser *p) {
  if (p == NULL) return;
  p->len = 0;
  if (p->buf != NULL) p->buf[0] = '\0';
  p->stable_off = 0;
  p->stable_count = 0;
}

char *incremark_parser_copy_buffer(incremark_parser *p) {
  if (p == NULL) return im_strdup("");
  return im_strndup(p->buf != NULL ? p->buf : "", p->len);
}

static void buffer_append(incremark_parser *p, const char *utf8, size_t len) {
  if (len == 0) return;
  if (p->len + len + 1 > p->cap) {
    while (p->len + len + 1 > p->cap) p->cap *= 2;
    p->buf = (char *)realloc(p->buf, p->cap);
  }
  memcpy(p->buf + p->len, utf8, len);
  p->len += len;
  p->buf[p->len] = '\0';
}

/* ==========================================================================
 * Update 组装
 * ========================================================================== */

static incremark_update *build_update(int index, bool advanced, int newly_completed,
                                      incremark_block **blocks, int n_blocks) {
  incremark_update *u = (incremark_update *)im_alloc(sizeof(incremark_update));
  u->index = index;
  u->advanced = advanced;
  u->newly_completed_count = newly_completed;
  u->blocks = blocks;
  u->n_blocks = n_blocks;
  u->tail_count = 0;
  for (int i = 0; i < n_blocks; i++) {
    if (!blocks[i]->is_closed) {
      u->tail_count = 1;
      break;
    }
  }
  return u;
}

/*
 * 把「尾部相对偏移 -> 尾部内行号」的映射表建出来。
 * 用于把块的行号（cmark 只给行号）换算回字节偏移。
 */
typedef struct {
  size_t *offsets; /* offsets[line] = 该行（1 基）的起始偏移；末尾多一项 = 文本长度 */
  int count;       /* 行数 + 1 */
} line_index;

static void line_index_build(const char *text, size_t len, line_index *li) {
  int cap = 64;
  li->offsets = (size_t *)malloc(sizeof(size_t) * (size_t)cap);
  li->count = 0;
  li->offsets[li->count++] = 0;
  for (size_t i = 0; i < len; i++) {
    if (text[i] == '\n') {
      if (li->count == cap) {
        cap *= 2;
        li->offsets = (size_t *)realloc(li->offsets, sizeof(size_t) * (size_t)cap);
      }
      li->offsets[li->count++] = i + 1;
    }
  }
  if (li->count == cap) {
    li->offsets = (size_t *)realloc(li->offsets, sizeof(size_t) * (size_t)(cap + 1));
  }
  li->offsets[li->count++] = len;
}

static void line_index_free(line_index *li) { free(li->offsets); }

/*
 * 解析尾部并组装 Update。
 *
 * ★ index 语义：返回的 blocks 是 **stable_off 之后的全部块**（含未闭合尾块）；
 *   调用方 apply 时把 ast 从 index 起截断再追加。index 之前的块完全不重解析。
 */
static incremark_update *parse_tail(incremark_parser *p, bool final) {
  if (p == NULL) return NULL;

  /*
   * ★ index 的语义是「截断点」：本轮返回的 blocks 覆盖 [stable_off, len)，
   *   而调用方的 ast[0, index) 是**之前各轮已经交付**的块。所以 index 必须取
   *   本轮开始时的 stable_count，而不是推进之后的值 —— 否则 ast 里已有的块
   *   会与 blocks 里重复的块一起出现，列表越滚越长。
   */
  const int index = p->stable_count;

  const char *tail = p->buf + p->stable_off;
  size_t tail_len = p->len - p->stable_off;

  block_vec out = {0};
  line_index li = {0};
  if (tail_len > 0) {
    line_index_build(tail, tail_len, &li);

    cmark_parser *parser = make_parser();
    cmark_parser_feed(parser, tail, tail_len);
    cmark_node *doc = cmark_parser_finish(parser);
    flatten_document(doc, &out);
    cmark_node_free(doc);
    cmark_parser_free(parser);
  }

  int newly_completed = 0;

  if (final) {
    /* 流结束：尾部全部定型（未闭合的围栏/标题/表格在此收尾）。 */
    for (int i = 0; i < out.count; i++) out.items[i]->is_closed = true;
    newly_completed = out.count;
    p->stable_off = p->len;
    p->stable_count += out.count;
  } else if (tail_len > 0) {
    /* 尾部内部的稳定边界（相对 tail 的字节偏移）。 */
    size_t rel = incremark_find_stable_boundary(tail, tail_len, 0);

    /*
     * 起点落在 [0, rel) 内的块本轮定型。
     *
     * 用「起始偏移」而不是「结束偏移」判定是安全的：rel 一定落在顶层块的边界上
     * （incremark_find_stable_boundary 只在空行或自终止块之后推进），而容器内
     * 的块（列表项等）会让 rel 停在容器之前，于是不会被误判为定型。
     */
    int stable_in_tail = 0;
    for (int i = 0; i < out.count; i++) {
      int line = atoi(out.items[i]->id); /* id 形如 "<行号>:<type>" */
      size_t start_off =
          (line > 0 && line - 1 < li.count) ? li.offsets[line - 1] : tail_len;
      if (start_off < rel) {
        out.items[i]->is_closed = true;
        stable_in_tail++;
      } else {
        out.items[i]->is_closed = false;
      }
    }
    if (stable_in_tail > 0) {
      p->stable_off += rel;
      p->stable_count += stable_in_tail;
      newly_completed = stable_in_tail;
    }
  }

  /* advanced：本轮的稳定块数是否真的往前推进了。 */
  bool advanced = p->stable_count > index;

  incremark_update *u =
      build_update(index, advanced, newly_completed, out.items, out.count);
  line_index_free(&li);
  return u;
}

incremark_update *incremark_parser_append(incremark_parser *p, const char *utf8,
                                          size_t len) {
  if (p == NULL || utf8 == NULL) return NULL;
  if (len == 0) return build_update(p->stable_count, false, 0, NULL, 0);
  buffer_append(p, utf8, len);
  return parse_tail(p, false);
}

incremark_update *incremark_parser_finalize(incremark_parser *p) {
  if (p == NULL) return NULL;
  return parse_tail(p, true);
}

/* ==========================================================================
 * Update 访问器
 * ========================================================================== */

int incremark_update_index(const incremark_update *u) { return u ? u->index : 0; }

bool incremark_update_advanced(const incremark_update *u) {
  return u ? u->advanced : false;
}

int incremark_update_newly_completed_count(const incremark_update *u) {
  return u ? u->newly_completed_count : 0;
}

int incremark_update_block_count(const incremark_update *u) {
  return u ? u->n_blocks : 0;
}

incremark_block *incremark_update_block(const incremark_update *u, int i) {
  if (u == NULL || i < 0 || i >= u->n_blocks) return NULL;
  return u->blocks[i];
}

int incremark_update_tail_count(const incremark_update *u) {
  return u ? u->tail_count : 0;
}

incremark_block *incremark_update_tail_block(const incremark_update *u) {
  if (u == NULL) return NULL;
  for (int i = u->n_blocks - 1; i >= 0; i--) {
    if (!u->blocks[i]->is_closed) return u->blocks[i];
  }
  return NULL;
}

/* ==========================================================================
 * 释放
 * ========================================================================== */

static void free_inline_array(incremark_inline **arr, int n) {
  for (int i = 0; i < n; i++) free_inline(arr[i]);
  free(arr);
}

static void free_inline(incremark_inline *x) {
  if (x == NULL) return;
  free(x->literal);
  free(x->url);
  free(x->title);
  free_inline_array(x->children, x->n_children);
  free(x);
}

static void free_block(incremark_block *b) {
  if (b == NULL) return;
  free(b->id);
  free(b->fence_info);
  free(b->literal);
  free(b->url);
  free(b->title);
  free_inline_array(b->content, b->n_content);
  for (int i = 0; i < b->n_table; i++) {
    incremark_row *r = b->table[i];
    if (r == NULL) continue;
    for (int j = 0; j < r->n_cells; j++) {
      incremark_cell *c = r->cells[j];
      if (c == NULL) continue;
      free_inline_array(c->content, c->n_content);
      free(c);
    }
    free(r->cells);
    free(r);
  }
  free(b->table);
  for (int i = 0; i < b->n_prefix; i++) free(b->prefix[i]);
  free(b->prefix);
  free(b);
}

void incremark_update_free(incremark_update *u) {
  if (u == NULL) return;
  for (int i = 0; i < u->n_blocks; i++) free_block(u->blocks[i]);
  free(u->blocks);
  free(u);
}

void incremark_string_free(char *s) { free(s); }

/* ==========================================================================
 * 渲染
 * ========================================================================== */

static void render_inlines_plain(incremark_inline **arr, int n, cmark_strbuf *buf) {
  for (int i = 0; i < n; i++) {
    incremark_inline *x = arr[i];
    if (x == NULL) continue;
    switch (x->type) {
      case INCREMARK_INLINE_SOFT_BREAK:
      case INCREMARK_INLINE_LINE_BREAK:
        cmark_strbuf_putc(buf, ' ');
        break;
      case INCREMARK_INLINE_IMAGE:
        if (x->literal) cmark_strbuf_puts(buf, x->literal);
        break;
      case INCREMARK_INLINE_TEXT:
      case INCREMARK_INLINE_CODE:
      case INCREMARK_INLINE_FORMULA:
      case INCREMARK_INLINE_HTML:
        if (x->literal) cmark_strbuf_puts(buf, x->literal);
        break;
      default:
        if (x->n_children > 0) {
          render_inlines_plain(x->children, x->n_children, buf);
        } else if (x->literal) {
          cmark_strbuf_puts(buf, x->literal);
        }
        break;
    }
  }
}

static void render_blocks_plain(incremark_block **blocks, int n, cmark_strbuf *buf) {
  for (int i = 0; i < n; i++) {
    incremark_block *b = blocks[i];
    if (b == NULL) continue;
    switch (b->type) {
      case INCREMARK_BLOCK_CODE_BLOCK:
      case INCREMARK_BLOCK_MATH_BLOCK:
      case INCREMARK_BLOCK_HTML_BLOCK:
        if (b->literal) cmark_strbuf_puts(buf, b->literal);
        break;
      case INCREMARK_BLOCK_TABLE:
        for (int r = 0; r < b->n_table; r++) {
          if (r > 0) cmark_strbuf_puts(buf, " | ");
          incremark_row *row = b->table[r];
          for (int c = 0; c < row->n_cells; c++) {
            if (c > 0) cmark_strbuf_puts(buf, " | ");
            render_inlines_plain(row->cells[c]->content, row->cells[c]->n_content, buf);
          }
        }
        break;
      case INCREMARK_BLOCK_IMAGE:
        if (b->url) cmark_strbuf_puts(buf, b->url);
        break;
      case INCREMARK_BLOCK_THEMATIC_BREAK:
        break;
      default:
        render_inlines_plain(b->content, b->n_content, buf);
        break;
    }
    cmark_strbuf_putc(buf, '\n');
  }
}

char *incremark_parser_render_plaintext(incremark_parser *p) {
  if (p == NULL) return im_strdup("");
  cmark_parser *parser = make_parser();
  cmark_parser_feed(parser, p->buf, p->len);
  cmark_node *doc = cmark_parser_finish(parser);

  block_vec out = {0};
  flatten_document(doc, &out);

  cmark_strbuf buf = CMARK_BUF_INIT(cmark_get_default_mem_allocator());
  render_blocks_plain(out.items, out.count, &buf);
  /* 这一版 cmark-gfm 没有 cmark_strbuf_str()，直接读结构体的 ptr/size。 */
  char *result = im_strndup((const char *)buf.ptr, (size_t)cmark_strbuf_len(&buf));

  cmark_strbuf_free(&buf);
  for (int i = 0; i < out.count; i++) free_block(out.items[i]);
  free(out.items);
  cmark_node_free(doc);
  cmark_parser_free(parser);
  return result;
}

char *incremark_parser_render_markdown(incremark_parser *p) {
  /* ima 侧此函数返回整篇 Markdown 原文；直接拷贝累积 buffer 即可满足语义。 */
  return incremark_parser_copy_buffer(p);
}

char *incremark_render_plaintext(const char *utf8, size_t len) {
  incremark_parser *p = incremark_parser_new();
  if (p == NULL) return im_strdup("");
  buffer_append(p, utf8 != NULL ? utf8 : "", len);
  char *r = incremark_parser_render_plaintext(p);
  incremark_parser_free(p);
  return r;
}

/* ==========================================================================
 * 调试名
 * ========================================================================== */

static const char *const BLOCK_NAMES[] = {
    "PARAGRAPH", "HEADING",     "CODE_BLOCK",   "MATH_BLOCK", "THEMATIC_BREAK",
    "TABLE",     "HTML_BLOCK",  "IMAGE",        "OTHER"};

static const char *const INLINE_NAMES[] = {
    "TEXT",      "SOFT_BREAK", "LINE_BREAK", "CODE",    "HTML",
    "EMPHASIS",  "STRONG",     "STRIKETHROUGH", "HIGHLIGHT", "UNDERLINE",
    "LINK",      "IMAGE",      "FORMULA",    "OTHER"};

const char *incremark_block_type_name(int type) {
  if (type < 0 || type > 8) return "OTHER";
  return BLOCK_NAMES[type];
}

const char *incremark_inline_type_name(int type) {
  if (type < 0 || type > 13) return "OTHER";
  return INLINE_NAMES[type];
}
