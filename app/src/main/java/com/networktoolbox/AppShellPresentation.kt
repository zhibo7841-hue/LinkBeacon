package com.networktoolbox

internal enum class AppShellDrawerItem(val label: String) {
    HISTORY("检测历史"),
    PRIVACY("隐私与数据"),
    ABOUT("关于"),
}

internal data class AppDrawerHeader(
    val appName: String,
    val versionLabel: String,
    val logoForegroundResource: Int,
    val logoBackgroundResource: Int,
)

/**
 * Back decisions owned by the app shell. Dialogs are handled by their own
 * platform window before this activity-level policy is reached.
 */
internal enum class AppBackAction {
    DISMISS_DRAWER,
    CLOSE_DEVICE_CENTER_SEARCH,
    NAVIGATE,
    UNHANDLED,
}

/** Small app-shell contract shared by the drawer and navigation tests. */
internal object AppShellPresentation {
    val drawerItems = AppShellDrawerItem.entries.toList()

    fun drawerItemLabels(): List<String> = drawerItems.map(AppShellDrawerItem::label)

    fun versionLabel(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)

    fun drawerHeader(versionName: String?): AppDrawerHeader = AppDrawerHeader(
        appName = AppInformationPresentation.appName,
        versionLabel = versionLabel(versionName),
        logoForegroundResource = AboutIconPresentation.foregroundResource,
        logoBackgroundResource = AboutIconPresentation.backgroundResource,
    )

    fun canShowDrawer(state: AppNavigationState): Boolean =
        state.toolScreen == ToolScreen.NONE

    fun resolveBackAction(
        drawerOpen: Boolean,
        deviceCenterVisible: Boolean,
        deviceCenterSearchActive: Boolean,
        hasNestedDestination: Boolean,
    ): AppBackAction = when {
        drawerOpen -> AppBackAction.DISMISS_DRAWER
        deviceCenterVisible && deviceCenterSearchActive ->
            AppBackAction.CLOSE_DEVICE_CENTER_SEARCH
        hasNestedDestination -> AppBackAction.NAVIGATE
        else -> AppBackAction.UNHANDLED
    }
}
