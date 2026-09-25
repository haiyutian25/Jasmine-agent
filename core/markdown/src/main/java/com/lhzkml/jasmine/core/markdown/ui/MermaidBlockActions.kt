package com.lhzkml.jasmine.core.markdown.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * 图表块 tab 栏右侧那两个动作的实现。
 *
 * 对应 ima 的 `FencedCodeTabBar`（源码 `FencedCodeBlockComposable.kt:411`）：
 * 代码页给「复制」，图片页给「保存图片」。ima 的图标是 `code_copy_icon` / `std_ic_download`，
 * 我们用等价的 Material 图标。
 */

/** 剪贴板条目的标签，仅用于系统剪贴板面板上的显示。 */
private const val CLIPBOARD_LABEL = "code"

/** 保存到相册时的子目录与文件名前缀。 */
private const val ALBUM_DIR = "Jasmine"
private const val FILE_PREFIX = "mermaid_"

/** 把代码/图表源码复制到系统剪贴板。 */
internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, text))
}

/** 保存图片是否还需要运行时授权 —— 只有 API 26-28 需要（29 起是分区存储）。 */
internal fun needsStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
    return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
}

/**
 * 把渲染好的位图存进系统相册；成功返回 true。
 *
 * 两条路径，因为分区存储是 API 29 才有的：
 *
 * - **API 29+**：交给 MediaStore，用 `RELATIVE_PATH` 指定目录、`IS_PENDING` 标记写入中。
 *   全程不碰文件路径，也就不需要任何存储权限。
 * - **API 26-28**：只能自己写进公共 `Pictures/Jasmine`，再通知媒体扫描，否则相册里
 *   看不到这张图。这一步需要 WRITE_EXTERNAL_STORAGE —— 调用方必须先过
 *   [needsStoragePermission]。
 *
 * 文件名带时间戳，同一张图存两次不会互相覆盖。
 */
internal fun saveMermaidImage(context: Context, bitmap: Bitmap): Boolean = try {
    val name = FILE_PREFIX + System.currentTimeMillis() + ".png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveViaMediaStore(context, bitmap, name)
    } else {
        saveViaPublicDir(context, bitmap, name)
    }
} catch (e: Exception) {
    false
}

/** API 29+：MediaStore 直接收，不需要权限。 */
private fun saveViaMediaStore(context: Context, bitmap: Bitmap, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM_DIR)
        // 写入期间先标记为 PENDING，避免相册读到半张图。
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    resolver.openOutputStream(uri)?.use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
    } ?: return false
    // 写完清掉 PENDING，这张图才对其它应用可见。
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return true
}

/** API 26-28：写公共目录 + 通知媒体扫描（需要 WRITE_EXTERNAL_STORAGE）。 */
private fun saveViaPublicDir(context: Context, bitmap: Bitmap, name: String): Boolean {
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_DIR)
    if (!dir.exists() && !dir.mkdirs()) return false
    val file = File(dir, name)
    FileOutputStream(file).use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
    }
    // 不扫描的话，相册和图库都看不到这张文件。
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    return true
}
