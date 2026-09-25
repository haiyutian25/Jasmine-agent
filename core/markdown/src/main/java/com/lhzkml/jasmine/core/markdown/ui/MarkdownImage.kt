package com.lhzkml.jasmine.core.markdown.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.lhzkml.jasmine.core.ui.theme.CssVariables

/**
 * markdown 图片 `![alt](url)`，由 Coil 加载。
 *
 * 宽度铺满容器、高度按图片自身比例 —— 与 markdown 的默认行为一致。
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
    AsyncImage(
        model = url,
        contentDescription = alt.ifEmpty { null },
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(currentTheme.radiusSm))
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
