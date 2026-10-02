package com.lhzkml.jasmine.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation contract of the settings feature.
 *
 * Other modules only ever depend on this sealed hierarchy (never on the
 * implementation module) when they need to navigate into the settings flow.
 */
@Serializable
sealed interface SettingsNavKey : NavKey {

    /** Settings menu list (entry of the settings flow). */
    @Serializable
    data object SettingsMenu : SettingsNavKey

    /** Appearance settings (color mode + palette presets). */
    @Serializable
    data object AppearanceSettings : SettingsNavKey

    /** Language settings. */
    @Serializable
    data object LanguageSettings : SettingsNavKey

    /** Font settings (typography engine + custom fonts). */
    @Serializable
    data object FontSettings : SettingsNavKey

    /** Font-size adjustment. */
    @Serializable
    data object FontSizeSettings : SettingsNavKey

    /** Usage statistics: what the app has spent, and on which providers and models. */
    @Serializable
    data object UsageStats : SettingsNavKey

    /**
     * **行为与权限** 设置页：模型侧的行为控制（眼下是"模型回复语言"，往后放工具权限、审核这些）。
     */
    @Serializable
    data object BehaviourAndPermissions : SettingsNavKey

    /**
     * **调试** 页：组件层（`core:widgets`）的独立试验场，只用来在真机上预览自建按钮的真实效果，
     * 不读任何业务状态、也不改任何东西。
     */
    @Serializable
    data object Debug : SettingsNavKey

    /**
     * **顶部栏变体预览** 页：`variant` 取 [SettingsDebugTopAppBarVariant] 里的常量。
     *
     * 这一页也是组件层的独立试验场：它自己的顶栏**就是被测的那个组件**，所以刻意不套设置流那套
     * `SettingsPage` 顶栏，也不参与 app 现有的顶部导航。
     */
    @Serializable
    data class DebugTopAppBar(val variant: String) : SettingsNavKey

    /**
     * **侧边栏变体预览** 页：`variant` 取 [SettingsDebugSidebarVariant] 里的常量。
     *
     * 与 [DebugTopAppBar] 同一套做法：页面自己的顶栏就是组件本身，不套设置流那套顶栏。
     */
    @Serializable
    data class DebugSidebar(val variant: String) : SettingsNavKey

    /**
     * **底部导航栏预览** 页：组件层（`core:widgets`）标准底栏的独立试验场，同样不套设置流那套顶栏。
     */
    @Serializable
    data object DebugBottomBar : SettingsNavKey

    /**
     * **滑块预览** 页：组件层（`core:widgets`）标准滑块（单值）的独立试验场。
     */
    @Serializable
    data object DebugSlider : SettingsNavKey

    /**
     * **搜索框变体预览** 页：`variant` 取 [SettingsDebugSearchBarVariant] 里的常量。
     *
     * 与 [DebugTopAppBar] 同一套做法：页面自己就是组件层的独立试验场 —— 页内顶栏是我们的小号
     * `TopAppBar`，被测的搜索框紧贴其下；展开态那两档进来就是展开状态。
     */
    @Serializable
    data class DebugSearchBar(val variant: String) : SettingsNavKey

    /**
     * **FAB 菜单预览** 页：`variant` 取 [SettingsDebugFabMenuVariant] 里的常量。
     *
     * 同样是组件层的独立试验场：页内顶栏是我们的小号 `TopAppBar`，右下角是被测的 FAB 菜单。
     */
    @Serializable
    data class DebugFabMenu(val variant: String) : SettingsNavKey

    /**
     * **对话框预览** 页：`variant` 取 [SettingsDebugDialogVariant] 里的常量。
     *
     * 同样是组件层的独立试验场：页内顶栏是我们的小号 `TopAppBar`，进来对话框就是打开的。
     */
    @Serializable
    data class DebugDialog(val variant: String) : SettingsNavKey

