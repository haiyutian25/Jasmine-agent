# 第三方代码与许可声明

本仓库包含来自第三方的源代码。按各自许可的要求，在此集中声明；许可证全文副本见
[`licenses/`](licenses/) 目录。

---

## 1. AndroidX Material 组件库（Apache-2.0）

### 范围

`core:widgets` 模块（`core/widgets/src/main/`）中的 **80 个 Kotlin 文件**是从 AndroidX 的
Material 组件库（`androidx.compose.material3`，含 `material3-expressive` 的稳定子集）移植而来的，
覆盖 31 个包，主要分布：

| 包 | 文件数 | 包 | 文件数 |
|---|---|---|---|
| `bottomsheet` | 15 | `internal` | 4 |
| `ripple` | 8 | `button` | 3 |
| `textfield` | 7 | `navigation` | 3 |
| `progress` | 6 | `slider` | 2 |
| `tokens` | 5 | `tabs` | 2 |
| `menu` | 5 | `motion` | 2 |
| 其余 19 个包各 1 个 | 19 | | |

（完整清单：在这些文件的头部都保留了 `Copyright (20xx) The Android Open Source Project` 与
Apache-2.0 许可头。）

### 版权

```
Copyright (C) The Android Open Source Project
```

### 许可

Apache License, Version 2.0 —— 全文见 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)，
亦可从 <http://www.apache.org/licenses/LICENSE-2.0> 获取。

### 修改说明

依 Apache-2.0 第 4(b) 条，**每个被修改的文件都已在其版权头下方就地标注**，形式为一行中文说明：
"本项目自有的组件代码（移植自上游…后自行维护），不再跟随上游生成，可直接改。"

主要修改内容：

1. **去掉对 `androidx.compose.material3` 的运行时依赖**：所有颜色/形状/排版取值改为读本项目的自有
   设计令牌（`core:ui` 的 `CssVariables` / `AppShapes` / `AppTypography`），不再经 Material 的
   `ColorScheme` / `Shapes` / `Typography` 或组件令牌键映射。
2. **命名空间与包名改为本项目**（`com.lhzkml.jasmine.core.widgets.*`），并对不符合本仓库约定的
   命名、注释与文档做了整理。
3. **子集裁剪与合并**：部分组件按本项目需要裁掉了未使用的重载、内部工具与实验性 API；另有个别
   组件在两个文件之间做过合并（例如 navigation 的内部几何工具）。
4. **行为等价的局部修正**：例如 `Surface` / `ProvideContentColorTextStyle` 的内容色回落采用
   `takeOrElse { LocalWidgetsContentColor.current }`、`Scaffold` 自带内部 inset 实现等；
   `core:widgets` 的令牌表（`tokens/`）按上游全量槽位保留，颜色槽改为直读自有主题。

按 Apache-2.0 第 4(a) 条，随附的 [Apache-2.0 全文](licenses/Apache-2.0.txt) 已包含在本仓库中。

---

## 2. 其它随仓库分发的第三方内容

这些内容自带许可文件或随构建产物分发，此处仅作指引：

| 内容 | 位置 | 许可 |
|---|---|---|
| MicroTeX（LaTeX 公式渲染，C++/C） | `core/markdown/src/main/cpp/tex/` | 见该目录内的许可文件（含 `License_for_dsrom.txt` 等） |
| mermaid（图表渲染，打包后的 JS） | `core/markdown/src/main/assets/mermaid/` | MIT（随打包产物附带的许可声明见产物内注释） |
| Lucide 图标（自带的图标几何数据） | `core/ui/src/main/java/com/lhzkml/jasmine/core/ui/icons/` | ISC |
| Rust 依赖（`Cargo.toml` 声明的 crates） | `rust/` | 各 crates 自带（多为 MIT / Apache-2.0） |
| Gradle/Compose/AndroidX 等构建与运行时依赖 | 各 `build.gradle.kts` 与 `gradle/libs.versions.toml` | 见各构件内 `META-INF` |

---

## 3. 本仓库自身的许可

**尚未声明。** 本文件只覆盖上述第三方内容；本仓库自身代码（除上表列出的第三方部分）的许可
需由仓库所有者确定后另行添加 `LICENSE`。
