package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton

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
    onOpenPolicy: (String) -> PrivacyPolicyOpenResult,
    onCopyPolicy: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    var feedback by rememberSaveable { mutableStateOf(PrivacyPolicyFeedback.NONE) }
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
            HorizontalDivider()
            InformationBlock(
                title = stringResource(R.string.info_wifi_title),
                description = stringResource(R.string.info_wifi_description),
            )
            HorizontalDivider()
            InformationBlock(
                title = stringResource(R.string.info_share_title),
                description = stringResource(R.string.info_share_description),
            )
            HorizontalDivider()
            InformationBlock(
                title = stringResource(R.string.info_policy_title),
                description = stringResource(R.string.info_policy_description),
            )
            PrimaryActionButton(
                onClick = {
                    feedback = when (onOpenPolicy(PrivacyPolicyUrls.from(resources))) {
                        PrivacyPolicyOpenResult.OPENED -> PrivacyPolicyFeedback.NONE
                        PrivacyPolicyOpenResult.NO_BROWSER -> PrivacyPolicyFeedback.NO_BROWSER
                        PrivacyPolicyOpenResult.FAILED -> PrivacyPolicyFeedback.OPEN_FAILED
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("privacy_open_policy"),
            ) { Text(stringResource(R.string.info_policy_open)) }
            SecondaryActionButton(
                onClick = {
                    feedback = if (onCopyPolicy(PrivacyPolicyUrls.from(resources))) {
                        PrivacyPolicyFeedback.COPIED
                    } else PrivacyPolicyFeedback.COPY_FAILED
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("privacy_copy_policy"),
            ) { Text(stringResource(R.string.info_policy_copy)) }
            val feedbackResource = when (feedback) {
                PrivacyPolicyFeedback.NONE -> null
                PrivacyPolicyFeedback.NO_BROWSER -> R.string.info_policy_no_browser
                PrivacyPolicyFeedback.OPEN_FAILED -> R.string.info_policy_open_failed
                PrivacyPolicyFeedback.COPIED -> R.string.info_policy_copied
                PrivacyPolicyFeedback.COPY_FAILED -> R.string.info_policy_copy_failed
            }
            feedbackResource?.let {
                Text(
                    text = stringResource(it),
                    modifier = Modifier.testTag("privacy_policy_feedback").semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (feedback == PrivacyPolicyFeedback.COPIED) {
                        MaterialTheme.colorScheme.onSurface
                    } else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.info_policy_url),
                modifier = Modifier.testTag("privacy_policy_url"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.info_policy_contact),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
