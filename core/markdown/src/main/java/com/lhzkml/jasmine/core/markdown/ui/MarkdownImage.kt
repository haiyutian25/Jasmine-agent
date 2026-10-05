package com.lhzkml.jasmine.core.markdown.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.lhzkml.jasmine.core.ui.theme.AppShapes
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.motion.ShimmerPlaceholder

/** 占位高度：图片自身比例要等解码完才知道，这里只保证"有块东西在那儿闪"，不假装知道尺寸。 */
private val MarkdownImageLoadingHeight = 160.dp

/**
 * markdown 图片 `![alt](url)`，由 Coil 加载。
 *
 * 宽度铺满容器、高度按图片自身比例 —— 与 markdown 的默认行为一致。
 *
 * 加载失败这里**不自己画兜底**：失败只登记地址（[markImageFailed]），由调用方在
 * 下一次重组时把整块换成原始源码 —— 这样「失败就显示完整原文」只有一处规则。
 *
 * 加载中显示 [ShimmerPlaceholder]（上游 ZCode 的 `markdown-image-loading-shimmer`：
 * `card → background → card` 三色微光、1.8s 线性无限）。
 * 为此把 `AsyncImage` 换成了 `SubcomposeAsyncImage` —— 只有它能拿到 loading 态；
 * 代价是 loading 期间多一层子组合，图片本身的加载路径与失败回调都没变。
 *
 * @param linkUrl 非空表示这张图被链接包裹（`[![alt](img)](url)`），点击打开它
 */
@Composable
internal fun MarkdownImage(
    url: String,
    alt: String,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    linkUrl: String? = null,
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = url,
        contentDescription = alt.ifEmpty { null },
        contentScale = ContentScale.FillWidth,
        onError = { markImageFailed(url) },
        loading = {
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MarkdownImageLoadingHeight),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .then(
                if (linkUrl.isNullOrBlank()) {
                    Modifier
                } else {
                    Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                        }
                    }
                }
            ),
    )
}
