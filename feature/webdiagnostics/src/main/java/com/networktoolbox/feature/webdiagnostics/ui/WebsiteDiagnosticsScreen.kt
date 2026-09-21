package com.networktoolbox.feature.webdiagnostics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
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
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.website.WebsiteDiagnosticFinding
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteDiagnosticRecommendation
import com.networktoolbox.core.network.website.WebsiteDiagnosticSnapshot
import com.networktoolbox.core.network.website.WebsiteFindingCode
import com.networktoolbox.core.network.website.WebsiteProgressStatus
import com.networktoolbox.core.network.website.WebsiteRecommendationCode
import com.networktoolbox.core.network.website.WebsiteScheme
import com.networktoolbox.core.network.website.WebsiteStage
import com.networktoolbox.core.network.website.WebsiteStageStatus
import com.networktoolbox.core.network.website.WebsiteTcpAttemptEvidence
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsRunState
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsUiState
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteInputError
import com.networktoolbox.feature.webdiagnostics.presentation.toVisualState

@Composable
fun WebsiteDiagnosticsScreen(
    uiState: WebsiteDiagnosticsUiState,
    onTargetChanged: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopAndLeave: (() -> Unit) -> Unit,
    onToggleDetails: () -> Unit,
    onToggleRedirects: () -> Unit,
    onToggleRecommendations: () -> Unit,
    onToggleCertificate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBackDialog by remember { mutableStateOf(false) }
    fun requestBack() { if (uiState.isRunning) showBackDialog = true else onBack() }
    BackHandler(onBack = ::requestBack)

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = stringResource(R.string.web_site_title),
            icon = Icons.Outlined.Language,
            accent = NetworkToolAccent.AMBER,
            onBack = ::requestBack,
        )
        Text(stringResource(R.string.web_site_description))
        ToolInputSection(stringResource(R.string.web_site_target)) {
            OutlinedTextField(
                value = uiState.targetInput,
                onValueChange = onTargetChanged,
                modifier = Modifier.fillMaxWidth().testTag("website_target"),
                enabled = !uiState.isRunning,
                singleLine = true,
                label = { Text(stringResource(R.string.web_site_target_hint)) },
                isError = WebsiteInputError.TARGET_REQUIRED in uiState.inputErrors,
                supportingText = {
                    if (WebsiteInputError.TARGET_REQUIRED in uiState.inputErrors) {
                        Text(stringResource(R.string.web_site_target_required))
                    }
                },
            )
            PrimaryActionButton(
                onClick = onStart,
                enabled = !uiState.isRunning,
                modifier = Modifier.fillMaxWidth().testTag("website_start"),
            ) { Text(stringResource(R.string.web_site_start)) }
            Text(stringResource(R.string.web_privacy_note))
        }

        when (val state = uiState.runState) {
            WebsiteDiagnosticsRunState.Idle -> Unit
            is WebsiteDiagnosticsRunState.Running -> ToolRunningSection(Modifier.testTag("website_running")) {
                ToolStatusSummary(
                    title = stringResource(R.string.web_site_running),
                    status = StatusVisualState.RUNNING,
                    description = state.progress.targetUrlRedacted.takeIf(String::isNotBlank),
                )
                WebsiteStage.entries.forEach { stage ->
                    val status = state.progress.stages[stage]
                    StageRow(
                        websiteStageLabel(stage),
                        status?.toVisualState() ?: StatusVisualState.NOT_EXECUTED,
                        websiteProgressLabel(status),
                    )
                }
                DestructiveActionButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.web_site_stop))
                }
            }
            is WebsiteDiagnosticsRunState.Cancelled -> ToolResultSection(Modifier.testTag("website_cancelled")) {
                ToolStatusSummary(
                    title = stringResource(R.string.web_site_cancelled),
                    status = StatusVisualState.CANCELLED,
                    description = stringResource(R.string.web_site_cancelled_desc),
                )
            }
            is WebsiteDiagnosticsRunState.Completed -> WebsiteResult(
                snapshot = state.snapshot,
                detailsExpanded = uiState.detailsExpanded,
                redirectsExpanded = uiState.redirectsExpanded,
                recommendationsExpanded = uiState.recommendationsExpanded,
                certificateExpanded = uiState.certificateExpanded,
                onToggleDetails = onToggleDetails,
                onToggleRedirects = onToggleRedirects,
                onToggleRecommendations = onToggleRecommendations,
                onToggleCertificate = onToggleCertificate,
            )
        }
    }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text(stringResource(R.string.web_back_dialog_title)) },
            text = { Text(stringResource(R.string.web_back_dialog_body)) },
            dismissButton = { TextButton(onClick = { showBackDialog = false }) { Text(stringResource(R.string.web_keep_running)) } },
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
private fun WebsiteResult(
    snapshot: WebsiteDiagnosticSnapshot,
    detailsExpanded: Boolean,
    redirectsExpanded: Boolean,
    recommendationsExpanded: Boolean,
    certificateExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onToggleRedirects: () -> Unit,
    onToggleRecommendations: () -> Unit,
    onToggleCertificate: () -> Unit,
) {
    val target = snapshot.normalizedTarget
    val lastHop = snapshot.hops.lastOrNull()
    ToolResultSection(Modifier.testTag("website_result")) {
        ToolStatusSummary(
            title = target?.displayUrlRedacted ?: snapshot.enteredInputRedacted,
            status = snapshot.outcome.toVisualState(),
            label = websiteOutcomeLabel(snapshot.outcome),
            description = websiteOutcomeDescription(snapshot.outcome),
        )
        if (target?.schemeWasInferred == true) Text(stringResource(R.string.web_site_https_inferred))
        if (target?.scheme == WebsiteScheme.HTTP) Text(stringResource(R.string.web_site_cleartext))
        WebsiteStage.entries.forEach { stage ->
            val status = finalStageStatus(stage, lastHop)
            StageRow(websiteStageLabel(stage), status.toVisualState(), websiteStageStatusLabel(status))
        }
    }
    if (snapshot.findings.isNotEmpty()) ToolResultSection {
        Text(stringResource(R.string.web_findings))
        snapshot.findings.forEach { Text("• ${websiteFinding(it)}") }
    }
    if (snapshot.recommendations.isNotEmpty()) ToolResultSection {
        Text(stringResource(R.string.web_recommendations))
        val visible = if (recommendationsExpanded) snapshot.recommendations else snapshot.recommendations.take(3)
        visible.forEachIndexed { index, recommendation -> Text("${index + 1}. ${websiteRecommendation(recommendation)}") }
        if (snapshot.recommendations.size > 3) TextButton(onClick = onToggleRecommendations) {
            Text(stringResource(if (recommendationsExpanded) R.string.web_less_recommendations else R.string.web_more_recommendations))
        }
    }
    if (snapshot.hops.size > 1 || snapshot.hops.any { it.redirectFailure != null }) {
        ToolResultSection {
            Text(stringResource(R.string.web_site_redirects))
            TextButton(onClick = onToggleRedirects) {
                Text(stringResource(if (redirectsExpanded) R.string.web_site_hide_redirects else R.string.web_site_view_redirects, snapshot.hops.size))
            }
            if (redirectsExpanded) snapshot.hops.forEachIndexed { index, hop ->
                val status = hop.http?.statusCode
                Text(if (status == null) stringResource(R.string.web_site_redirect_no_status, index + 1)
                else stringResource(R.string.web_site_redirect_hop, index + 1, status))
                Text(hop.target.displayUrlRedacted)
                if (index > 0 && hop.target.asciiHost != snapshot.hops[index - 1].target.asciiHost) {
                    Text(stringResource(R.string.web_site_cross_host))
                }
            }
        }
    }
    SecondaryActionButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (detailsExpanded) R.string.web_hide_details else R.string.web_view_details))
    }
    if (detailsExpanded) WebsiteTechnicalDetails(snapshot, certificateExpanded, onToggleCertificate)
}

