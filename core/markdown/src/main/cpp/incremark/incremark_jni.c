/*
 * incremark_jni.c —— JNI 入口（自研）
 * ============================================================================
 * 与 ima 的 libincremark_jni.so 对应：那边是 9 个
 * `Java_com_tencent_incremark_NativeBridge_*` 导出（见 .dynsym），
 * 这里是同样 9 个、同样签名的函数，只是包名换成 jasmin 自己的。
 *
 *   nativeNew               -> incremark_parser_new
 *   nativeFree              -> incremark_parser_free
 *   nativeReset             -> incremark_parser_reset
 *   nativeAppend            -> incremark_parser_append
 *   nativeFinalize          -> incremark_parser_finalize
 *   nativeCopyBuffer        -> incremark_parser_copy_buffer
 *   nativeRenderMarkdown    -> incremark_parser_render_markdown
 *   nativeRenderPlaintext   -> incremark_parser_render_plaintext
 *   nativeRenderPlaintextOf -> incremark_render_plaintext
 *
 * native -> Java 的构造走 IncremarkBridge 的 @JvmStatic 工厂方法
 * （makeUpdate / makeBlock / makeInline / makePrefix / makeTableRow / makeTableCell），
 * 与 ima 的 com.tencent.incremark.IncremarkBridge 一一对应 —— 那边用数组参数是因为
 * JNI 构造数组比构造 List 方便，这里沿用同一约定。
 * ============================================================================
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "incremark.h"

#define JNI_FN(name)                                                          \
  JNIEXPORT jstring JNICALL Java_com_lhzkml_jasmine_core_markdown_NativeBridge_##name

/* 缓存的 Java 侧类/方法句柄 —— 首次调用时解析，之后复用。 */
typedef struct {
  jclass bridge;      /* IncremarkBridge */
  jmethodID makeUpdate;
  jmethodID makeBlock;
  jmethodID makeInline;
  jmethodID makePrefix;
  jmethodID makeTableRow;
  jmethodID makeTableCell;
  jclass inlineClass;
  jclass blockClass;
  jclass tableRowClass;
  jclass tableCellClass;
  jclass prefixClass;
  jclass updateClass;
} jni_cache;

static jni_cache g_cache;
static int g_cache_ok = 0;

static jclass find_class(JNIEnv *env, const char *name) {
  jclass c = (*env)->FindClass(env, name);
  return c;
}

static int ensure_cache(JNIEnv *env) {
  if (g_cache_ok) return 1;
  const char *PKG = "com/lhzkml/jasmine/core/markdown";

  g_cache.bridge = find_class(env, "com/lhzkml/jasmine/core/markdown/IncremarkBridge");
  if (g_cache.bridge == NULL) return 0;

  g_cache.makeUpdate = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makeUpdate",
      "(IZI[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownBlock;)"
      "Lcom/lhzkml/jasmine/core/markdown/model/MarkdownUpdate;");
  g_cache.makeBlock = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makeBlock",
      "(Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;"
      "Ljava/lang/String;Z[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownInline;"
      "[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownTableRow;"
      "[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownPrefixContext;)"
      "Lcom/lhzkml/jasmine/core/markdown/model/MarkdownBlock;");
  g_cache.makeInline = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makeInline",
      "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;"
      "[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownInline;)"
      "Lcom/lhzkml/jasmine/core/markdown/model/MarkdownInline;");
  g_cache.makePrefix = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makePrefix",
      "(IZZZIZ)Lcom/lhzkml/jasmine/core/markdown/model/MarkdownPrefixContext;");
  g_cache.makeTableRow = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makeTableRow",
      "(Z[Lcom/lhzkml/jasmine/core/markdown/model/MarkdownTableCell;)"
      "Lcom/lhzkml/jasmine/core/markdown/model/MarkdownTableRow;");
  g_cache.makeTableCell = (*env)->GetStaticMethodID(
      env, g_cache.bridge, "makeTableCell",
      "([Lcom/lhzkml/jasmine/core/markdown/model/MarkdownInline;I)"
      "Lcom/lhzkml/jasmine/core/markdown/model/MarkdownTableCell;");

  g_cache.inlineClass = find_class(env, "com/lhzkml/jasmine/core/markdown/model/MarkdownInline");
  g_cache.blockClass = find_class(env, "com/lhzkml/jasmine/core/markdown/model/MarkdownBlock");
  g_cache.tableRowClass = find_class(env, "com/lhzkml/jasmine/core/markdown/model/MarkdownTableRow");
  g_cache.tableCellClass = find_class(env, "com/lhzkml/jasmine/core/markdown/model/MarkdownTableCell");
  g_cache.prefixClass = find_class(env, "com/lhzkml/jasmine/core/markdown/model/MarkdownPrefixContext");

  if (g_cache.makeUpdate == NULL || g_cache.makeBlock == NULL ||
      g_cache.makeInline == NULL || g_cache.makePrefix == NULL ||
      g_cache.makeTableRow == NULL || g_cache.makeTableCell == NULL ||
      g_cache.inlineClass == NULL || g_cache.blockClass == NULL ||
      g_cache.tableRowClass == NULL || g_cache.tableCellClass == NULL ||
      g_cache.prefixClass == NULL) {
    return 0;
  }
  (void)PKG;
  g_cache_ok = 1;
  return 1;
}

