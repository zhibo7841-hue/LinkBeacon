package com.networktoolbox.feature.rdap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.*
import com.networktoolbox.core.network.rdap.*
import java.text.DateFormat
import java.util.Date
import java.time.ZoneId

@Composable
fun RdapScreen(state: RdapUiState,onInput:(String)->Unit,onRequest:()->Unit,
    onStop:()->Unit,onBack:()->Unit,onProtocol:(RegistrationProtocol)->Unit,onCopy:(String)->Unit) {
    BackHandler {onStop();onBack()}
    var menu by remember {mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var details by rememberSaveable(state.result) {mutableStateOf(false)}
    var more by rememberSaveable(state.result) {mutableStateOf(false)}
    var notices by rememberSaveable(state.result) {mutableStateOf(false)}
    ToolScreenLayout {
        ToolScreenHeader(stringResource(R.string.rdap_title),icon=Icons.Outlined.Language,
            accent=NetworkToolAccent.CYAN,onBack={onStop();onBack()})
        if(!state.running) OutlinedNetworkCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick={menu=true},modifier=Modifier.testTag("rdap_protocol"),contentPadding=PaddingValues(horizontal=10.dp)) {
                        Text(state.protocol.name)
                        Icon(Icons.Outlined.ArrowDropDown,contentDescription=null)
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        RegistrationProtocol.entries.forEach {protocol ->
                            DropdownMenuItem(text={Text(protocol.name)},onClick={menu=false;onProtocol(protocol)},
                                modifier=Modifier.testTag("rdap_protocol_${protocol.name}"))
                        }
                    }
                }
                OutlinedTextField(state.input,onValueChange=onInput,
                    label={Text(stringResource(R.string.rdap_domain_or_ip))},
                    singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
                    modifier=Modifier.weight(1f).testTag("rdap_input"))
            }
            PrimaryActionButton(onClick={focus.clearFocus();keyboard?.hide();onRequest()},modifier=Modifier.fillMaxWidth().testTag("rdap_query")) {
                Text(stringResource(R.string.rdap_query))
            }
        }
        if(state.running) ToolRunningSection(Modifier.testTag("rdap_running")) {
            Text(stringResource(R.string.rdap_running),style=MaterialTheme.typography.titleMedium)
            Text(state.target?.query ?: state.input)
            Text(state.protocol.name)
            Text(stringResource(when(state.phase) {
                RdapPhase.BOOTSTRAP -> R.string.rdap_phase_bootstrap
                RdapPhase.RESOLVING -> R.string.rdap_phase_resolve
                RdapPhase.QUERYING -> if(state.protocol==RegistrationProtocol.RDAP) R.string.rdap_phase_query else R.string.whois_phase_query
                RdapPhase.PARSING -> R.string.rdap_phase_parse
            }))
            DestructiveActionButton(onClick=onStop,modifier=Modifier.fillMaxWidth().testTag("rdap_stop")) {Text(stringResource(R.string.rdap_stop))}
        }
        state.result?.let {r ->
            val view=remember(r) {registrationPresentation(r)}
            ToolResultSection(Modifier.testTag("rdap_result")) {
                val status=if(r.status==RdapStatus.SUCCESS) when(view.content) {
                    RegistrationContent.MAIN_FIELDS->stringResource(R.string.registration_main)
                    RegistrationContent.PARTIAL->stringResource(R.string.registration_partial)
                    RegistrationContent.MULTIPLE->stringResource(R.string.registration_multiple,view.records.size)
                    RegistrationContent.UPSTREAM_ONLY->stringResource(R.string.registration_upstream)
                    else->stringResource(R.string.registration_unparsed)
                } else if(r.protocol==RegistrationProtocol.WHOIS && r.source!=null && r.status in setOf(RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.DNS_FAILED))
                    stringResource(if(r.domain!=null && r.source=="whois://${WhoisIpData.IANA}:43") R.string.rdap_directory_failed else R.string.registration_connect_failed)
                else stringResource(statusText(r.status))
                val visual=when {
                    r.status==RdapStatus.CANCELLED->StatusVisualState.CANCELLED
                    r.status==RdapStatus.SUCCESS && view.content==RegistrationContent.MAIN_FIELDS->StatusVisualState.NORMAL
                    r.status in setOf(RdapStatus.SUCCESS,RdapStatus.NOT_FOUND,RdapStatus.WHOIS_SERVICE_NOT_FOUND)->StatusVisualState.NOTICE
                    else->StatusVisualState.WARNING
                }
                val query=r.input?.query ?: r.domain?.ascii
                SelectionContainer {
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        ToolStatusSummary(query ?: stringResource(R.string.rdap_title),visual,label=r.protocol.name)
                        Text(status,modifier=Modifier.testTag("registration_status"))
                        r.input?.original?.takeIf {it!=query}?.let {Text(stringResource(R.string.registration_input_value,it),style=MaterialTheme.typography.bodySmall)}
                    }
                }
                if(r.status==RdapStatus.INVALID_INPUT) Text(stringResource(R.string.rdap_input_help))
                if(r.status==RdapStatus.NOT_FOUND) Text(stringResource(R.string.rdap_not_for_sale))
                if(r.requests==0 && r.status==RdapStatus.WHOIS_SERVICE_NOT_FOUND) Text(stringResource(R.string.registration_no_request))
                if(r.status!=RdapStatus.SUCCESS && view.content==RegistrationContent.UPSTREAM_ONLY) Text(stringResource(R.string.registration_upstream))
                val sharedOrg=commonRegistrationField(view.records,R.string.rdap_org)
                val sharedSource=commonRegistrationSource(view.records)
                SelectionContainer {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.testTag("registration_main_fields")) {
                        if (view.records.any { record -> (record.fields + record.more).any { isRegistrationDate(it.label) } }) {
                            Text(stringResource(R.string.registration_dates_zone, ZoneId.systemDefault().id),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        sharedOrg?.let {Fact(R.string.rdap_org,it)}
                        view.records.forEachIndexed {index,record ->
                            val cidr=record.more.firstOrNull {it.label==R.string.whois_cidr}
                            if(view.records.size>1) {
                                if(index>0) HorizontalDivider()
                                Text(stringResource(if(index==0) R.string.registration_specific else R.string.registration_other),style=MaterialTheme.typography.labelLarge)
                            }
                            if(cidr!=null) Fact(cidr.label,cidr.value)
                            record.fields.filterNot {(it.label==R.string.rdap_org && sharedOrg!=null) || (it.label==R.string.rdap_ip_range && cidr!=null)}.forEach {Field(it)}
                            if(sharedSource==null) record.source?.let {Fact(R.string.rdap_service,it)}
                            if(record.summaryOnly) Text(stringResource(R.string.registration_summary_only),style=MaterialTheme.typography.bodySmall)
                        }
                        r.registration?.let {d -> Fact(R.string.rdap_dnssec,stringResource(dnssecText(d.dnssec))) }
                        sharedSource?.let {Fact(R.string.rdap_service,it)}
                    }
                }
                if(view.records.any {it.fields.isNotEmpty()}) {
                    val copied=listOfNotNull(query)+view.records.flatMap {record->
                        (record.fields+record.more).map {"${stringResource(it.label)}: ${it.value}"}+listOfNotNull(record.source)
                    }+listOfNotNull(r.registration?.dnssec?.let {"DNSSEC: "+stringResource(dnssecText(it))})
                    TextButton(onClick={onCopy(copied.joinToString("\n"))},modifier=Modifier.testTag("registration_copy_fields")) {Text(stringResource(R.string.registration_copy_fields))}
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if(view.records.isNotEmpty()) {
                        DisclosureButton("registration_more",R.string.registration_more,more) {more=!more}
                        if(more) SelectionContainer {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.testTag("registration_more_content")) {
                                view.records.forEach {record ->
                                    if(view.records.size>1) Text(record.more.firstOrNull {it.label==R.string.whois_cidr}?.value
                                        ?: record.fields.firstOrNull {it.label==R.string.rdap_ip_range}?.value.orEmpty(),style=MaterialTheme.typography.titleSmall)
                                    if(record.more.any {it.label==R.string.whois_cidr}) record.fields.firstOrNull {it.label==R.string.rdap_ip_range}?.let {Field(it)}
                                    record.more.filterNot {it.label==R.string.whois_cidr}.forEach {Field(it)}
                                    record.source?.let {Fact(R.string.rdap_service,it)}
                                    (record.fields+record.more).filter {isRegistrationDate(it.label) || it.label==R.string.rdap_domain_status || it.label==R.string.rdap_recorded_status}.forEach {
                                        FactText(stringResource(it.label)+" · "+stringResource(R.string.registration_original_value),it.value)
                                    }
                                    if(record.more.any {it.label==R.string.rdap_country}) Text(stringResource(R.string.rdap_country_note),style=MaterialTheme.typography.bodySmall)
                                }
                                if(r.registration?.restricted==true) Text(stringResource(R.string.rdap_restricted))
                            }
                        }
                    }
                    if(r.registration?.notices?.isNotEmpty()==true) {
                        DisclosureButton("registration_notices",R.string.registration_notices,notices) {notices=!notices}
                        if(notices) SelectionContainer {Column(Modifier.testTag("registration_notices_content"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            r.registration?.notices?.forEach {Text(it)}
                        }}
                    }
                    DisclosureButton("rdap_details",R.string.registration_process,details) {details=!details}
                    if(details) {
                        SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Fact(R.string.rdap_source,r.source)
                            Fact(R.string.rdap_observed,DateFormat.getDateTimeInstance().format(Date(r.observedAt)))
                            if(r.requests>0) {
                                if(r.durationMs>0) Fact(R.string.rdap_duration,"${r.durationMs} ms")
                                Fact(R.string.rdap_requests,r.requests.toString())
                            }
                            if(r.evidence.mappedServiceAddress) Text(stringResource(R.string.registration_mapping_note))
                            if(r.evidence.systemProxyUsed) Text(stringResource(R.string.registration_proxy_note))
                            Fact(R.string.rdap_http,r.httpStatus?.toString())
                            Fact(R.string.rdap_bootstrap,r.bootstrapPublication)
                            if(r.protocol==RegistrationProtocol.WHOIS) Fact(R.string.rdap_directory_source,WhoisServers.VERSION+"\n"+WhoisServers.SOURCE)
                            r.reason?.let {Fact(R.string.rdap_reason,it.name)}
                            r.evidence.addressClasses.filterNot {r.evidence.mappedServiceAddress && it==RdapAddressClass.BENCHMARK}.forEach {Text(stringResource(addressClassText(it)))}
                            r.vpn?.let {Fact(R.string.rdap_vpn,stringResource(if(it) R.string.rdap_yes else R.string.rdap_no))}
                            r.whoisSteps.forEachIndexed {index,step ->
                                Text("${index+1}. ${step.server} · ${step.target}",style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(if(step.status==RdapStatus.SUCCESS) R.string.whois_received else statusText(step.status)))
                                if(step.ianaAllocation) Text(stringResource(R.string.registration_iana_source))
                            }
                        }}
                        if(view.raw.isNotEmpty()) TextButton(onClick={onCopy(view.allRaw())},modifier=Modifier.testTag("registration_copy_all_raw")) {Text(stringResource(R.string.registration_copy_all_raw))}
                        view.raw.forEachIndexed {index,raw ->
                            HorizontalDivider()
                            Text(raw.source,style=MaterialTheme.typography.titleSmall)
                            if(view.raw.size>1) TextButton(onClick={onCopy(raw.text)},modifier=Modifier.testTag("registration_copy_raw_$index")) {Text(stringResource(R.string.registration_copy_raw))}
                            SelectionContainer {Text(raw.text,modifier=Modifier.testTag("whois_step_$index"))}
                        }
                        SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(if(r.protocol==RegistrationProtocol.RDAP) R.string.rdap_disclosure else R.string.whois_disclosure),style=MaterialTheme.typography.bodySmall)
                            Text(stringResource(if(r.protocol==RegistrationProtocol.RDAP) R.string.rdap_limitations else R.string.whois_limitations),style=MaterialTheme.typography.bodySmall)
                        }}
                    }
                }
            }
        }
    }
}