@Composable
private fun WebsiteTechnicalDetails(
    snapshot: WebsiteDiagnosticSnapshot,
    certificateExpanded: Boolean,
    onToggleCertificate: () -> Unit,
) {
    val hop = snapshot.hops.lastOrNull()
    ToolResultSection {
        Text(stringResource(R.string.web_site_transport))
        val transport = hop?.http?.transportPath ?: hop?.plannedHttpTransport
        ToolResultRow(stringResource(R.string.web_site_transport), transportLabel(transport))
        if (transport != null && transport != HttpTransportPath.DIRECT) Text(stringResource(R.string.web_site_proxy_notice))
        if (snapshot.networkContext.vpnActive == true) Text(stringResource(R.string.web_site_vpn_notice))
        if (hop?.dns?.fakeIpDetected == true) Text(stringResource(R.string.web_site_fake_ip))
    }
    ToolResultSection {
        Text(stringResource(R.string.web_technical_details))
        ToolResultRow(
            stringResource(R.string.web_dns_addresses),
            hop?.dns?.candidateAddresses?.joinToString("\n").takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.web_no_value),
        )
        ToolResultRow(
            stringResource(R.string.web_tcp_attempts),
            tcpAttemptsLabel(hop?.tcp?.attempts.orEmpty())
                .takeUnless(String::isBlank) ?: stringResource(R.string.web_no_value),
        )
        ToolResultRow(stringResource(R.string.web_site_validated), when (snapshot.networkContext.validated) {
            true -> stringResource(R.string.web_yes)
            false -> stringResource(R.string.web_no)
            null -> stringResource(R.string.web_unknown)
        })
    }
    hop?.tls?.result?.let { tls ->
        ToolResultSection {
            Text(stringResource(R.string.web_direct_tls))
            ToolResultRow(stringResource(R.string.web_address), tls.connectAddress)
            ToolResultRow(stringResource(R.string.web_tls_version), tls.session.protocol ?: stringResource(R.string.web_no_value))
            ToolResultRow(stringResource(R.string.web_cipher), tls.session.cipherSuite ?: stringResource(R.string.web_no_value))
            ToolResultRow(stringResource(R.string.web_trust), when (tls.trustStatus) {
                com.networktoolbox.core.network.tls.CertificateTrustStatus.SYSTEM_TRUSTED -> stringResource(R.string.web_trusted)
                com.networktoolbox.core.network.tls.CertificateTrustStatus.UNTRUSTED -> stringResource(R.string.web_untrusted)
                com.networktoolbox.core.network.tls.CertificateTrustStatus.NOT_EVALUATED -> stringResource(R.string.web_not_evaluated)
            })
            TextButton(onClick = onToggleCertificate) {
                Text(stringResource(if (certificateExpanded) R.string.web_hide_chain else R.string.web_certificate))
            }
            if (certificateExpanded) tls.certificate.leaf?.let { leaf ->
                ToolResultRow(stringResource(R.string.web_subject), leaf.subject)
                ToolResultRow(stringResource(R.string.web_issuer), leaf.issuer)
                ToolResultRow(stringResource(R.string.web_valid_from), java.text.DateFormat.getDateInstance().format(java.util.Date(leaf.validFromEpochMs)))
                ToolResultRow(stringResource(R.string.web_valid_until), java.text.DateFormat.getDateInstance().format(java.util.Date(leaf.validUntilEpochMs)))
                val sans = leaf.dnsSubjectAlternativeNames + leaf.ipSubjectAlternativeNames
                if (sans.isNotEmpty()) ToolResultRow(stringResource(R.string.web_san), sans.joinToString("\n"))
            }
        }
    }
    hop?.http?.let { http -> ToolResultSection {
        Text(stringResource(R.string.web_http_response))
        http.statusCode?.let { ToolResultRow(stringResource(R.string.web_site_http_status), it.toString()) }
        ToolResultRow(stringResource(R.string.web_site_protocol), protocolLabel(http.protocol))
        http.responseHeaders.server?.let { ToolResultRow(stringResource(R.string.web_site_server), it) }
        http.responseHeaders.contentType?.let { ToolResultRow(stringResource(R.string.web_site_content_type), it) }
        http.responseHeaders.location?.let { ToolResultRow(stringResource(R.string.web_site_location), it) }
    } }
    ToolResultSection {
        Text(stringResource(R.string.web_duration))
        val metrics = buildList {
            hop?.dns?.durationMs?.let { add(ToolMetric(stringResource(R.string.web_timing_dns), stringResource(R.string.web_milliseconds, it))) }
            hop?.tcp?.durationMs?.let { add(ToolMetric(stringResource(R.string.web_timing_tcp), stringResource(R.string.web_milliseconds, it))) }
            hop?.tls?.durationMs?.let { add(ToolMetric(stringResource(R.string.web_timing_tls), stringResource(R.string.web_milliseconds, it))) }
            hop?.http?.durationMs?.let { add(ToolMetric(stringResource(R.string.web_timing_http), stringResource(R.string.web_milliseconds, it))) }
            add(ToolMetric(stringResource(R.string.web_timing_total), stringResource(R.string.web_milliseconds, snapshot.totalDurationMs)))
        }
        ToolMetricGrid(metrics)
    }
}