/* ---------- native -> Java 对象构造 ---------- */

static jobjectArray build_inlines(JNIEnv *env, incremark_inline **arr, int n);

static jobject make_inline(JNIEnv *env, incremark_inline *x) {
  jobjectArray children = build_inlines(env, x->children, x->n_children);
  jstring literal = x->literal ? (*env)->NewStringUTF(env, x->literal) : NULL;
  jstring url = x->url ? (*env)->NewStringUTF(env, x->url) : NULL;
  jstring title = x->title ? (*env)->NewStringUTF(env, x->title) : NULL;
  return (*env)->CallStaticObjectMethod(env, g_cache.bridge, g_cache.makeInline,
                                       (jint)x->type, literal, url, title, children);
}

static jobjectArray build_inlines(JNIEnv *env, incremark_inline **arr, int n) {
  jobjectArray ja = (*env)->NewObjectArray(env, (jsize)n, g_cache.inlineClass, NULL);
  if (ja == NULL) return NULL;
  for (int i = 0; i < n; i++) {
    jobject o = make_inline(env, arr[i]);
    (*env)->SetObjectArrayElement(env, ja, (jsize)i, o);
    if (o != NULL) (*env)->DeleteLocalRef(env, o);
  }
  return ja;
}

static jobjectArray build_prefixes(JNIEnv *env, incremark_prefix **arr, int n) {
  jobjectArray ja = (*env)->NewObjectArray(env, (jsize)n, g_cache.prefixClass, NULL);
  if (ja == NULL) return NULL;
  for (int i = 0; i < n; i++) {
    incremark_prefix *p = arr[i];
    jobject o = (*env)->CallStaticObjectMethod(
        env, g_cache.bridge, g_cache.makePrefix, (jint)p->container_type,
        (jboolean)p->show_list_marker, (jboolean)p->show_quote_marker,
        (jboolean)p->is_end_block, (jint)p->number_list_index,
        (jboolean)p->task_list_checked);
    (*env)->SetObjectArrayElement(env, ja, (jsize)i, o);
    if (o != NULL) (*env)->DeleteLocalRef(env, o);
  }
  return ja;
}

