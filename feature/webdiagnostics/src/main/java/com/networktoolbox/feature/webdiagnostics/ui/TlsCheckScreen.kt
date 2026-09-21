package com.networktoolbox.feature.webdiagnostics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.DestructiveActionButton
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolMetric
import com.networktoolbox.core.designsystem.ToolMetricGrid
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.CertificateValidityStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckFindingCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckRecommendationCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckStage
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckFailureReason
import com.networktoolbox.feature.webdiagnostics.presentation.TlsCheckRunState
import com.networktoolbox.feature.webdiagnostics.presentation.TlsCheckUiState
import com.networktoolbox.feature.webdiagnostics.presentation.TlsInputError
import com.networktoolbox.feature.webdiagnostics.presentation.toVisualState
import java.text.DateFormat
import java.util.Date

@Composable
fun TlsCheckScreen(
    uiState: TlsCheckUiState,
    onTargetChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopAndLeave: (() -> Unit) -> Unit,
    onToggleDetails: () -> Unit,
    onToggleSans: () -> Unit,
    onToggleChain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBackDialog by remember { mutableStateOf(false) }
    fun requestBack() { if (uiState.isRunning) showBackDialog = true else onBack() }
    BackHandler(onBack = ::requestBack)

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.web_tls_title),
            icon = Icons.Outlined.Security,
            accent = NetworkToolAccent.CYAN,
            onBack = ::requestBack,
        )
        Text(stringResource(R.string.web_tls_description))
        InputCard(uiState, onTargetChanged, onPortChanged, onStart)
        when (val state = uiState.runState) {
            TlsCheckRunState.Idle -> Unit
            is TlsCheckRunState.Running -> ToolRunningSection(Modifier.testTag("tls_running")) {
                ToolStatusSummary(
                    title = stringResource(R.string.web_tls_running),
                    status = StatusVisualState.RUNNING,
                    description = state.progress.target,
                )
                TlsCheckStage.entries.forEach { stage ->
                    StageRow(tlsStageLabel(stage), state.progress.stages.getValue(stage))
                }
                DestructiveActionButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.web_tls_stop))
                }
            }
            is TlsCheckRunState.Cancelled -> ToolResultSection(Modifier.testTag("tls_cancelled")) {
                ToolStatusSummary(
                    title = stringResource(R.string.web_tls_cancelled),
                    status = StatusVisualState.CANCELLED,
                    description = stringResource(R.string.web_tls_cancelled_desc),
                )
            }
            is TlsCheckRunState.Completed -> ResultContent(
                result = state.result,
                detailsExpanded = uiState.detailsExpanded,
                sansExpanded = uiState.sansExpanded,
                chainExpanded = uiState.chainExpanded,
                onToggleDetails = onToggleDetails,
                onToggleSans = onToggleSans,
                onToggleChain = onToggleChain,
            )
        }
    }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text(stringResource(R.string.web_back_dialog_title)) },
            text = { Text(stringResource(R.string.web_back_dialog_body)) },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) { Text(stringResource(R.string.web_keep_running)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    onStopAndLeave(onBack)
                }) { Text(stringResource(R.string.web_stop_leave)) }
            },
        )
    }
}

