package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing

@Composable
internal fun AppShellDrawer(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenAbout = onOpenAbout,
                )
            }
        },
        content = content,
    )
}

@Composable
private fun AppDrawerContent(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = NetworkToolboxSpacing.XL,
                top = NetworkToolboxSpacing.LG,
                end = NetworkToolboxSpacing.XL,
                bottom = NetworkToolboxSpacing.XXL,
            ),
        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        val header = AppShellPresentation.drawerHeader(BuildConfig.VERSION_NAME)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
        ) {
            AppBrandLogo(
                modifier = Modifier.size(NetworkToolboxSpacing.XXL + NetworkToolboxSpacing.SM),
                shape = RoundedCornerShape(12.dp),
                foregroundResource = header.logoForegroundResource,
                backgroundResource = header.logoBackgroundResource,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Text(
                    header.appName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.app_ui_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(NetworkToolboxSpacing.SM))
        HorizontalDivider()
        AppShellPresentation.drawerItems.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.labelRes)) },
                selected = false,
                onClick = {
                    when (item) {
                        AppShellDrawerItem.HISTORY -> onOpenHistory()
                        AppShellDrawerItem.SETTINGS -> onOpenSettings()
                        AppShellDrawerItem.PRIVACY -> onOpenPrivacy()
                        AppShellDrawerItem.ABOUT -> onOpenAbout()
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon(),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun AppShellDrawerItem.icon() = when (this) {
    AppShellDrawerItem.HISTORY -> Icons.Outlined.History
    AppShellDrawerItem.SETTINGS -> Icons.Outlined.Settings
    AppShellDrawerItem.PRIVACY -> Icons.Outlined.Lock
    AppShellDrawerItem.ABOUT -> Icons.Outlined.Info
}
