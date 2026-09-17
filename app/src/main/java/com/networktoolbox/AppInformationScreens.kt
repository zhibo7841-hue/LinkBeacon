package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        SecondaryInformationHeader(
            title = stringResource(R.string.shell_about),
            onBack = onBack,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NetworkToolboxSpacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
            ) {
                AppBrandLogo(modifier = Modifier.size(56.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
                ) {
                    Text(
                        AppInformationPresentation.appName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(AppInformationPresentation.appDescription),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        AppInformationPresentation.brandByline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            InformationRow(
                title = stringResource(AppInformationPresentation.versionTitle),
                supportingText = stringResource(AppInformationPresentation.versionSupport),
                value = stringResource(R.string.app_ui_version, BuildConfig.VERSION_NAME),
            )
        }
    }
}

@Composable
internal fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        SecondaryInformationHeader(
            title = stringResource(R.string.shell_privacy),
            onBack = onBack,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
        ) {
            InformationBlock(
                title = stringResource(AppInformationPresentation.localFirstTitle),
                description = stringResource(AppInformationPresentation.localFirstDescription),
            )
            HorizontalDivider()
            InformationBlock(
                title = stringResource(AppInformationPresentation.uploadTitle),
                description = stringResource(AppInformationPresentation.uploadDescription),
            )
            HorizontalDivider()
            InformationBlock(
                title = stringResource(AppInformationPresentation.accountTitle),
                description = stringResource(AppInformationPresentation.accountDescription),
            )
        }
    }
}

@Composable
private fun InformationBlock(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InformationRow(
    title: String,
    supportingText: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