private fun finalStageStatus(stage: WebsiteStage, hop: com.networktoolbox.core.network.website.WebsiteDiagnosticHop?): WebsiteStageStatus {
    if (hop == null) return WebsiteStageStatus.SKIPPED
    return when (stage) {
        WebsiteStage.DNS -> hop.dns.status
        WebsiteStage.TCP -> hop.tcp.status
        WebsiteStage.TLS -> hop.tls.status
        WebsiteStage.CERTIFICATE -> {
            val tls = hop.tls.result
            when {
                hop.target.scheme == WebsiteScheme.HTTP -> WebsiteStageStatus.NOT_APPLICABLE
                tls?.certificate?.leaf == null -> WebsiteStageStatus.SKIPPED
                tls.certificateIssues.isNotEmpty() ||
                    tls.trustStatus != CertificateTrustStatus.SYSTEM_TRUSTED ||
                    tls.hostnameStatus != HostnameVerificationStatus.MATCH -> WebsiteStageStatus.ATTENTION
                else -> WebsiteStageStatus.PASS
            }
        }
        WebsiteStage.HTTP -> {
            val http = hop.http
            when {
                http?.responded == true && http.statusCode in 200..299 -> WebsiteStageStatus.PASS
                http?.responded == true -> WebsiteStageStatus.ATTENTION
                hop.httpFailure != null -> WebsiteStageStatus.FAIL
                else -> WebsiteStageStatus.SKIPPED
            }
        }
    }
}

