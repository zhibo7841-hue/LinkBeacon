package com.networktoolbox.feature.dns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.networktoolbox.feature.dns.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.feature.dns.domain.IpAddressClassification
import com.networktoolbox.feature.dns.domain.IpAddressKind
import com.networktoolbox.feature.dns.presentation.DnsStatus
import com.networktoolbox.feature.dns.presentation.DnsUiState

@Composable
fun DnsScreen(
    uiState: DnsUiState,
    onDomainChanged: (String) -> Unit,
    onLookup: () -> Unit,
    onAdvancedSettingsToggle: () -> Unit,
    onRecordTypeToggle: (DnsRecordType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState.status is DnsStatus.Loading
    val isInvalidInput = uiState.status.isInvalidInput()

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.dns_title),
            description = null,
            icon = Icons.Outlined.Dns,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
            backEnabled = !isLoading,
        )

        ToolInputSection(title = stringResource(R.string.dns_domain)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.domainInput,
                onValueChange = onDomainChanged,
                placeholder = { Text(stringResource(R.string.dns_example)) },
                singleLine = true,
                enabled = !isLoading,
                isError = isInvalidInput,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { if (!isLoading) onLookup() },
                ),
            )
            if (isInvalidInput) {
                Text(
                    stringResource(R.string.dns_invalid_help),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLookup,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) stringResource(R.string.dns_loading) else stringResource(R.string.dns_query))
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdvancedSettingsToggle,
                enabled = !isLoading,
            ) {
                Text(if (uiState.advancedSettingsExpanded) stringResource(R.string.dns_collapse_advanced) else stringResource(R.string.dns_advanced))
            }
            if (uiState.advancedSettingsExpanded) {
                AdvancedSettings(
                    selectedRecordTypes = uiState.selectedRecordTypes,
                    onRecordTypeToggle = onRecordTypeToggle,
                )
            }
        }

        when (val status = uiState.status) {
            DnsStatus.Idle -> Unit
            is DnsStatus.Loading -> LoadingMessage(status.domain)
            is DnsStatus.Success -> DnsResultCard(
                result = status.result,
                addressClassifications = status.addressClassifications,
            )
            is DnsStatus.Error -> DnsResultCard(
                result = status.result,
                addressClassifications = status.addressClassifications,
            )
        }
    }
}

@Composable
private fun AdvancedSettings(
    selectedRecordTypes: Set<DnsRecordType>,
    onRecordTypeToggle: (DnsRecordType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.dns_record_type), style = MaterialTheme.typography.titleSmall)
        DnsRecordType.entries.chunked(3).forEach { types ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                types.forEach { type ->
                    FilterChip(
                        selected = type in selectedRecordTypes,
                        onClick = { onRecordTypeToggle(type) },
                        label = { Text(type.displayName()) },
                    )
                }
            }
        }
        Text(stringResource(R.string.dns_server), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.dns_system_dns),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsResultCard(
    result: DnsLookupResult,
    addressClassifications: List<IpAddressClassification>,
) {
    var showDetails by rememberSaveable(result.queryName, result.endTime) {
        mutableStateOf(false)
    }
    val isCompleted = result.status == DnsLookupStatus.SUCCESS ||
        result.status == DnsLookupStatus.PARTIAL ||
        result.status == DnsLookupStatus.NO_RECORDS

    ToolResultSection {
        ToolStatusSummary(
            title = stringResource(R.string.dns_result),
            status = result.status.statusVisualState(),
            label = result.status.headline(),
        )
        ToolResultRow(
            stringResource(R.string.dns_domain),
            result.queryName.ifBlank { stringResource(R.string.dns_unknown) },
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            stringResource(R.string.dns_elapsed),
            result.durationMs?.let { "$it ms" } ?: stringResource(R.string.dns_unknown),
        )
        Text(
            text = result.status.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AddressRecordSection(
            title = "IPv4",
            type = DnsRecordType.A,
            records = result.records,
        )
        AddressRecordSection(
            title = "IPv6",
            type = DnsRecordType.AAAA,
            records = result.records,
        )

        addressClassifications.forEach { classification ->
            SpecialAddressNotice(classification)
        }

        if (result.errorMessage != null && !isCompleted) {
            Text(
                text = result.status.errorDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) stringResource(R.string.dns_collapse_details) else stringResource(R.string.dns_details))
        }
        if (showDetails) {
            HorizontalDivider()
            DnsDetails(result)
        }
    }
}

