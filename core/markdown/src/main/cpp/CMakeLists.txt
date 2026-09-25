# ============================================================================
# core:markdown 的 native 构建
#
# 组成（与 ima 的 libincremark_jni.so 同构）：
#   third_party/cmark-gfm/   —— 上游 cmark-gfm 原样 vendor（BSD-2-Clause，
#                               v0.29.0.gfm.13 = commit 499789b）
#   incremark/extensions/    —— 三个自研扩展（highlight / underline / fomula）
#   incremark/incremark.c    —— 自研增量层（稳定边界 + 扁平化 + Update）
#   incremark/incremark_jni.c—— JNI 入口
#
# 产物：libincremark_jni.so
# ============================================================================
cmake_minimum_required(VERSION 3.22.1)
project(incremark_jni C CXX)

set(CMAKE_C_STANDARD 11)

set(CMARK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/cmark-gfm)
set(INCREMARK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/incremark)

# ---------------------------------------------------------------------------
# 上游需要两个由 CMake 生成的配置头（模板在 src/ 下）。
# 版本取自 vendored 的 v0.29.0.gfm.13。
# ---------------------------------------------------------------------------
set(PROJECT_VERSION_MAJOR 0)
set(PROJECT_VERSION_MINOR 29)
set(PROJECT_VERSION_PATCH 0)
set(PROJECT_VERSION_GFM 13)

set(CMARK_GEN_DIR ${CMAKE_CURRENT_BINARY_DIR}/cmark_gen)
file(MAKE_DIRECTORY ${CMARK_GEN_DIR})

# config.h.in 用 #cmakedefine 引用这三个探测结果 —— 不设的话 config.h 里
# HAVE_STDBOOL_H 会保持未定义，导致 cmark 源码里的 true/false 编译不过
# （config.h 会退回 typedef char bool）。
# 上游用 check_include_file / check_c_source_compiles 探测；目标平台是
# Android + clang，三者必定成立，直接置 1 即可。
include(CheckIncludeFile)
check_include_file(stdbool.h HAVE_STDBOOL_H)
set(HAVE___BUILTIN_EXPECT 1)
set(HAVE___ATTRIBUTE__ 1)

configure_file(${CMARK_DIR}/src/config.h.in ${CMARK_GEN_DIR}/config.h)
configure_file(${CMARK_DIR}/src/cmark-gfm_version.h.in ${CMARK_GEN_DIR}/cmark-gfm_version.h)

