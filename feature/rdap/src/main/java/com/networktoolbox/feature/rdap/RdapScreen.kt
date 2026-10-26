package com.networktoolbox.feature.rdap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.*
import com.networktoolbox.core.network.rdap.*
import java.text.DateFormat
import java.util.Date

@Composable
fun RdapScreen(state: RdapUiState,onInput:(String)->Unit,onRequest:()->Unit,onConfirm:()->Unit,
    onDismiss:()->Unit,onStop:()->Unit,onBack:()->Unit) {
    BackHandler {onStop();onBack()}
    val focus=LocalFocusManager.current
    var details by rememberSaveable(state.result) {mutableStateOf(false)}
    ToolScreenLayout {
        ToolScreenHeader(stringResource(R.string.rdap_title),icon=Icons.Outlined.Language,
            accent=NetworkToolAccent.CYAN,onBack={onStop();onBack()})
        Text(stringResource(R.string.rdap_intro))
        if(!state.running) ToolInputSection(title=stringResource(R.string.rdap_domain)) {
            OutlinedTextField(state.input,onValueChange=onInput,label={Text(stringResource(R.string.rdap_domain))},
                singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
                modifier=Modifier.fillMaxWidth().testTag("rdap_input"))
            PrimaryActionButton(onClick={focus.clearFocus();onRequest()},modifier=Modifier.fillMaxWidth().testTag("rdap_query")) {
                Text(stringResource(R.string.rdap_query))
            }
            Text(stringResource(R.string.rdap_privacy_short),style=MaterialTheme.typography.bodySmall)
        }
        if(state.running) ToolRunningSection(Modifier.testTag("rdap_running")) {
            Text(stringResource(R.string.rdap_running),style=MaterialTheme.typography.titleMedium)
            Text(state.input)
            Text(stringResource(when(state.phase) {
                RdapPhase.BOOTSTRAP -> R.string.rdap_phase_bootstrap
                RdapPhase.RESOLVING -> R.string.rdap_phase_resolve
                RdapPhase.QUERYING -> R.string.rdap_phase_query
                RdapPhase.PARSING -> R.string.rdap_phase_parse
            }))
            DestructiveActionButton(onClick=onStop,modifier=Modifier.fillMaxWidth().testTag("rdap_stop")) {Text(stringResource(R.string.rdap_stop))}
        }
        state.result?.let {r -> ToolResultSection(Modifier.testTag("rdap_result")) {
            ToolStatusSummary(stringResource(if(r.status==RdapStatus.UNSAFE_ENDPOINT && r.reason==RdapReason.DNS_ADDRESS_POLICY)
                R.string.rdap_dns_policy_summary else statusText(r.status)),when(r.status) {
                RdapStatus.SUCCESS -> StatusVisualState.NORMAL
                RdapStatus.CANCELLED -> StatusVisualState.CANCELLED
                RdapStatus.NOT_FOUND,RdapStatus.UNSUPPORTED,RdapStatus.HTTP_ONLY,RdapStatus.PROXY_UNSUPPORTED -> StatusVisualState.NOTICE
                else -> StatusVisualState.WARNING
            })
            r.domain?.let {Text(it.unicode,style=MaterialTheme.typography.titleMedium);if(it.unicode!=it.ascii) Text(it.ascii)}
            if(r.status==RdapStatus.NOT_FOUND) Text(stringResource(R.string.rdap_not_for_sale))
            if(r.status==RdapStatus.INVALID_INPUT) Text(stringResource(R.string.rdap_input_help))
            r.registration?.let {data ->
                Fact(R.string.rdap_registrar,data.registrar)
                Fact(R.string.rdap_registered,data.registeredAt)
                Fact(R.string.rdap_changed,data.changedAt)
                Fact(R.string.rdap_expires,data.expiresAt)
                if(data.restricted) Text(stringResource(R.string.rdap_restricted))
            }
            Fact(R.string.rdap_service,r.source?.let {runCatching {java.net.URI(it).host}.getOrNull()})
            Fact(R.string.rdap_observed,DateFormat.getDateTimeInstance().format(Date(r.observedAt)))
            TextButton(onClick={details=!details},modifier=Modifier.testTag("rdap_details")) {
                Text(stringResource(if(details) R.string.rdap_hide else R.string.rdap_details))
            }
            if(details) {
                Fact(R.string.rdap_ascii,r.domain?.ascii)
                Fact(R.string.rdap_source,r.source)
                Fact(R.string.rdap_duration,"${r.durationMs} ms")
                Fact(R.string.rdap_requests,r.requests.toString())
                Fact(R.string.rdap_exchanges,r.evidence.httpExchanges.toString())
                Fact(R.string.rdap_http,r.httpStatus?.toString())
                r.reason?.let {Fact(R.string.rdap_reason,it.name)}
                if(r.evidence.addressClasses.isNotEmpty()) Column {
                    Text(stringResource(R.string.rdap_address_class),style=MaterialTheme.typography.labelMedium)
                    r.evidence.addressClasses.forEach {Text(stringResource(addressClassText(it)))}
                }
                Fact(R.string.rdap_bootstrap,r.bootstrapPublication)
                Fact(R.string.rdap_directory_time,r.bootstrapFetchedAt?.let {DateFormat.getDateTimeInstance().format(Date(it))})
                Fact(R.string.rdap_directory_source,r.bootstrapSource?.let {stringResource(when(it) {
                    RdapBootstrapSource.BUNDLED -> R.string.rdap_bundled
                    RdapBootstrapSource.CACHE -> R.string.rdap_cached
                    RdapBootstrapSource.UPDATED -> R.string.rdap_updated
                })})
                r.vpn?.let {Fact(R.string.rdap_vpn,stringResource(if(it) R.string.rdap_yes else R.string.rdap_no))}
                Text(stringResource(R.string.rdap_route_note))
                r.registration?.let {data ->
                    Fact(R.string.rdap_domain_status,data.statuses.takeIf {it.isNotEmpty()}?.joinToString("\n"))
                    Fact(R.string.rdap_nameservers,data.nameservers.takeIf {it.isNotEmpty()}?.joinToString("\n"))
                    Fact(R.string.rdap_dnssec,stringResource(when(data.dnssec) {
                        RdapDnssec.SIGNED -> R.string.rdap_signed
                        RdapDnssec.UNSIGNED -> R.string.rdap_unsigned
                        RdapDnssec.NOT_PROVIDED -> R.string.rdap_missing
                    }))
                    data.notices.forEach {Text(it)} // Plain text only: never HTML, links, or contact actions.
                }
                Text(stringResource(R.string.rdap_limitations))
            }
        }}
    }
    if(state.consent) AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.rdap_consent_title))},
        text={Text(stringResource(R.string.rdap_disclosure))},
        confirmButton={TextButton(onClick=onConfirm,modifier=Modifier.testTag("rdap_confirm")) {Text(stringResource(R.string.rdap_continue))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.rdap_cancel))}})
}
fun addressClassText(value:RdapAddressClass):Int=when(value) {
    RdapAddressClass.PUBLIC_IPV4 -> R.string.rdap_public_ipv4
    RdapAddressClass.PUBLIC_IPV6 -> R.string.rdap_public_ipv6
    RdapAddressClass.PRIVATE -> R.string.rdap_private_address
    RdapAddressClass.LOOPBACK -> R.string.rdap_loopback
    RdapAddressClass.LINK_LOCAL -> R.string.rdap_link_local
    RdapAddressClass.SHARED_CGNAT -> R.string.rdap_cgnat
    RdapAddressClass.DOCUMENTATION -> R.string.rdap_documentation
    RdapAddressClass.BENCHMARK -> R.string.rdap_benchmark
    RdapAddressClass.MULTICAST -> R.string.rdap_multicast
    RdapAddressClass.UNSPECIFIED -> R.string.rdap_unspecified
    RdapAddressClass.RESERVED -> R.string.rdap_reserved
}
@Composable private fun Fact(label: Int,value: String?) {
    Column {Text(stringResource(label),style=MaterialTheme.typography.labelMedium)
        Text(value ?: stringResource(R.string.rdap_missing))}
}
fun statusText(status: RdapStatus): Int = when(status) {
    RdapStatus.SUCCESS -> R.string.rdap_success
    RdapStatus.INVALID_INPUT -> R.string.rdap_invalid
    RdapStatus.UNSUPPORTED -> R.string.rdap_unsupported
    RdapStatus.HTTP_ONLY -> R.string.rdap_http_only
    RdapStatus.NOT_FOUND -> R.string.rdap_not_found
    RdapStatus.ACCESS_RESTRICTED -> R.string.rdap_access
    RdapStatus.QUERY_REJECTED -> R.string.rdap_rejected
    RdapStatus.RATE_LIMITED -> R.string.rdap_rate
    RdapStatus.SERVER_ERROR -> R.string.rdap_server_error
    RdapStatus.NETWORK_UNAVAILABLE -> R.string.rdap_offline
    RdapStatus.DNS_FAILED -> R.string.rdap_dns_failed
    RdapStatus.TLS_FAILED -> R.string.rdap_tls_failed
    RdapStatus.TIMEOUT -> R.string.rdap_timeout
    RdapStatus.CANCELLED -> R.string.rdap_cancelled
    RdapStatus.INVALID_RESPONSE -> R.string.rdap_invalid_response
    RdapStatus.RESPONSE_TOO_LARGE -> R.string.rdap_too_large
    RdapStatus.NETWORK_CHANGED -> R.string.rdap_network_changed
    RdapStatus.REDIRECT_REJECTED -> R.string.rdap_redirect
    RdapStatus.UNSAFE_ENDPOINT -> R.string.rdap_unsafe
    RdapStatus.PROXY_UNSUPPORTED -> R.string.rdap_proxy
    RdapStatus.BOOTSTRAP_UNAVAILABLE -> R.string.rdap_directory_failed
    RdapStatus.BUSY -> R.string.rdap_busy
}
