package com.lhzkml.jasmine.feature.main.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.R
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.text.Text

// ── Sidebar content dimensions ─────────────────────────────────────────

/** Inner padding of the sidebar content column. */
private val SidebarContentPaddingHorizontal = 16.dp
private val SidebarContentPaddingVertical = 14.dp

/** Settings entry: icon size. */
private val SidebarSettingsIconSize = 32.dp

/** 「新建对话」按钮：图标尺寸 / 内边距，以及与列表之间的间距。 */
private val SidebarNewConversationIconSize = 16.dp
private val SidebarNewConversationPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val SidebarNewConversationGap = 8.dp

/** 历史对话行：字号沿用聊天区的正文 / 说明两级。 */
private val SidebarConversationTitleFontSize = 13.5.sp
private val SidebarConversationMetaFontSize = 11.sp
private val SidebarConversationRowPaddingVertical = 11.dp
private val SidebarConversationDeleteIconSize = 16.dp

/**
 * 侧边栏里的一条历史对话。中性类型，不依赖 core:data 的 `Conversation`，由调用方映射后传进来。
 *
 * @param subtitle 次要说明，例如产生这条对话的模型名
 */
data class SidebarConversation(
    val id: String,
    val title: String,
    val subtitle: String,
)

/**
 * 侧边栏内容：顶部「新建对话」+ 历史对话列表 + 底部设置入口。
 *
 * 抽屉容器（推挤式 + 手势）由 core:widgets 的 `DismissibleNavigationDrawer` 提供，这里只负责
 * 「内容长什么样」。按钮一律用 core:widgets 的自有组件（`Button` / `TextButton` / `IconButton`），
 * 不再用 core:ui 的旧 Button，所以历史行的按下涟漪是 M3 `TextButton` 自带的整行涟漪。
 *
 * The former workspace/brand header was intentionally removed.
 */
@Composable
fun AppSidebarContent(
    currentTheme: CssVariables,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    conversations: List<SidebarConversation> = emptyList(),
    activeConversationId: String? = null,
    onNewConversation: () -> Unit = {},
    onConversationSelected: (String) -> Unit = {},
    onConversationDeleted: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = SidebarContentPaddingHorizontal, vertical = SidebarContentPaddingVertical)
            .testTag("app_sidebar_drawer")
    ) {
        // 1. 新建对话（放在历史列表顶部）
        Button(
            onClick = {
                onNewConversation()
                onCloseDrawer()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sidebar_new_conversation_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = currentTheme.subtleSurface,
                contentColor = currentTheme.foreground,
            ),
            contentPadding = SidebarNewConversationPadding,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LucideIcons.Plus,
                    contentDescription = null,
                    tint = currentTheme.foreground,
                    modifier = Modifier.size(SidebarNewConversationIconSize)
                )
                Spacer(modifier = Modifier.width(SidebarNewConversationGap))
                Text(
                    text = stringResource(R.string.sidebar_new_conversation),
                    fontSize = SidebarConversationTitleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.foreground
                )
            }
        }

        Spacer(modifier = Modifier.height(SidebarNewConversationGap))

        // 2. 历史对话列表：占满剩余高度（空态也在这块里），超出可滚。
        //    设置入口因此始终贴在底部。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.sidebar_history_empty),
                    fontSize = SidebarConversationMetaFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                conversations.forEach { conversation ->
                    SidebarConversationRow(
                        conversation = conversation,
                        isCurrent = conversation.id == activeConversationId,
                        currentTheme = currentTheme,
                        onSelect = {
                            onConversationSelected(conversation.id)
                            onCloseDrawer()
                        },
                        onDelete = { onConversationDeleted(conversation.id) },
                    )
                }
            }
        }

        // 3. Settings Entry (moved from the top nav bar, pinned to the bottom,
        // aligned to the right edge). Bare icon button (no chrome).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = {
                    onOpenSettings()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("sidebar_settings_btn")
            ) {
                Icon(
                    imageVector = LucideIcons.Settings,
                    contentDescription = stringResource(R.string.sidebar_cd_settings),
                    tint = currentTheme.foreground,
                    modifier = Modifier.size(SidebarSettingsIconSize)
                )
            }
        }
    }
}

/**
 * 历史对话行：标题 + 次要说明（产生它的模型），右侧删除按钮。
 * 当前对话用 primary 色 + SemiBold 标出 —— 与原来 BottomSheet 里的行保持一致。
 */
@Composable
private fun SidebarConversationRow(
    conversation: SidebarConversation,
    isCurrent: Boolean,
    currentTheme: CssVariables,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sidebar_conversation_${conversation.id}")
            // indication = null：去掉按下的涟漪（阴影），与删除图标/设置按钮的无涟漪观感一致。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onSelect,
            )
            .padding(vertical = SidebarConversationRowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                fontSize = SidebarConversationTitleFontSize,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) currentTheme.primary else currentTheme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = conversation.subtitle,
                fontSize = SidebarConversationMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(40.dp)
                .testTag("sidebar_conversation_delete_${conversation.id}")
        ) {
            Icon(
                imageVector = LucideIcons.Trash,
                contentDescription = stringResource(R.string.sidebar_cd_delete_conversation),
                tint = currentTheme.mutedForeground,
                modifier = Modifier.size(SidebarConversationDeleteIconSize)
            )
        }
    }
}