@Composable
private fun InputCard(
    state: TlsCheckUiState,
    onTargetChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onStart: () -> Unit,
) {
    ToolInputSection(stringResource(R.string.web_tls_target)) {
        OutlinedTextField(
            value = state.targetInput,
            onValueChange = onTargetChanged,
            modifier = Modifier.fillMaxWidth().testTag("tls_target"),
            enabled = !state.isRunning,
            singleLine = true,
            label = { Text(stringResource(R.string.web_tls_target_hint)) },
            isError = TlsInputError.TARGET_REQUIRED in state.inputErrors || TlsInputError.TARGET_INVALID in state.inputErrors,
            supportingText = {
                val message = when {
                    TlsInputError.TARGET_REQUIRED in state.inputErrors -> R.string.web_tls_host_required
                    TlsInputError.TARGET_INVALID in state.inputErrors -> R.string.web_tls_host_invalid
                    else -> null
                }
                message?.let { Text(stringResource(it)) }
            },
        )
        OutlinedTextField(
            value = state.portInput,
            onValueChange = onPortChanged,
            modifier = Modifier.fillMaxWidth().testTag("tls_port"),
            enabled = !state.isRunning,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.web_tls_port)) },
            isError = TlsInputError.PORT_REQUIRED in state.inputErrors || TlsInputError.PORT_INVALID in state.inputErrors,
            supportingText = {
                val message = when {
                    TlsInputError.PORT_REQUIRED in state.inputErrors -> R.string.web_tls_port_required
                    TlsInputError.PORT_INVALID in state.inputErrors -> R.string.web_tls_port_invalid
                    else -> null
                }
                message?.let { Text(stringResource(it)) }
            },
        )
        PrimaryActionButton(
            onClick = onStart,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth().testTag("tls_start"),
        ) { Text(stringResource(R.string.web_tls_start)) }
    }
}

@Composable
private fun ResultContent(
    result: TlsCheckResult,
    detailsExpanded: Boolean,
    sansExpanded: Boolean,
    chainExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onToggleSans: () -> Unit,
    onToggleChain: () -> Unit,
) {
    ToolResultSection(Modifier.testTag("tls_result")) {
        ToolStatusSummary(
            title = result.normalizedTarget?.let { "$it:${result.port}" } ?: stringResource(R.string.web_tls_result),
            status = result.analysis.outcome.toVisualState(),
            label = tlsOutcomeLabel(result.analysis.outcome),
            description = tlsOutcomeDescription(result),
        )
        result.probeResult?.let { probe ->
            ToolMetricGrid(
                listOf(
                    ToolMetric(
                        stringResource(R.string.web_connection),
                        if (probe.connection.outcome == TcpConnectOutcome.CONNECTED) stringResource(R.string.web_connected)
                        else stringResource(R.string.web_not_connected),
                    ),
                    ToolMetric(stringResource(R.string.web_trust), trustLabel(probe.trustStatus)),
                    ToolMetric(stringResource(R.string.web_hostname), hostnameLabel(probe.hostnameStatus)),
                    ToolMetric(stringResource(R.string.web_validity), validityLabel(probe.certificate.leaf?.validityStatus)),
                ),
            )
        }
    }
    if (result.analysis.findings.isNotEmpty()) ToolResultSection {
        Text(stringResource(R.string.web_findings))
        result.analysis.findings.forEach { Text("• ${tlsFinding(it)}") }
    }
    if (result.analysis.recommendations.isNotEmpty()) ToolResultSection {
        Text(stringResource(R.string.web_recommendations))
        result.analysis.recommendations.forEachIndexed { index, code -> Text("${index + 1}. ${tlsRecommendation(code)}") }
    }
    SecondaryActionButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (detailsExpanded) R.string.web_hide_details else R.string.web_view_details))
    }
    if (detailsExpanded) TlsTechnicalDetails(result, sansExpanded, chainExpanded, onToggleSans, onToggleChain)
}