@Composable private fun DisclosureButton(tag:String,label:Int,expanded:Boolean,onClick:()->Unit) {
    TextButton(onClick=onClick,modifier=Modifier.testTag(tag)) {
        Text(stringResource(label)+if(expanded) " −" else " +")
    }
}
@Composable private fun Field(field:RegistrationField) {
    val locale=LocalConfiguration.current.locales[0]
    val value=when {
        isRegistrationDate(field.label)->formatRegistrationDate(field.value,locale,ZoneId.systemDefault())
        else->field.value
    }
    val display=if(field.label==R.string.rdap_domain_status || field.label==R.string.rdap_recorded_status) {
        val strings=field.value.lines().map {code->registrationStatusLabel(code)?.let {stringResource(it)} ?: code}
        strings.joinToString("\n")
    } else value
    Fact(field.label,display)
}
@Composable private fun Fact(label:Int,value:String?) {if(!value.isNullOrBlank()) FactText(stringResource(label),value,"registration_field_$label")}
@Composable private fun FactText(label:String,value:String,tag:String="") {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth.value / androidx.compose.ui.platform.LocalDensity.current.fontScale < 280f
        val valueModifier = if(tag.isEmpty()) Modifier else Modifier.testTag(tag)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, modifier=valueModifier)
            }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(0.4f))
            Text(value,modifier=Modifier.weight(0.6f).then(valueModifier))
        }
    }
}
fun dnssecText(value:RdapDnssec):Int=when(value) {
    RdapDnssec.SIGNED->R.string.rdap_signed
    RdapDnssec.UNSIGNED->R.string.rdap_unsigned
    RdapDnssec.NOT_PROVIDED->R.string.registration_dnssec_unknown
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
    RdapStatus.WHOIS_SERVICE_NOT_FOUND -> R.string.registration_no_service
}