@Composable private fun websiteStageLabel(stage: WebsiteStage): String = stringResource(when (stage) {
    WebsiteStage.DNS -> R.string.web_stage_dns
    WebsiteStage.TCP -> R.string.web_stage_tcp
    WebsiteStage.TLS -> R.string.web_stage_tls
    WebsiteStage.CERTIFICATE -> R.string.web_stage_certificate
    WebsiteStage.HTTP -> R.string.web_stage_http
})

@Composable private fun websiteProgressLabel(status: WebsiteProgressStatus?): String = stringResource(when (status) {
    null -> R.string.web_stage_pending
    WebsiteProgressStatus.RUNNING -> R.string.web_stage_running
    WebsiteProgressStatus.PASS -> R.string.web_stage_success
    WebsiteProgressStatus.ATTENTION -> R.string.web_stage_attention
    WebsiteProgressStatus.FAIL -> R.string.web_stage_failed
    WebsiteProgressStatus.NOT_APPLICABLE -> R.string.web_stage_na
    WebsiteProgressStatus.SKIPPED -> R.string.web_stage_skipped
})

@Composable private fun websiteStageStatusLabel(status: WebsiteStageStatus): String = stringResource(when (status) {
    WebsiteStageStatus.PASS -> R.string.web_stage_success
    WebsiteStageStatus.ATTENTION -> R.string.web_stage_attention
    WebsiteStageStatus.FAIL -> R.string.web_stage_failed
    WebsiteStageStatus.NOT_APPLICABLE -> R.string.web_stage_na
    WebsiteStageStatus.SKIPPED, WebsiteStageStatus.UNKNOWN -> R.string.web_stage_skipped
})

