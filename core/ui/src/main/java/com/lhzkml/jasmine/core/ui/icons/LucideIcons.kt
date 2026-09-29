package com.lhzkml.jasmine.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 全工程共用的图标集：**全部来自 lucide**，与 ZCode 同一个图标库。
 *
 * 查证过 ZCode 的来源：它没有自己的图标资产（`packages/ui/src/components/icons` 里只有一个 Electron
 * 窗口按钮用的 `windowIcons.ts`），全篇 409 处 `from "lucide-react"`。所以这里不再用 Material 图标，
 * 每个图标都是**逐字照抄 lucide 的 path 数据**（24×24 视口、描边 2、圆头圆角、只描边不填充），颜色交
 * 给 `Icon` 的 tint —— 所以在任何主题下都与 ZCode 同形。
 *
 * 图标名与 lucide 的 kebab-case 名一一对应，需要新增时从
 * `https://unpkg.com/lucide-static@latest/icons/<名>.svg` 取原文，把 `<path>` / `<circle>` / `<rect>`
 * 的几何按下面的写法转过来（`<circle cx cy r>` 与 `<rect>` 的转换见本文件里的常量）。
 */
object LucideIcons {

    private const val ViewportSize = 24f
    private const val Stroke = 2f

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = ViewportSize.dp,
            defaultHeight = ViewportSize.dp,
            viewportWidth = ViewportSize,
            viewportHeight = ViewportSize,
        )
        paths.forEach { path ->
            builder.addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = Stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    // --- 几何小工具：把 lucide 的 <circle> / <rect> 写成 path（笔画形状，不是填充） ---

    /** 实心图标：与描边图标同一套 path，但同时填充 —— ZCode 的 `className="fill-current"` 就是这个效果。 */
    private fun filledIcon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = ViewportSize.dp,
            defaultHeight = ViewportSize.dp,
            viewportWidth = ViewportSize,
            viewportHeight = ViewportSize,
        )
        paths.forEach { path ->
            builder.addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = Stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    /** `<circle cx cy r>`（用两段对称的圆弧闭环）。 */
    private fun circle(cx: Float, cy: Float, r: Float, clockwise: Boolean = true): String {
        val sweep = if (clockwise) 1 else 0
        val d = "M${cx + r} $cy a$r $r 0 1 $sweep ${-2 * r} 0 a$r $r 0 1 $sweep ${2 * r} 0z"
        return d
    }

    /** `<rect x y width height rx>`（圆角矩形；lucide 的矩形圆角都是 2）。 */
    private fun roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} $r" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    val Plus: ImageVector by lazy { icon("Plus", "M5 12h14", "M12 5v14") }

    val ArrowLeft: ImageVector by lazy { icon("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5") }

    val ArrowRight: ImageVector by lazy { icon("ArrowRight", "M5 12h14", "m12 5 7 7-7 7") }

    val Sparkles: ImageVector by lazy {
        icon(
            "Sparkles",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1" +
                " 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2" +
                " 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            circle(4f, 20f, 2f),
        )
    }

    val ChartColumn: ImageVector by lazy {
        icon("ChartColumn", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")
    }

    val Check: ImageVector by lazy { icon("Check", "M20 6 9 17l-5-5") }

    val Close: ImageVector by lazy { icon("X", "M18 6 6 18", "m6 6 12 12") }

    val Cloud: ImageVector by lazy {
        icon("Cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")
    }

    val CloudDownload: ImageVector by lazy {
        icon(
            "CloudDownload",
            "M12 13v8l-4-4",
            "m12 21 4-4",
            "M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284",
        )
    }

    val Copy: ImageVector by lazy {
        icon("Copy", roundedRect(8f, 8f, 14f, 14f, 2f), "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    }

    val Contrast: ImageVector by lazy {
        icon("Contrast", circle(12f, 12f, 10f), "M12 18a6 6 0 0 0 0-12v12z")
    }

    val Moon: ImageVector by lazy {
        icon(
            "Moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344" +
                "-.215.825-.004.803.401",
        )
    }

    val Trash: ImageVector by lazy {
        icon(
            "Trash2",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    val Download: ImageVector by lazy {
        icon("Download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")
    }

    val Pencil: ImageVector by lazy {
        icon(
            "Pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0" +
                " .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val FormatSize: ImageVector by lazy {
        icon(
            "ALargeSmall",
            "m15 16 2.536-7.328a1.02 1.02 0 0 1 1.928 0L22 16",
            "M15.697 14h5.606",
            "m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16",
            "M3.304 13h6.392",
        )
    }

    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "m6 9 6 6 6-6") }

    val ChevronRight: ImageVector by lazy { icon("ChevronRight", "m9 18 6-6-6-6") }

    val Languages: ImageVector by lazy {
        icon(
            "Languages",
            "m5 8 6 6",
            "m4 14 6-6 2-3",
            "M2 5h12",
            "M7 2h1",
            "m22 22-5-10-5 10",
            "M14 18h6",
        )
    }

    val Sun: ImageVector by lazy {
        icon(
            "Sun",
            circle(12f, 12f, 4f),
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    val Menu: ImageVector by lazy { icon("Menu", "M4 5h16", "M4 12h16", "M4 19h16") }

    val Palette: ImageVector by lazy {
        icon(
            "Palette",
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1" +
                ".75 1.75 0 0 1-1.4 2.8z",
            circle(13.5f, 6.5f, 0.5f),
            circle(17.5f, 10.5f, 0.5f),
            circle(6.5f, 12.5f, 0.5f),
            circle(8.5f, 7.5f, 0.5f),
        )
    }

    val Refresh: ImageVector by lazy {
        icon(
            "RefreshCw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033" +
                " 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34" +
                " 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.8" +
                "31A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            circle(12f, 12f, 3f),
        )
    }

    val Bot: ImageVector by lazy {
        icon(
            "Bot",
            "M12 8V4H8",
            roundedRect(4f, 8f, 16f, 12f, 2f),
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2",
        )
    }

    val Terminal: ImageVector by lazy { icon("Terminal", "M12 19h8", "m4 17 6-6-6-6") }

    val Type: ImageVector by lazy {
        icon("Type", "M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6")
    }

    val Upload: ImageVector by lazy {
        icon("Upload", "M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
    }

    /** `arrow-up`：聊天输入行的发送按钮。 */
    val ArrowUp: ImageVector by lazy { icon("ArrowUp", "m5 12 7-7 7 7", "M12 19V5") }

    /** `play`：继续 / 重试按钮。 */
    val Play: ImageVector by lazy { icon("Play", "M6 3l14 9-14 9z") }

    /** `square`：停止按钮（描边版，留作备用）。 */
    val Square: ImageVector by lazy { icon("Square", roundedRect(3f, 3f, 18f, 18f, 2f)) }

    /**
     * `square`（**实心**）：停止按钮用的就是这个 —— ZCode 是
     * `<SquareIcon className="size-4 fill-current" />`，描边 + 填充同色，看起来是一整块实心方块。
     */
    val SquareFilled: ImageVector by lazy {
        filledIcon("SquareFilled", roundedRect(3f, 3f, 18f, 18f, 2f))
    }

    /** `square-terminal`：命令执行的工具卡（ZCode 的 `SquareTerminalIcon`）。 */
    val SquareTerminal: ImageVector by lazy {
        icon("SquareTerminal", "m7 11 2-2-2-2", "M11 13h4", roundedRect(3f, 3f, 18f, 18f, 2f))
    }

    /** `list-todo`：待办清单的工具卡（ZCode 的 `ListTodoIcon`）。 */
    val ListTodo: ImageVector by lazy {
        icon("ListTodo", "M13 5h8", "M13 12h8", "M13 19h8", "m3 17 2 2 4-4")
    }

    /** `wrench`：认不出类型的工具卡的兜底图标。 */
    val Wrench: ImageVector by lazy {
        icon(
            "Wrench",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6" +
                " 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12" +
                ".54.662.219.984z",
        )
    }

    /** `brain`：思考（深度思考 / 深度推理）块的图标。 */
    val Brain: ImageVector by lazy {
        icon(
            "Brain",
            "M12 18V5",
            "M15 13a4.17 4.17 0 0 1-3-4 4.17 4.17 0 0 1-3 4",
            "M17.598 6.5A3 3 0 1 0 12 5a3 3 0 1 0-5.598 1.5",
            "M17.997 5.125a4 4 0 0 1 2.526 5.77",
            "M18 18a4 4 0 0 0 2-7.464",
            "M19.967 17.483A4 4 0 1 1 12 18a4 4 0 1 1-7.967-.517",
            "M6 18a4 4 0 0 1-2-7.464",
            "M6.003 5.125a4 4 0 0 0-2.526 5.77",
        )
    }
}