static jobjectArray build_cells(JNIEnv *env, incremark_cell **arr, int n) {
  jobjectArray ja = (*env)->NewObjectArray(env, (jsize)n, g_cache.tableCellClass, NULL);
  if (ja == NULL) return NULL;
  for (int i = 0; i < n; i++) {
    incremark_cell *c = arr[i];
    jobjectArray content = build_inlines(env, c->content, c->n_content);
    jobject o = (*env)->CallStaticObjectMethod(env, g_cache.bridge,
                                              g_cache.makeTableCell, content,
                                              (jint)c->alignment);
    (*env)->SetObjectArrayElement(env, ja, (jsize)i, o);
    if (content != NULL) (*env)->DeleteLocalRef(env, content);
    if (o != NULL) (*env)->DeleteLocalRef(env, o);
  }
  return ja;
}

static jobjectArray build_rows(JNIEnv *env, incremark_row **arr, int n) {
  jobjectArray ja = (*env)->NewObjectArray(env, (jsize)n, g_cache.tableRowClass, NULL);
  if (ja == NULL) return NULL;
  for (int i = 0; i < n; i++) {
    incremark_row *r = arr[i];
    jobjectArray cells = build_cells(env, r->cells, r->n_cells);
    jobject o = (*env)->CallStaticObjectMethod(env, g_cache.bridge,
                                              g_cache.makeTableRow,
                                              (jboolean)r->is_header, cells);
    (*env)->SetObjectArrayElement(env, ja, (jsize)i, o);
    if (cells != NULL) (*env)->DeleteLocalRef(env, cells);
    if (o != NULL) (*env)->DeleteLocalRef(env, o);
  }
  return ja;
}

static jobject make_block(JNIEnv *env, incremark_block *b) {
  jstring id = (*env)->NewStringUTF(env, b->id != NULL ? b->id : "");
  jstring fence = (*env)->NewStringUTF(env, b->fence_info != NULL ? b->fence_info : "");
  jstring literal = (*env)->NewStringUTF(env, b->literal != NULL ? b->literal : "");
  jstring url = (*env)->NewStringUTF(env, b->url != NULL ? b->url : "");
  jstring title = (*env)->NewStringUTF(env, b->title != NULL ? b->title : "");

  jobjectArray content = build_inlines(env, b->content, b->n_content);
  jobjectArray table = build_rows(env, b->table, b->n_table);
  jobjectArray prefix = build_prefixes(env, b->prefix, b->n_prefix);

  jobject o = (*env)->CallStaticObjectMethod(
      env, g_cache.bridge, g_cache.makeBlock, id, (jint)b->type,
      (jint)b->heading_level, fence, literal, url, title, (jboolean)b->is_closed,
      content, table, prefix);

  if (content != NULL) (*env)->DeleteLocalRef(env, content);
  if (table != NULL) (*env)->DeleteLocalRef(env, table);
  if (prefix != NULL) (*env)->DeleteLocalRef(env, prefix);
  return o;
}

static jobject make_update(JNIEnv *env, incremark_update *u) {
  if (u == NULL) return NULL;
  jobjectArray blocks = (*env)->NewObjectArray(env, (jsize)u->n_blocks,
                                              g_cache.blockClass, NULL);
  for (int i = 0; i < u->n_blocks; i++) {
    jobject o = make_block(env, u->blocks[i]);
    (*env)->SetObjectArrayElement(env, blocks, (jsize)i, o);
    if (o != NULL) (*env)->DeleteLocalRef(env, o);
  }
  jobject result = (*env)->CallStaticObjectMethod(
      env, g_cache.bridge, g_cache.makeUpdate, (jint)u->index,
      (jboolean)u->advanced, (jint)u->newly_completed_count, blocks);
  (*env)->DeleteLocalRef(env, blocks);
  return result;
}

static incremark_parser *as_parser(jlong handle) {
  return (incremark_parser *)(intptr_t)handle;
}

/* ==========================================================================
 * 9 个 JNI 入口
 * ========================================================================== */

JNIEXPORT jlong JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeNew(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;
  return (jlong)(intptr_t)incremark_parser_new();
}

JNIEXPORT void JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeFree(JNIEnv *env, jobject thiz,
                                                             jlong handle) {
  (void)env;
  (void)thiz;
  incremark_parser_free(as_parser(handle));
}