@Composable private fun websiteOutcomeLabel(outcome: WebsiteDiagnosticOutcome): String = stringResource(when (outcome) {
    WebsiteDiagnosticOutcome.HEALTHY -> R.string.web_stage_success
    WebsiteDiagnosticOutcome.ATTENTION -> R.string.web_stage_attention
    WebsiteDiagnosticOutcome.FAILED -> R.string.web_stage_failed
    WebsiteDiagnosticOutcome.STOPPED -> R.string.web_site_cancelled
    WebsiteDiagnosticOutcome.NETWORK_CHANGED -> R.string.web_tls_network_changed
})

@Composable private fun websiteOutcomeDescription(outcome: WebsiteDiagnosticOutcome): String = stringResource(when (outcome) {
    WebsiteDiagnosticOutcome.HEALTHY -> R.string.web_site_healthy
    WebsiteDiagnosticOutcome.ATTENTION -> R.string.web_site_attention
    WebsiteDiagnosticOutcome.FAILED -> R.string.web_site_failed
    WebsiteDiagnosticOutcome.STOPPED -> R.string.web_site_cancelled_desc
    WebsiteDiagnosticOutcome.NETWORK_CHANGED -> R.string.web_site_changed
})

@Composable private fun transportLabel(path: HttpTransportPath?): String = stringResource(when (path) {
    HttpTransportPath.DIRECT -> R.string.web_site_transport_direct
    HttpTransportPath.HTTP_PROXY -> R.string.web_site_transport_proxy
    HttpTransportPath.PAC -> R.string.web_site_transport_pac
    HttpTransportPath.UNKNOWN_PROXY -> R.string.web_site_transport_unknown
    null -> R.string.web_unknown
})

@Composable private fun protocolLabel(protocol: HttpProtocol?): String = when (protocol) {
    HttpProtocol.HTTP_1_0 -> "HTTP/1.0"
    HttpProtocol.HTTP_1_1 -> "HTTP/1.1"
    HttpProtocol.HTTP_2 -> "HTTP/2"
    HttpProtocol.UNKNOWN, null -> stringResource(R.string.web_unknown)
}

@Composable private fun tcpOutcomeLabel(outcome: TcpConnectOutcome): String = stringResource(when (outcome) {
    TcpConnectOutcome.CONNECTED -> R.string.web_tcp_connected_short
    TcpConnectOutcome.REFUSED -> R.string.web_tcp_refused_short
    TcpConnectOutcome.TIMEOUT -> R.string.web_tcp_timeout_short
    TcpConnectOutcome.NO_ROUTE -> R.string.web_tcp_no_route_short
    TcpConnectOutcome.NETWORK_UNREACHABLE -> R.string.web_tcp_unreachable_short
    TcpConnectOutcome.ERROR -> R.string.web_tcp_error_short
})

@Composable private fun tcpAttemptsLabel(attempts: List<WebsiteTcpAttemptEvidence>): String {
    val lines = mutableListOf<String>()
    for (attempt in attempts) {
        lines += "${attempt.address}: ${tcpOutcomeLabel(attempt.outcome)}"
    }
    return lines.joinToString("\n")
}