@Composable
private fun TlsTechnicalDetails(
    result: TlsCheckResult,
    sansExpanded: Boolean,
    chainExpanded: Boolean,
    onToggleSans: () -> Unit,
    onToggleChain: () -> Unit,
) {
    val probe = result.probeResult
    ToolResultSection {
        Text(stringResource(R.string.web_technical_details))
        ToolResultRow(stringResource(R.string.web_address), result.selectedAddress ?: stringResource(R.string.web_no_value))
        ToolResultRow(stringResource(R.string.web_tls_version), probe?.session?.protocol ?: stringResource(R.string.web_no_value))
        ToolResultRow(stringResource(R.string.web_cipher), probe?.session?.cipherSuite ?: stringResource(R.string.web_no_value))
        probe?.session?.applicationProtocol?.let { ToolResultRow(stringResource(R.string.web_alpn), it) }
        ToolResultRow(stringResource(R.string.web_duration), stringResource(R.string.web_milliseconds, result.totalDurationMs))
    }
    ToolResultSection {
        Text(stringResource(R.string.web_certificate))
        val leaf = probe?.certificate?.leaf
        if (leaf == null) Text(stringResource(R.string.web_no_certificate)) else {
            ToolResultRow(stringResource(R.string.web_subject), leaf.subject)
            ToolResultRow(stringResource(R.string.web_issuer), leaf.issuer)
            ToolResultRow(stringResource(R.string.web_valid_from), formatDate(leaf.validFromEpochMs))
            ToolResultRow(stringResource(R.string.web_valid_until), formatDate(leaf.validUntilEpochMs))
            leaf.remainingValidityDays?.let { ToolResultRow(stringResource(R.string.web_validity), stringResource(R.string.web_remaining_days, it)) }
            val sans = leaf.dnsSubjectAlternativeNames + leaf.ipSubjectAlternativeNames
            if (sans.isNotEmpty()) {
                TextButton(onClick = onToggleSans) {
                    Text(stringResource(if (sansExpanded) R.string.web_hide_san else R.string.web_view_san))
                }
                if (sansExpanded) sans.forEach { Text("• $it") }
            }
        }
    }
    val chain = probe?.certificate?.presentedChain.orEmpty()
    ToolResultSection {
        Text(stringResource(R.string.web_presented_chain))
        Text(stringResource(R.string.web_presented_chain_help))
        if (chain.isEmpty()) Text(stringResource(R.string.web_no_certificate)) else {
            TextButton(onClick = onToggleChain) {
                Text(stringResource(if (chainExpanded) R.string.web_hide_chain else R.string.web_view_chain, chain.size))
            }
            if (chainExpanded) chain.forEachIndexed { index, cert ->
                Text(stringResource(R.string.web_certificate_item, index + 1))
                ToolResultRow(stringResource(R.string.web_subject), cert.subject)
                ToolResultRow(stringResource(R.string.web_issuer), cert.issuer)
            }
        }
    }
}

@Composable private fun tlsStageLabel(stage: TlsCheckStage): String = stringResource(when (stage) {
    TlsCheckStage.DNS -> R.string.web_stage_dns
    TlsCheckStage.TCP -> R.string.web_stage_tcp
    TlsCheckStage.TLS -> R.string.web_stage_tls
    TlsCheckStage.CERTIFICATE -> R.string.web_stage_certificate
})

@Composable private fun tlsOutcomeLabel(outcome: TlsCheckOutcome): String = stringResource(when (outcome) {
    TlsCheckOutcome.HEALTHY -> R.string.web_stage_success
    TlsCheckOutcome.ATTENTION -> R.string.web_stage_attention
    TlsCheckOutcome.FAILED -> R.string.web_stage_failed
    TlsCheckOutcome.NETWORK_CHANGED -> R.string.web_tls_network_changed
})

@Composable private fun tlsOutcomeDescription(result: TlsCheckResult): String {
    result.failureReason?.let { return stringResource(when (it) {
        TlsCheckFailureReason.INVALID_TARGET -> R.string.web_tls_invalid_target
        TlsCheckFailureReason.INVALID_PORT -> R.string.web_tls_port_invalid
        TlsCheckFailureReason.DNS_NXDOMAIN -> R.string.web_tls_dns_nxdomain
        TlsCheckFailureReason.DNS_NO_RECORDS -> R.string.web_tls_dns_no_records
        TlsCheckFailureReason.DNS_TIMEOUT -> R.string.web_tls_dns_timeout
        TlsCheckFailureReason.DNS_FAILED -> R.string.web_tls_dns_failed
        TlsCheckFailureReason.UNEXPECTED_ERROR -> R.string.web_tls_unexpected
    }) }
    return stringResource(when (result.analysis.outcome) {
        TlsCheckOutcome.HEALTHY -> R.string.web_tls_healthy
        TlsCheckOutcome.ATTENTION -> R.string.web_tls_attention
        TlsCheckOutcome.FAILED -> R.string.web_tls_failed
        TlsCheckOutcome.NETWORK_CHANGED -> R.string.web_tls_network_changed
    })
}