    /**
     * **徽标预览** 页：`variant` 取 [SettingsDebugBadgeVariant] 里的常量。
     *
     * 同样是组件层的独立试验场：页内顶栏是我们的小号 `TopAppBar`，中间是挂在图标上的徽标。
     */
    @Serializable
    data class DebugBadge(val variant: String) : SettingsNavKey

    /**
     * **复选框预览** 页：`variant` 取 [SettingsDebugCheckboxVariant] 里的常量。
     *
     * 同样是组件层的独立试验场：页内顶栏是我们的小号 `TopAppBar`，正文是几个可点的复选框。
     */
    @Serializable
    data class DebugCheckbox(val variant: String) : SettingsNavKey
}

/** [SettingsNavKey.DebugSidebar] 的 `variant` 取值。 */
object SettingsDebugSidebarVariant {
    /** 可推开抽屉（内容被挤开）。 */
    const val DISMISSIBLE_DRAWER = "dismissibleDrawer"

    /** 常驻抽屉（大屏）。 */
    const val PERMANENT_DRAWER = "permanentDrawer"
}

/** [SettingsNavKey.DebugTopAppBar] 的 `variant` 取值。 */
object SettingsDebugTopAppBarVariant {
    /** 小号顶部栏（左对齐）。 */
    const val SMALL = "small"

    /** 小号顶部栏（标题居中）。 */
    const val CENTER_ALIGNED = "centerAligned"
}

/** [SettingsNavKey.DebugSearchBar] 的 `variant` 取值。 */
object SettingsDebugSearchBarVariant {
    /** 折叠态：基础搜索栏（`SearchBar(state, …)`），配全屏展开。 */
    const val SEARCH_BAR = "searchBar"

    /** 展开态：全屏（`ExpandedFullScreenSearchBar`，走 `BasicEdgeToEdgeDialog` + 预测返回）。 */
    const val EXPANDED_FULL_SCREEN = "expandedFullScreen"

    /** 展开态：停靠（`ExpandedDockedSearchBar`，走 `Popup`）。 */
    const val EXPANDED_DOCKED = "expandedDocked"
}

/** [SettingsNavKey.DebugFabMenu] 的 `variant` 取值（只影响按钮本体，菜单条目固定 56dp）。 */
object SettingsDebugFabMenuVariant {
    /** 基准档：按钮 56dp（跟随基准档 FAB）。 */
    const val BASELINE = "baseline"

    /** 中号：按钮 80dp。 */
    const val MEDIUM = "medium"

    /** 大号：按钮 96dp。 */
    const val LARGE = "large"
}

/** [SettingsNavKey.DebugDialog] 的 `variant` 取值。 */
object SettingsDebugDialogVariant {
    /** 基础档：`BasicAlertDialog` + 调用方自定内容。 */
    const val BASIC = "basic"

    /** 经典档：`AlertDialog`（图标 + 标题 + 正文 + 两个按钮）。 */
    const val CLASSIC = "classic"

    /** 经典档·长文案：标题/正文/按钮都拉长，用来看按钮自动换行。 */
    const val CLASSIC_LONG = "classicLong"
}

/** [SettingsNavKey.DebugBadge] 的 `variant` 取值。 */
object SettingsDebugBadgeVariant {
    /** 小圆点：无内容，6dp。 */
    const val DOT = "dot"

    /** 一位数字：16dp 高、随文字撑宽的胶囊。 */
    const val COUNT = "count"

    /** 三位数字：同上，但更宽。 */
    const val LONG_COUNT = "longCount"
}

/** [SettingsNavKey.DebugCheckbox] 的 `variant` 取值。 */
object SettingsDebugCheckboxVariant {
    /** 两态：勾选 / 未勾选 / 禁用（含禁用+勾选）。 */
    const val TWO_STATE = "twoState"

    /** 三态：父项 + 子项（点父项循环切换，点子项会回到"部分选中"）。 */
    const val TRI_STATE = "triState"

    /** 自定义配色：`CheckboxDefaults.colors(...)`。 */
    const val COLORS = "colors"
}