@Composable private fun websiteFinding(finding: WebsiteDiagnosticFinding): String = stringResource(when (finding.code) {
    WebsiteFindingCode.DNS_RESOLUTION_SUCCEEDED -> R.string.web_finding_dns_success
    WebsiteFindingCode.DNS_NXDOMAIN -> R.string.web_finding_dns_nxdomain
    WebsiteFindingCode.DNS_NO_RECORDS -> R.string.web_finding_dns_no_records
    WebsiteFindingCode.DNS_TIMEOUT -> R.string.web_finding_dns_timeout
    WebsiteFindingCode.DNS_FAILED -> R.string.web_finding_dns_failed
    WebsiteFindingCode.FAKE_IP_DETECTED -> R.string.web_site_fake_ip
    WebsiteFindingCode.TCP_CONNECTED -> R.string.web_tls_tcp_connected
    WebsiteFindingCode.TCP_REFUSED -> R.string.web_tls_tcp_refused
    WebsiteFindingCode.TCP_TIMEOUT -> R.string.web_tls_tcp_timeout
    WebsiteFindingCode.TCP_UNREACHABLE -> R.string.web_tls_tcp_unreachable
    WebsiteFindingCode.TLS_HANDSHAKE_SUCCEEDED -> R.string.web_tls_handshake_success
    WebsiteFindingCode.TLS_TRUST_FAILED -> R.string.web_tls_untrusted
    WebsiteFindingCode.TLS_HOSTNAME_MISMATCH -> R.string.web_tls_hostname_mismatch
    WebsiteFindingCode.CERTIFICATE_EXPIRED -> R.string.web_tls_expired
    WebsiteFindingCode.CERTIFICATE_NOT_YET_VALID -> R.string.web_tls_future
    WebsiteFindingCode.HTTP_RESPONDED -> R.string.web_finding_http_responded
    WebsiteFindingCode.HTTP_AUTH_REQUIRED -> R.string.web_finding_http_401
    WebsiteFindingCode.HTTP_PROXY_AUTH_REQUIRED -> R.string.web_finding_http_407
    WebsiteFindingCode.HTTP_FORBIDDEN -> R.string.web_finding_http_403
    WebsiteFindingCode.HTTP_NOT_FOUND -> R.string.web_finding_http_404
    WebsiteFindingCode.HTTP_RATE_LIMITED -> R.string.web_finding_http_429
    WebsiteFindingCode.HTTP_SERVER_ERROR -> R.string.web_finding_http_5xx
    WebsiteFindingCode.HTTP_FAILED -> R.string.web_finding_http_failed
    WebsiteFindingCode.REDIRECTED -> R.string.web_finding_redirect
    WebsiteFindingCode.REDIRECT_LOOP -> R.string.web_finding_redirect_loop
    WebsiteFindingCode.TOO_MANY_REDIRECTS -> R.string.web_finding_redirect_limit
    WebsiteFindingCode.REDIRECT_INVALID -> R.string.web_finding_redirect_invalid
    WebsiteFindingCode.INVALID_TARGET -> R.string.web_finding_invalid_target
    WebsiteFindingCode.SESSION_TIMEOUT -> R.string.web_finding_session_timeout
    WebsiteFindingCode.CLEARTEXT_USED -> R.string.web_site_cleartext
    WebsiteFindingCode.HTTPS_DOWNGRADE -> R.string.web_finding_downgrade
    WebsiteFindingCode.PROXY_PATH_USED -> R.string.web_site_proxy_notice
    WebsiteFindingCode.VPN_ACTIVE -> R.string.web_site_vpn_notice
    WebsiteFindingCode.NETWORK_CHANGED -> R.string.web_site_changed
})

@Composable private fun websiteRecommendation(recommendation: WebsiteDiagnosticRecommendation): String = stringResource(when (recommendation.code) {
    WebsiteRecommendationCode.CHECK_DOMAIN_SPELLING -> R.string.web_rec_domain
    WebsiteRecommendationCode.CHECK_DNS_CONFIGURATION -> R.string.web_rec_dns
    WebsiteRecommendationCode.CHECK_SERVICE_PORT -> R.string.web_rec_host_port
    WebsiteRecommendationCode.CHECK_FIREWALL_OR_PATH -> R.string.web_rec_firewall
    WebsiteRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED -> R.string.web_rec_private_ca
    WebsiteRecommendationCode.RENEW_CERTIFICATE -> R.string.web_rec_renew
    WebsiteRecommendationCode.CHECK_DEVICE_AND_SERVER_TIME -> R.string.web_rec_time
    WebsiteRecommendationCode.CHECK_CERTIFICATE_SAN -> R.string.web_rec_san
    WebsiteRecommendationCode.CHECK_AUTHENTICATION -> R.string.web_rec_auth
    WebsiteRecommendationCode.CHECK_ACCESS_POLICY -> R.string.web_rec_policy
    WebsiteRecommendationCode.CHECK_RESOURCE_PATH -> R.string.web_rec_path
    WebsiteRecommendationCode.RETRY_AFTER_RATE_LIMIT -> R.string.web_rec_rate
    WebsiteRecommendationCode.CHECK_SERVER_OR_UPSTREAM -> R.string.web_rec_server
    WebsiteRecommendationCode.CHECK_PROXY_CONFIGURATION -> R.string.web_rec_proxy
    WebsiteRecommendationCode.RETRY_ON_STABLE_NETWORK -> R.string.web_rec_stable
})