# ---------------------------------------------------------------------------
# 源码清单
# ---------------------------------------------------------------------------
file(GLOB CMARK_SRC ${CMARK_DIR}/src/*.c)
# main.c 是 cmark 的命令行工具入口，不是库的一部分。
list(REMOVE_ITEM CMARK_SRC ${CMARK_DIR}/src/main.c)

file(GLOB CMARK_EXT_SRC ${CMARK_DIR}/extensions/*.c)

set(INCREMARK_SRC
    ${INCREMARK_DIR}/incremark.c
    ${INCREMARK_DIR}/extensions/highlight.c
    ${INCREMARK_DIR}/extensions/underline.c
    ${INCREMARK_DIR}/extensions/fomula.c
)

# ---------------------------------------------------------------------------
# 依赖解析：上游引用头文件的方式是 #include "..."，所以要同时给
#   src/（源码头）与 ${CMARK_GEN_DIR}（生成头）
# 扩展源码用 #include <cmark-gfm.h> 这类尖括号形式，同样两条 include 路径覆盖。
# ---------------------------------------------------------------------------
set(CMARK_INCLUDE_DIRS
    ${CMARK_DIR}/src
    ${CMARK_DIR}/extensions
    ${CMARK_GEN_DIR}
)

add_library(cmark-gfm STATIC ${CMARK_SRC} ${CMARK_EXT_SRC})

# ---------------------------------------------------------------------------
# 上游的 src/CMakeLists.txt 用 GenerateExportHeader 生成 cmark-gfm_export.h
# （提供 CMARK_GFM_EXPORT 宏）。这里复刻同一步，否则 src/cmark-gfm.h 找不到它。
#
# 静态库分支要求定义 CMARK_GFM_STATIC_DEFINE（不是 CMARK_STATIC_DEFINE），
# 否则宏会展开成 __attribute__((visibility("default")))。
# ---------------------------------------------------------------------------
include(GenerateExportHeader)
generate_export_header(cmark-gfm BASE_NAME cmark-gfm)

target_include_directories(cmark-gfm PUBLIC ${CMARK_INCLUDE_DIRS} ${CMAKE_CURRENT_BINARY_DIR})
target_compile_definitions(cmark-gfm PRIVATE CMARK_GFM_STATIC_DEFINE=1)
# 上游用了 GNU 扩展（typeof、语句表达式等），保持宽松。
target_compile_options(cmark-gfm PRIVATE -Wno-unused-function -Wno-unused-variable)

add_library(incremark STATIC ${INCREMARK_SRC})
target_include_directories(incremark PUBLIC ${CMARK_INCLUDE_DIRS})
target_include_directories(incremark PRIVATE ${INCREMARK_DIR})
target_compile_definitions(incremark PRIVATE CMARK_STATIC_DEFINE=1)
target_link_libraries(incremark PUBLIC cmark-gfm)

# ---------------------------------------------------------------------------
# JNI 共享库
# ---------------------------------------------------------------------------
add_library(incremark_jni SHARED ${INCREMARK_DIR}/incremark_jni.c)
target_include_directories(incremark_jni PRIVATE ${INCREMARK_DIR} ${CMARK_INCLUDE_DIRS})
target_link_libraries(incremark_jni PRIVATE incremark cmark-gfm)

# ima 的 libincremark_jni.so 导出的正是「cmark-gfm 公开 API + 自研 incremark_*
# + 9 个 JNI 入口」（见 verification/01_dynsym_exports.txt：38 incremark_* /
# 8 create_*_extension / 14 cmark_gfm_* / 9 Java_* / 6 _scan_* / JNI_OnLoad）。
# cmark 的内部函数都是 static，本来就不可见。
#
# ⚠️ 不要加 -Wl,--exclude-libs,ALL —— 它会把静态库里的符号全部隐藏，
#    连带 incremark_* 一起消失。
target_link_options(incremark_jni PRIVATE "-Wl,--gc-sections")
set_target_properties(incremark_jni PROPERTIES
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# ============================================================================
# MicroTeX —— 数学公式渲染（开源，MIT）
#
# 来源：
#   https://github.com/NanoMichael/AndroidLaTeXMath  （Android 绑定 + JNI 胶水）
#   https://github.com/NanoMichael/cLaTeXMath       （C++ 核心，MicroTeX 的旧名）
#
# 上游用 ndk-build（jni/Android.mk：递归收所有 .cpp + APP_STL=c++_static）。
# 这里做等价的 CMake 翻译 —— 源文件按上游那样递归收集，编译选项照抄。
#
# 产物：libtex.so（与 ima 的同名库对应）
# ============================================================================
set(TEX_DIR ${CMAKE_CURRENT_SOURCE_DIR}/tex)

file(GLOB_RECURSE TEX_SRC
    ${TEX_DIR}/port/*.cpp
    ${TEX_DIR}/microtex/src/*.cpp
)

add_library(tex SHARED ${TEX_SRC})

target_include_directories(tex PRIVATE
    ${TEX_DIR}/microtex/src
    ${TEX_DIR}/port
)

# ---------------------------------------------------------------------------
# 编译选项（每一项都在真机上验过，别照抄上游 Android.mk）
#
# ⚠️ **不要用 -Os / -O2**：`LaTeX.nParse` 会 SIGSEGV（初始化正常、一 parse 就崩，
#    fault addr 0x100000008）。实测 -Os、-Os -fno-strict-aliasing、-Os + sections
#    三种组合全崩；-O0 与 -O1 都正常。省下的体积远不如下面两项符号裁剪多，
#    所以用 -O1 换稳定。
#
# ✅ 安全的体积优化（9.63 MB → 8.18 MB，省 1.45 MB）：
#    - -ffunction-sections -fdata-sections + -Wl,--gc-sections：裁掉未引用的 section
#    - CXX_VISIBILITY_PRESET hidden：收窄动态符号表（JNI_OnLoad 带 JNIEXPORT，
#      会覆盖 hidden，所以 System.loadLibrary 不受影响 —— 见 jni_def.cpp:68）
# ---------------------------------------------------------------------------
target_compile_options(tex PRIVATE
    -fexceptions
    -frtti
    -O1
    -ffunction-sections
    -fdata-sections
)
target_link_options(tex PRIVATE "-Wl,--gc-sections")
target_link_libraries(tex PRIVATE log m)

set_target_properties(tex PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

set_target_properties(tex PROPERTIES
    CXX_STANDARD 11
    CXX_STANDARD_REQUIRED ON
)
