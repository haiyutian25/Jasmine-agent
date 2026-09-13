package com.lhzkml.jasmine.core.data.model

/**
 * A downloadable preset font hosted on GitHub Releases
 * (https://github.com/haiyutian25/jasmine-fonts).
 *
 * [sha256] is the expected checksum of the downloaded bytes; the downloader
 * verifies it after transfer and rejects a mismatching file (e.g. tampered or
 * truncated) before installing it.
 */
data class PresetFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Static catalog of preset fonts published as GitHub Release assets (tag v1.0).
 * Downstream can download them over HTTPS without authentication via the
 * `releases/latest/download/<file>` direct links.
 */
object PresetFontCatalog {
    private const val BASE_URL =
        "https://github.com/haiyutian25/jasmine-fonts/releases/latest/download"

    val ALL: List<PresetFont> = listOf(
        PresetFont(
            id = "preset-source-han-serif",
            displayName = "Source Han Serif SC",
            fileName = "SourceHanSerifSC-Regular.otf",
            downloadUrl = "$BASE_URL/SourceHanSerifSC-Regular.otf",
            sizeBytes = 24_543_332L,
            sha256 = "78aa7a328fd974df2d688c8a9fd74a33d8334dfa84ab24d9d11efb2ffc464117",
        ),
        PresetFont(
            id = "preset-lxgw-wenkai",
            displayName = "LXGW WenKai",
            fileName = "LXGWWenKai-Regular.ttf",
            downloadUrl = "$BASE_URL/LXGWWenKai-Regular.ttf",
            sizeBytes = 25_575_676L,
            sha256 = "39ad71264b588165b469e35e6afb162a378dacd1f95348160240ba9038ac3009",
        ),
        PresetFont(
            id = "preset-jetbrains-mono",
            displayName = "JetBrains Mono",
            fileName = "JetBrainsMono-Regular.ttf",
            downloadUrl = "$BASE_URL/JetBrainsMono-Regular.ttf",
            sizeBytes = 273_900L,
            sha256 = "a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f",
        ),
    )

    fun findByFileName(fileName: String): PresetFont? = ALL.firstOrNull { it.fileName == fileName }
}

/**
 * A font installed on the device (a downloaded preset or a user import).
 * [id] is the on-disk file name and uniquely identifies the font.
 */
data class InstalledFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val isPreset: Boolean,
    val sizeBytes: Long,
)