@Composable private fun trustLabel(status: CertificateTrustStatus): String = stringResource(when (status) {
    CertificateTrustStatus.SYSTEM_TRUSTED -> R.string.web_trusted
    CertificateTrustStatus.UNTRUSTED -> R.string.web_untrusted
    CertificateTrustStatus.NOT_EVALUATED -> R.string.web_not_evaluated
})

@Composable private fun hostnameLabel(status: HostnameVerificationStatus): String = stringResource(when (status) {
    HostnameVerificationStatus.MATCH -> R.string.web_hostname_match
    HostnameVerificationStatus.MISMATCH -> R.string.web_hostname_mismatch
    HostnameVerificationStatus.NOT_EVALUATED -> R.string.web_not_evaluated
})

@Composable private fun validityLabel(status: CertificateValidityStatus?): String = stringResource(when (status) {
    CertificateValidityStatus.VALID -> R.string.web_valid
    CertificateValidityStatus.EXPIRED -> R.string.web_expired
    CertificateValidityStatus.NOT_YET_VALID -> R.string.web_not_yet_valid
    CertificateValidityStatus.UNKNOWN, null -> R.string.web_unknown
})

@Composable private fun tlsFinding(code: TlsCheckFindingCode): String = stringResource(when (code) {
    TlsCheckFindingCode.TCP_CONNECTED -> R.string.web_tls_tcp_connected
    TlsCheckFindingCode.TCP_REFUSED -> R.string.web_tls_tcp_refused
    TlsCheckFindingCode.TCP_TIMEOUT -> R.string.web_tls_tcp_timeout
    TlsCheckFindingCode.TCP_UNREACHABLE -> R.string.web_tls_tcp_unreachable
    TlsCheckFindingCode.TLS_CONNECTED -> R.string.web_tls_handshake_success
    TlsCheckFindingCode.TLS_TIMEOUT -> R.string.web_tls_timeout
    TlsCheckFindingCode.TLS_HANDSHAKE_FAILED -> R.string.web_tls_handshake_failed
    TlsCheckFindingCode.TRUSTED -> R.string.web_tls_trusted
    TlsCheckFindingCode.UNTRUSTED -> R.string.web_tls_untrusted
    TlsCheckFindingCode.HOSTNAME_MATCH -> R.string.web_tls_hostname_match
    TlsCheckFindingCode.HOSTNAME_MISMATCH -> R.string.web_tls_hostname_mismatch
    TlsCheckFindingCode.SELF_SIGNED -> R.string.web_tls_self_signed
    TlsCheckFindingCode.EXPIRED -> R.string.web_tls_expired
    TlsCheckFindingCode.NOT_YET_VALID -> R.string.web_tls_future
    TlsCheckFindingCode.EXPIRING_SOON -> R.string.web_tls_expiring
    TlsCheckFindingCode.NO_CERTIFICATE -> R.string.web_tls_no_cert
    TlsCheckFindingCode.VPN_ACTIVE -> R.string.web_tls_vpn
    TlsCheckFindingCode.NETWORK_CHANGED -> R.string.web_tls_changed
})

@Composable private fun tlsRecommendation(code: TlsCheckRecommendationCode): String = stringResource(when (code) {
    TlsCheckRecommendationCode.CHECK_HOST_AND_PORT -> R.string.web_rec_host_port
    TlsCheckRecommendationCode.CHECK_FIREWALL_OR_PATH -> R.string.web_rec_firewall
    TlsCheckRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED -> R.string.web_rec_private_ca
    TlsCheckRecommendationCode.CHECK_CERTIFICATE_SAN -> R.string.web_rec_san
    TlsCheckRecommendationCode.RENEW_CERTIFICATE -> R.string.web_rec_renew
    TlsCheckRecommendationCode.CHECK_DEVICE_AND_SERVER_TIME -> R.string.web_rec_time
    TlsCheckRecommendationCode.RETRY_ON_STABLE_NETWORK -> R.string.web_rec_stable
})

private fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
