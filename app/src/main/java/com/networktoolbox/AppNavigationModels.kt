package com.networktoolbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class NavigationOrigin {
    HOME,
    TOOLS,
    DEVICES,
}

internal enum class TopLevelDestination(
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.shell_home, Icons.Outlined.Home),
    TOOLS(R.string.shell_tools, Icons.Outlined.Build),
    DEVICES(R.string.shell_devices, Icons.Outlined.Lan),
}

internal enum class ToolScreen {
    NONE,
    SUBNET,
    PING,
    DNS,
    TCP,
    PORT_SCAN,
    TRACEROUTE,
    TLS_CHECK,
    WEBSITE_DIAGNOSTICS,
    WEB_DIAGNOSTICS_HISTORY,
    REPORT,
    HISTORY,
    SETTINGS,
    PRIVACY,
    ABOUT,
    LAN_SCAN,
    WIFI_ANALYZER,
    DEVICE_DETAIL,
    DEVICE_PROFILE_EDIT,
}

internal enum class NavigationTargetSource {
    NONE,
    CURRENT_ADDRESS,
    LAST_OBSERVED_ADDRESS,
}

internal fun TopLevelDestination.navigationOrigin(): NavigationOrigin = when (this) {
    TopLevelDestination.HOME -> NavigationOrigin.HOME
    TopLevelDestination.TOOLS -> NavigationOrigin.TOOLS
    TopLevelDestination.DEVICES -> NavigationOrigin.DEVICES
}

internal fun NavigationOrigin.backDestination(): TopLevelDestination = when (this) {
    NavigationOrigin.HOME -> TopLevelDestination.HOME
    NavigationOrigin.TOOLS -> TopLevelDestination.TOOLS
    NavigationOrigin.DEVICES -> TopLevelDestination.DEVICES
}

/**
 * The app shell has one tool surface with source-aware top-level callers.
 * Keeping the origin beside the tool destination prevents each feature screen
 * from inventing its own back behavior.
 */
