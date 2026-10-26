package com.networktoolbox.core.network.rdap

/** Categories only. Never contains a queried name, address, proxy identity or exception text. */
enum class RdapAddressClass {
    PUBLIC_IPV4, PUBLIC_IPV6, UNSPECIFIED, PRIVATE, LOOPBACK, LINK_LOCAL, SHARED_CGNAT,
    DOCUMENTATION, BENCHMARK, MULTICAST, RESERVED,
}
enum class RdapReason { ENDPOINT_URI, DNS_ADDRESS_POLICY, CONNECT_ADDRESS_POLICY, SOCKET_ADDRESS_POLICY, PROXY_PATH }
enum class RdapTraceStep {
    NETWORK_CAPTURED, ENDPOINT_ALLOWED, DNS_STARTED, DNS_COMPLETED, CANONICALIZED, DNS_REJECTED,
    CONNECT_STARTED, TCP_CONNECTED, TLS_COMPLETED, CONNECT_REJECTED, SOCKET_REJECTED,
    HTTP_HEADERS_WRITTEN, HTTP_RESPONSE, BODY_READ, PARSED, SESSION_CLOSED,
}
data class RdapTraceEvent(val step:RdapTraceStep,val value:Int?=null,
    val addressClasses:Set<RdapAddressClass> = emptySet())
fun interface RdapObserver {
    fun record(event:RdapTraceEvent)
    companion object {val NONE=RdapObserver {}}
}
internal fun RdapObserver.emit(step:RdapTraceStep,value:Int?=null,classes:Set<RdapAddressClass> = emptySet()) {
    runCatching {record(RdapTraceEvent(step,value,classes.toSet()))}
}
data class RdapTransportEvidence(
    val dnsCompleted:Boolean=false,val tcpConnected:Boolean=false,val tlsCompleted:Boolean=false,
    val httpExchanges:Int=0,val httpResponses:Int=0,val bodiesRead:Int=0,
    val addressClasses:Set<RdapAddressClass> = emptySet(),val privateDns:Boolean?=null,
    val proxyConfigured:Boolean?=null,
)
