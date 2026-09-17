package com.networktoolbox

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.OutlinedNetworkCard

@Composable
internal fun LanguageSettingsScreen(onBack: () -> Unit) {
    val configuration = LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // UI projection refreshed after configuration change AND return from system Settings.
    val tags = remember(configuration, revision) { AppCompatDelegate.getApplicationLocales().toLanguageTags() }
    val selected = AppLanguage.fromLanguageTags(tags)
    var showDialog by remember { mutableStateOf(false) }
    ToolScreenLayout {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back))
            }
            Text(stringResource(R.string.shell_settings), style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedNetworkCard(
            modifier = Modifier.clickable(role = Role.Button) { showDialog = true },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Text(
                    selected?.let { stringResource(it.labelRes()) } ?: tags,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showDialog) AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected == language, role = Role.RadioButton, onClick = {
                                showDialog = false
                                val locales = if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(language.languageTags)
                                AppCompatDelegate.setApplicationLocales(locales)
                                revision++
                            }).padding(horizontal = NetworkToolboxSpacing.SM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
                    ) {
                        RadioButton(selected = selected == language, onClick = null)
                        Text(stringResource(language.labelRes()))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private fun AppLanguage.labelRes() = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_chinese
    AppLanguage.ENGLISH -> R.string.language_english
}