internal data class AppNavigationState(
    val topLevelDestination: TopLevelDestination = TopLevelDestination.HOME,
    val toolScreen: ToolScreen = ToolScreen.NONE,
    val toolOrigin: NavigationOrigin = NavigationOrigin.HOME,
    val toolBackDestination: ToolScreen = ToolScreen.NONE,
    val deviceDetailKey: String? = null,
    /** A one-time target supplied by a Device Detail tool entry. */
    val toolInitialTarget: String? = null,
    val toolTargetSource: NavigationTargetSource = NavigationTargetSource.NONE,
    /** Only an ID is saved; the original snapshot is read from History. */
    val reportHistoryId: Long? = null,
) {
    fun openTool(screen: ToolScreen): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.TOOLS,
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        toolBackDestination = toolScreen.takeIf { it != ToolScreen.NONE } ?: ToolScreen.NONE,
        deviceDetailKey = null,
        toolInitialTarget = null,
        toolTargetSource = NavigationTargetSource.NONE,
        reportHistoryId = null,
    )

    fun openSecondaryDestination(screen: ToolScreen): AppNavigationState = copy(
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        toolBackDestination = ToolScreen.NONE,
        deviceDetailKey = null,
        toolInitialTarget = null,
        toolTargetSource = NavigationTargetSource.NONE,
        reportHistoryId = null,
    )

    fun openDeviceDetail(key: String): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.DEVICES,
        toolScreen = ToolScreen.DEVICE_DETAIL,
        toolOrigin = NavigationOrigin.DEVICES,
        toolBackDestination = ToolScreen.NONE,
        deviceDetailKey = key,
        toolInitialTarget = null,
        toolTargetSource = NavigationTargetSource.NONE,
        reportHistoryId = null,
    )

    fun openDeviceProfileEdit(): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.DEVICES,
        toolScreen = ToolScreen.DEVICE_PROFILE_EDIT,
        toolOrigin = NavigationOrigin.DEVICES,
        toolBackDestination = ToolScreen.DEVICE_DETAIL,
        toolInitialTarget = null,
        toolTargetSource = NavigationTargetSource.NONE,
        reportHistoryId = null,
    )

    /** Opens an existing tool with a transient target and a Device Detail back path. */
    fun openToolFromDeviceDetail(
        screen: ToolScreen,
        detailKey: String,
        initialTarget: String,
        targetSource: NavigationTargetSource = NavigationTargetSource.CURRENT_ADDRESS,
    ): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.DEVICES,
        toolScreen = screen,
        toolOrigin = NavigationOrigin.DEVICES,
        toolBackDestination = ToolScreen.DEVICE_DETAIL,
        deviceDetailKey = detailKey,
        toolInitialTarget = initialTarget,
        toolTargetSource = targetSource,
        reportHistoryId = null,
    )

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState = copy(
        topLevelDestination = destination,
        toolScreen = ToolScreen.NONE,
        toolBackDestination = ToolScreen.NONE,
        deviceDetailKey = null,
        toolInitialTarget = null,
        toolTargetSource = NavigationTargetSource.NONE,
        reportHistoryId = null,
    )

    fun goBack(): AppNavigationState = when {
        toolScreen == ToolScreen.NONE -> this
        toolScreen == ToolScreen.DEVICE_DETAIL -> copy(
            topLevelDestination = TopLevelDestination.DEVICES,
            toolScreen = ToolScreen.NONE,
            toolBackDestination = ToolScreen.NONE,
            deviceDetailKey = null,
            toolInitialTarget = null,
            toolTargetSource = NavigationTargetSource.NONE,
            reportHistoryId = null,
        )
        toolBackDestination != ToolScreen.NONE -> copy(
            toolScreen = toolBackDestination,
            toolBackDestination = ToolScreen.NONE,
            deviceDetailKey = deviceDetailKey.takeIf { toolBackDestination == ToolScreen.DEVICE_DETAIL },
            toolInitialTarget = null,
            toolTargetSource = NavigationTargetSource.NONE,
            reportHistoryId = null,
        )
        else -> copy(
            topLevelDestination = toolOrigin.backDestination(),
            toolScreen = ToolScreen.NONE,
            toolBackDestination = ToolScreen.NONE,
            deviceDetailKey = null,
            toolInitialTarget = null,
            toolTargetSource = NavigationTargetSource.NONE,
            reportHistoryId = null,
        )
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.topLevelDestination.name,
                    state.toolScreen.name,
                    state.toolOrigin.name,
                    state.toolBackDestination.name,
                    state.deviceDetailKey,
                    state.toolInitialTarget,
                    state.reportHistoryId,
                    state.toolTargetSource.name,
                )
            },
            restore = { saved ->
                AppNavigationState(
                    topLevelDestination = TopLevelDestination.valueOf(saved[0] as String),
                    toolScreen = ToolScreen.valueOf(saved[1] as String),
                    toolOrigin = NavigationOrigin.valueOf(saved[2] as String),
                    // Slot 3 used to hold the Drawer source in the 063-B
                    // save format. Unknown values (including DRAWER) safely
                    // restore as no nested tool destination.
                    toolBackDestination = (saved.getOrNull(3) as? String)
                        ?.let { value -> runCatching { ToolScreen.valueOf(value) }.getOrNull() }
                        ?: ToolScreen.NONE,
                    deviceDetailKey = saved.getOrNull(4) as? String,
                    toolInitialTarget = saved.getOrNull(5) as? String,
                    reportHistoryId = saved.getOrNull(6) as? Long,
                    toolTargetSource = (saved.getOrNull(7) as? String)
                        ?.let { value -> runCatching { NavigationTargetSource.valueOf(value) }.getOrNull() }
                        ?: NavigationTargetSource.NONE,
                )
            },
        )
    }
}