JNIEXPORT void JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeReset(JNIEnv *env, jobject thiz,
                                                              jlong handle) {
  (void)env;
  (void)thiz;
  incremark_parser_reset(as_parser(handle));
}

JNIEXPORT jobject JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeAppend(JNIEnv *env, jobject thiz,
                                                                jlong handle,
                                                                jbyteArray utf8) {
  (void)thiz;
  if (!ensure_cache(env)) return NULL;
  incremark_parser *p = as_parser(handle);
  if (p == NULL || utf8 == NULL) return NULL;

  jsize len = (*env)->GetArrayLength(env, utf8);
  jbyte *bytes = (*env)->GetByteArrayElements(env, utf8, NULL);
  incremark_update *u = incremark_parser_append(p, (const char *)bytes, (size_t)len);
  (*env)->ReleaseByteArrayElements(env, utf8, bytes, JNI_ABORT);

  jobject result = make_update(env, u);
  incremark_update_free(u);
  return result;
}

JNIEXPORT jobject JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeFinalize(JNIEnv *env, jobject thiz,
                                                                  jlong handle) {
  (void)thiz;
  if (!ensure_cache(env)) return NULL;
  incremark_parser *p = as_parser(handle);
  if (p == NULL) return NULL;
  incremark_update *u = incremark_parser_finalize(p);
  jobject result = make_update(env, u);
  incremark_update_free(u);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeCopyBuffer(JNIEnv *env,
                                                                    jobject thiz,
                                                                    jlong handle) {
  (void)thiz;
  incremark_parser *p = as_parser(handle);
  char *s = incremark_parser_copy_buffer(p);
  jstring result = (*env)->NewStringUTF(env, s != NULL ? s : "");
  incremark_string_free(s);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeRenderMarkdown(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jlong handle) {
  (void)thiz;
  incremark_parser *p = as_parser(handle);
  char *s = incremark_parser_render_markdown(p);
  jstring result = (*env)->NewStringUTF(env, s != NULL ? s : "");
  incremark_string_free(s);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeRenderPlainText(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jlong handle) {
  (void)thiz;
  incremark_parser *p = as_parser(handle);
  char *s = incremark_parser_render_plaintext(p);
  jstring result = (*env)->NewStringUTF(env, s != NULL ? s : "");
  incremark_string_free(s);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_core_markdown_NativeBridge_nativeRenderPlainTextOf(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jbyteArray utf8) {
  (void)thiz;
  if (utf8 == NULL) return (*env)->NewStringUTF(env, "");
  jsize len = (*env)->GetArrayLength(env, utf8);
  jbyte *bytes = (*env)->GetByteArrayElements(env, utf8, NULL);
  char *s = incremark_render_plaintext((const char *)bytes, (size_t)len);
  (*env)->ReleaseByteArrayElements(env, utf8, bytes, JNI_ABORT);
  jstring result = (*env)->NewStringUTF(env, s != NULL ? s : "");
  incremark_string_free(s);
  return result;
}

/* ==========================================================================
 * JNI_OnLoad：把回调类的 Class 引用转成全局引用，避免被 GC。
 * ========================================================================== */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;
  JNIEnv *env = NULL;
  if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  if (ensure_cache(env)) {
    g_cache.bridge = (jclass)(*env)->NewGlobalRef(env, g_cache.bridge);
    g_cache.inlineClass = (jclass)(*env)->NewGlobalRef(env, g_cache.inlineClass);
    g_cache.blockClass = (jclass)(*env)->NewGlobalRef(env, g_cache.blockClass);
    g_cache.tableRowClass = (jclass)(*env)->NewGlobalRef(env, g_cache.tableRowClass);
    g_cache.tableCellClass = (jclass)(*env)->NewGlobalRef(env, g_cache.tableCellClass);
    g_cache.prefixClass = (jclass)(*env)->NewGlobalRef(env, g_cache.prefixClass);
  }
  return JNI_VERSION_1_6;
}