@Composable
private fun AddressRecordSection(
    title: String,
    type: DnsRecordType,
    records: List<DnsRecord>,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    val matchingRecords = records.filter { record -> record.type == type }
    if (matchingRecords.isEmpty()) {
        Text(
            stringResource(R.string.dns_no_records),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        matchingRecords.forEach { record ->
            RecordValue(record.value, record.ttlSeconds)
        }
    }
}

@Composable
private fun RecordValue(
    value: String,
    ttlSeconds: Long?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(value, style = NetworkToolboxTextStyles.TechnicalData)
        ttlSeconds?.let {
            Text(
                stringResource(R.string.dns_ttl_seconds, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpecialAddressNotice(classification: IpAddressClassification) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        NetworkStatusChip(
            status = StatusVisualState.NOTICE,
            label = stringResource(R.string.dns_special),
        )
        Text(
            text = classification.address,
            style = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            text = classification.kind.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsDetails(result: DnsLookupResult) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(stringResource(R.string.dns_query_information), style = MaterialTheme.typography.titleSmall)
        ToolResultRow(
            stringResource(R.string.dns_domain),
            result.queryName,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        ToolResultRow(
            stringResource(R.string.dns_query_type),
            result.requestedTypes.joinToString(" / ") { it.displayName() },
        )
        ToolResultRow(stringResource(R.string.dns_method), result.method.displayName())
        ToolResultRow(stringResource(R.string.dns_elapsed), result.durationMs?.let { "$it ms" } ?: stringResource(R.string.dns_unknown))

        Text(stringResource(R.string.dns_environment), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.dns_configured_dns), style = MaterialTheme.typography.bodyMedium)
        val server = result.server
        if (server?.configuredAddresses.isNullOrEmpty()) {
            Text(
                stringResource(R.string.dns_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                server?.configuredAddresses.orEmpty().forEach { address ->
                    Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                }
            }
        }
        server?.privateDnsActive?.let { active ->
            ToolResultRow("Private DNS", if (active) stringResource(R.string.dns_enabled) else stringResource(R.string.dns_disabled))
        }
        server?.privateDnsServerName?.let { name ->
            ToolResultRow(stringResource(R.string.dns_private_name), name)
        }

        Text(stringResource(R.string.dns_records), style = MaterialTheme.typography.titleSmall)
        if (result.records.isEmpty()) {
            Text(
                stringResource(R.string.dns_no_records),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            result.records.forEach { record -> DnsRecordDetails(record) }
        }
    }
}

@Composable
private fun DnsRecordDetails(record: DnsRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
        Text(record.type.displayName(), style = MaterialTheme.typography.titleSmall)
        ToolResultRow(stringResource(R.string.dns_name), record.name.ifBlank { stringResource(R.string.dns_unknown) })
        ToolResultRow(
            stringResource(R.string.dns_value),
            record.value,
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        record.ttlSeconds?.let { ttl -> ToolResultRow("TTL", stringResource(R.string.dns_seconds, ttl)) }
        record.priority?.let { priority -> ToolResultRow(stringResource(R.string.dns_priority), priority.toString()) }
        if (record.txtSegments.size > 1) {
            ToolResultRow(stringResource(R.string.dns_segments), record.txtSegments.joinToString(" | "))
        }
    }
}

@Composable
private fun LoadingMessage(domain: String) {
    ToolRunningSection {
        ToolStatusSummary(
            title = stringResource(R.string.dns_running),
            status = StatusVisualState.RUNNING,
            label = stringResource(R.string.dns_querying),
        )
        Text(
            domain.ifBlank { stringResource(R.string.dns_enter_domain) },
            style = NetworkToolboxTextStyles.TechnicalData,
        )
    }
}

private fun DnsLookupStatus.statusVisualState(): StatusVisualState = when (this) {
    DnsLookupStatus.SUCCESS -> StatusVisualState.NORMAL
    DnsLookupStatus.NO_RECORDS -> StatusVisualState.NOTICE
    DnsLookupStatus.PARTIAL,
    DnsLookupStatus.NXDOMAIN,
    -> StatusVisualState.WARNING

    DnsLookupStatus.TIMEOUT,
    DnsLookupStatus.NETWORK_ERROR,
    DnsLookupStatus.INVALID_RESPONSE,
    DnsLookupStatus.INVALID_QUERY,
    DnsLookupStatus.FAILED,
    -> StatusVisualState.ERROR
}

@Composable
private fun DnsLookupStatus.headline(): String = when (this) {
    DnsLookupStatus.SUCCESS -> stringResource(R.string.dns_success)
    DnsLookupStatus.PARTIAL -> stringResource(R.string.dns_partial)
    DnsLookupStatus.NO_RECORDS -> stringResource(R.string.dns_empty)
    DnsLookupStatus.NXDOMAIN -> stringResource(R.string.dns_nxdomain)
    DnsLookupStatus.TIMEOUT -> stringResource(R.string.dns_timeout)
    DnsLookupStatus.NETWORK_ERROR -> stringResource(R.string.dns_network_error)
    DnsLookupStatus.INVALID_QUERY -> stringResource(R.string.dns_invalid)
    DnsLookupStatus.INVALID_RESPONSE -> stringResource(R.string.dns_invalid_response)
    DnsLookupStatus.FAILED -> stringResource(R.string.dns_failed)
}

@Composable
private fun DnsLookupStatus.description(): String = when (this) {
    DnsLookupStatus.SUCCESS -> stringResource(R.string.dns_success_description)
    DnsLookupStatus.PARTIAL -> stringResource(R.string.dns_partial_description)
    DnsLookupStatus.NO_RECORDS -> stringResource(R.string.dns_empty_description)
    DnsLookupStatus.NXDOMAIN -> stringResource(R.string.dns_nxdomain_description)
    DnsLookupStatus.TIMEOUT -> stringResource(R.string.dns_timeout_description)
    DnsLookupStatus.NETWORK_ERROR -> stringResource(R.string.dns_network_description)
    DnsLookupStatus.INVALID_QUERY -> stringResource(R.string.dns_invalid_description)
    DnsLookupStatus.INVALID_RESPONSE -> stringResource(R.string.dns_response_description)
    DnsLookupStatus.FAILED -> stringResource(R.string.dns_failed_description)
}

@Composable
private fun DnsLookupStatus.errorDescription(): String = when (this) {
    DnsLookupStatus.NXDOMAIN -> stringResource(R.string.dns_nxdomain_help)
    DnsLookupStatus.TIMEOUT -> stringResource(R.string.dns_timeout_help)
    DnsLookupStatus.NETWORK_ERROR -> stringResource(R.string.dns_network_help)
    DnsLookupStatus.INVALID_RESPONSE -> stringResource(R.string.dns_response_help)
    DnsLookupStatus.INVALID_QUERY -> stringResource(R.string.dns_format_help)
    else -> stringResource(R.string.dns_retry)
}

@Composable
private fun DnsQueryMethod.displayName(): String = when (this) {
    DnsQueryMethod.ANDROID_DNS_RESOLVER -> stringResource(R.string.dns_system_dns)
    DnsQueryMethod.SYSTEM_RESOLVER_ADDRESSES_ONLY -> stringResource(R.string.dns_addresses_only)
    DnsQueryMethod.UNAVAILABLE -> stringResource(R.string.dns_unavailable)
}

private fun DnsRecordType.displayName(): String = when (this) {
    DnsRecordType.A -> "A"
    DnsRecordType.AAAA -> "AAAA"
    DnsRecordType.CNAME -> "CNAME"
    DnsRecordType.MX -> "MX"
    DnsRecordType.TXT -> "TXT"
}

@Composable
private fun IpAddressKind.description(): String = when (this) {
    IpAddressKind.FAKE_IP_RANGE ->
        stringResource(R.string.dns_fake_ip)
    IpAddressKind.RFC1918_PRIVATE ->
        stringResource(R.string.dns_private)
    IpAddressKind.LOOPBACK ->
        stringResource(R.string.dns_loopback)
    IpAddressKind.LINK_LOCAL ->
        stringResource(R.string.dns_link_local)
    IpAddressKind.IPV6_ULA ->
        stringResource(R.string.dns_ula)
    IpAddressKind.IPV6_LINK_LOCAL ->
        stringResource(R.string.dns_ipv6_link_local)
}

private fun DnsStatus.isInvalidInput(): Boolean =
    this is DnsStatus.Error && result.status == DnsLookupStatus.INVALID_QUERY
