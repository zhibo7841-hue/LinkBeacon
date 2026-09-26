package com.networktoolbox

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun savedReportIdSurvivesSaverAndBackKeepsHistoryCaller() {
        val original = AppNavigationState().selectTopLevel(TopLevelDestination.DEVICES)
            .openSecondaryDestination(ToolScreen.HISTORY)
            .openTool(ToolScreen.REPORT).copy(reportHistoryId = 42)
        val encoded = with(AppNavigationState.Saver) {
            SaverScope { true }.save(original)
        }
        val restored = requireNotNull(AppNavigationState.Saver.restore(requireNotNull(encoded)))
        assertEquals(42L, restored.reportHistoryId)
        assertEquals(ToolScreen.HISTORY, restored.goBack().toolScreen)
        assertEquals(null, restored.goBack().reportHistoryId)
        assertEquals(TopLevelDestination.DEVICES, restored.goBack().goBack().topLevelDestination)
        assertEquals(null, restored.selectTopLevel(TopLevelDestination.HOME).reportHistoryId)
    }

    private val toolScreens = listOf(
        ToolScreen.PING,
        ToolScreen.DNS,
        ToolScreen.TCP,
        ToolScreen.PORT_SCAN,
        ToolScreen.SUBNET,
        ToolScreen.TRACEROUTE,
        ToolScreen.TLS_CHECK,
        ToolScreen.WEBSITE_DIAGNOSTICS,
        ToolScreen.LAN_SCAN,
        ToolScreen.WIFI_ANALYZER,
        ToolScreen.REPORT,
        ToolScreen.HISTORY,
        ToolScreen.PRIVACY,
        ToolScreen.ABOUT,
    )

    @Test
    fun toolsOpenedFromHome_returnToHomeForEveryTool() {
        toolScreens.forEach { tool ->
            val state = AppNavigationState()
                .selectTopLevel(TopLevelDestination.HOME)
                .openTool(tool)

            assertEquals(TopLevelDestination.HOME, state.goBack().topLevelDestination)
            assertEquals(ToolScreen.NONE, state.goBack().toolScreen)
        }
    }

    @Test
    fun toolsOpenedFromTools_returnToToolsForEveryTool() {
        toolScreens.forEach { tool ->
            val state = AppNavigationState()
                .selectTopLevel(TopLevelDestination.TOOLS)
                .openTool(tool)

            assertEquals(TopLevelDestination.TOOLS, state.goBack().topLevelDestination)
            assertEquals(ToolScreen.NONE, state.goBack().toolScreen)
        }
    }

    @Test
    fun topLevelDestinations_areHomeToolsAndDevices() {
        assertEquals(
            listOf(R.string.shell_home, R.string.shell_tools, R.string.shell_devices),
            TopLevelDestination.entries.map(TopLevelDestination::labelRes),
        )
        assertEquals(3, TopLevelDestination.entries.size)
        assertEquals(null, TopLevelDestination.entries.find { it.name == "SETTINGS" })
    }

    @Test
    fun drawerDestinationsAreSecondaryAndBackReturnsToItsCaller() {
        listOf(
            TopLevelDestination.HOME,
            TopLevelDestination.TOOLS,
            TopLevelDestination.DEVICES,
        ).forEach { caller ->
            listOf(ToolScreen.HISTORY, ToolScreen.SETTINGS, ToolScreen.PRIVACY, ToolScreen.ABOUT).forEach { screen ->
                val state = AppNavigationState()
                    .selectTopLevel(caller)
                    .openSecondaryDestination(screen)

                assertEquals(screen, state.toolScreen)
                val returned = state.goBack()
                assertEquals(caller, returned.topLevelDestination)
                assertEquals(ToolScreen.NONE, returned.toolScreen)
                assertEquals(ToolScreen.NONE, returned.toolBackDestination)
            }
        }
    }

    @Test
    fun nonDrawerHistoryBack_returnsToCallerWithoutReopeningDrawer() {
        val returned = AppNavigationState()
            .selectTopLevel(TopLevelDestination.TOOLS)
            .openTool(ToolScreen.HISTORY)
            .goBack()

        assertEquals(TopLevelDestination.TOOLS, returned.topLevelDestination)
        assertEquals(ToolScreen.NONE, returned.toolBackDestination)
    }

    @Test
    fun drawerIsAvailableOnlyForTopLevelDestinations() {
        assertEquals(true, AppShellPresentation.canShowDrawer(AppNavigationState()))
        assertEquals(
            false,
            AppShellPresentation.canShowDrawer(AppNavigationState().openTool(ToolScreen.PING)),
        )
        assertEquals(
            false,
            AppShellPresentation.canShowDrawer(
                AppNavigationState().openSecondaryDestination(ToolScreen.PRIVACY),
            ),
        )
        assertEquals(
            listOf(R.string.shell_history, R.string.shell_settings, R.string.shell_privacy, R.string.shell_about),
            AppShellPresentation.drawerItemLabels(),
        )
    }

    @Test
    fun reportOpenedFromHistory_preservesOriginalHomeOrigin() {
        val state = AppNavigationState()
            .openTool(ToolScreen.HISTORY)
            .openTool(ToolScreen.REPORT)

        val history = state.goBack()
        assertEquals(ToolScreen.HISTORY, history.toolScreen)
        assertEquals(ToolScreen.NONE, history.toolBackDestination)
        assertEquals(TopLevelDestination.HOME, history.goBack().topLevelDestination)
    }

    @Test
    fun reportOpenedFromHistoryPreservesOriginalToolsOrigin() {
        val state = AppNavigationState()
            .selectTopLevel(TopLevelDestination.TOOLS)
            .openTool(ToolScreen.HISTORY)
            .openTool(ToolScreen.REPORT)

        val history = state.goBack()
        assertEquals(ToolScreen.HISTORY, history.toolScreen)
        assertEquals(TopLevelDestination.TOOLS, history.goBack().topLevelDestination)
    }

    @Test
    fun selectingBottomTab_closesToolAndDoesNotCreateBackStack() {
        val state = AppNavigationState()
            .openTool(ToolScreen.PING)
            .selectTopLevel(TopLevelDestination.DEVICES)

        assertEquals(TopLevelDestination.DEVICES, state.topLevelDestination)
        assertEquals(ToolScreen.NONE, state.toolScreen)
        assertEquals(state, state.goBack())
    }

    @Test
    fun deviceDetail_isADevicesSecondaryRouteAndBackReturnsToDevices() {
        val state = AppNavigationState().openDeviceDetail("observed:key:10.0.1.20")

        assertEquals(TopLevelDestination.DEVICES, state.topLevelDestination)
        assertEquals(ToolScreen.DEVICE_DETAIL, state.toolScreen)
        assertEquals("observed:key:10.0.1.20", state.deviceDetailKey)

        val returned = state.goBack()
        assertEquals(TopLevelDestination.DEVICES, returned.topLevelDestination)
        assertEquals(ToolScreen.NONE, returned.toolScreen)
        assertEquals(null, returned.deviceDetailKey)
    }

    @Test
    fun deviceDetailRoute_survivesSaveableRestore() {
        val state = AppNavigationState()
            .selectTopLevel(TopLevelDestination.DEVICES)
            .openDeviceDetail("favorite:scope:type:value")

        val saverScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(AppNavigationState.Saver) {
            with(saverScope) { save(state) }
        }
        val restored = AppNavigationState.Saver.restore(saved!!)

        assertEquals(state, restored)
    }

    @Test
    fun deviceProfileEdit_returnsToTheSameDeviceDetail() {
        val detailKey = "favorite:scope:type:value"
        val edit = AppNavigationState()
            .openDeviceDetail(detailKey)
            .openDeviceProfileEdit()

        assertEquals(TopLevelDestination.DEVICES, edit.topLevelDestination)
        assertEquals(ToolScreen.DEVICE_PROFILE_EDIT, edit.toolScreen)
        assertEquals(ToolScreen.DEVICE_DETAIL, edit.toolBackDestination)
        assertEquals(detailKey, edit.deviceDetailKey)

        val returned = edit.goBack()
        assertEquals(ToolScreen.DEVICE_DETAIL, returned.toolScreen)
        assertEquals(detailKey, returned.deviceDetailKey)
    }

    @Test
    fun existingToolsOpenedFromDeviceDetail_returnToTheSameDetail() {
        listOf(ToolScreen.PING, ToolScreen.TCP, ToolScreen.PORT_SCAN).forEach { tool ->
            val detailKey = "favorite:scope:type:value"
            val state = AppNavigationState()
                .openDeviceDetail(detailKey)
                .openToolFromDeviceDetail(
                    screen = tool,
                    detailKey = detailKey,
                    initialTarget = "10.0.1.10",
                )

            assertEquals(TopLevelDestination.DEVICES, state.topLevelDestination)
            assertEquals(tool, state.toolScreen)
            assertEquals(ToolScreen.DEVICE_DETAIL, state.toolBackDestination)
            assertEquals("10.0.1.10", state.toolInitialTarget)

            val returned = state.goBack()
            assertEquals(TopLevelDestination.DEVICES, returned.topLevelDestination)
            assertEquals(ToolScreen.DEVICE_DETAIL, returned.toolScreen)
            assertEquals(detailKey, returned.deviceDetailKey)
            assertEquals(ToolScreen.NONE, returned.toolBackDestination)
            assertEquals(null, returned.toolInitialTarget)
        }
    }

    @Test
    fun portScanOpenedFromDeviceDetail_preservesCallerTargetAndLastObservedSource() {
        val detailKey = "favorite:scope:type:value"
        val state = AppNavigationState()
            .openDeviceDetail(detailKey)
            .openToolFromDeviceDetail(
                screen = ToolScreen.PORT_SCAN,
                detailKey = detailKey,
                initialTarget = "10.0.1.80",
                targetSource = NavigationTargetSource.LAST_OBSERVED_ADDRESS,
            )

        assertEquals(ToolScreen.PORT_SCAN, state.toolScreen)
        assertEquals("10.0.1.80", state.toolInitialTarget)
        assertEquals(NavigationTargetSource.LAST_OBSERVED_ADDRESS, state.toolTargetSource)
        assertEquals(ToolScreen.DEVICE_DETAIL, state.goBack().toolScreen)
        assertEquals(detailKey, state.goBack().deviceDetailKey)
    }

    @Test
    fun portScanNavigationMetadata_survivesSaveableRestore() {
        val state = AppNavigationState()
            .openDeviceDetail("observed:10.0.1.10")
            .openToolFromDeviceDetail(
                ToolScreen.PORT_SCAN,
                "observed:10.0.1.10",
                "10.0.1.10",
                NavigationTargetSource.CURRENT_ADDRESS,
            )
        val saved = with(AppNavigationState.Saver) { SaverScope { true }.save(state) }
        val restored = requireNotNull(AppNavigationState.Saver.restore(requireNotNull(saved)))

        assertEquals(state, restored)
    }

    @Test
    fun openingAnotherDestination_clearsDeviceDetailKey() {
        val state = AppNavigationState()
            .openDeviceDetail("favorite:scope:type:value")
            .openTool(ToolScreen.PING)

        assertEquals(null, state.deviceDetailKey)
    }

    @Test
    fun backPolicy_prioritizesDrawerOverSearchAndNavigation() {
        assertEquals(
            AppBackAction.DISMISS_DRAWER,
            AppShellPresentation.resolveBackAction(
                drawerOpen = true,
                deviceCenterVisible = true,
                deviceCenterSearchActive = true,
                hasNestedDestination = true,
            ),
        )
    }

    @Test
    fun backPolicy_closesDeviceCenterSearchBeforePageNavigation() {
        assertEquals(
            AppBackAction.CLOSE_DEVICE_CENTER_SEARCH,
            AppShellPresentation.resolveBackAction(
                drawerOpen = false,
                deviceCenterVisible = true,
                deviceCenterSearchActive = true,
                hasNestedDestination = false,
            ),
        )
    }

    @Test
    fun backPolicy_navigatesOnlyWhenThereIsANestedDestination() {
        assertEquals(
            AppBackAction.NAVIGATE,
            AppShellPresentation.resolveBackAction(
                drawerOpen = false,
                deviceCenterVisible = false,
                deviceCenterSearchActive = false,
                hasNestedDestination = true,
            ),
        )
        assertEquals(
            AppBackAction.UNHANDLED,
            AppShellPresentation.resolveBackAction(
                drawerOpen = false,
                deviceCenterVisible = true,
                deviceCenterSearchActive = false,
                hasNestedDestination = false,
            ),
        )
    }
}
