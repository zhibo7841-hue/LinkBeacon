package com.networktoolbox.core.network.rdap

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class Registration185Test {
    private val dispatcher=StandardTestDispatcher()
    private val domain=requireNotNull(RdapDomainSyntax.ascii("zzb.asia"))
    private val ip=requireNotNull(RegistrationIp.parse("173.249.200.19"))
    private val body="Domain Name: ZZB.ASIA\r\nRegistrar: Example Registrar\r\nCreation Date: 2020-01-01T00:00:00Z\r\nRegistry Expiry Date: 2030-01-01T00:00:00Z\r\nUpdated Date: 2025-01-01T00:00:00Z\r\nDomain Status: ok\r\nDomain Status: ok\r\nName Server: NS1.EXAMPLE.COM\r\nName Server: ns1.example.com\r\nName Server: ns2.example.com\r\nDNSSEC: unsigned\r\nRegistrant Organization: Example Registrant\r\n"
    private class Session: RdapHttpSession {
        override var changed=false;override val vpn=false
        val calls=mutableListOf<Pair<String,String>>();var closed=0;var authorized:String?=null
        var response:suspend (String,String)->String={_,_->error("unexpected")}
        override suspend fun get(url:String,limit:Int,remainingMs:Long,onProgress:(RdapPhase)->Unit):RdapHttpResponse=error("no protocol fallback")
        override suspend fun whois(server:String,query:String,limit:Int,onProgress:(RdapPhase)->Unit):ByteArray {
            calls+=server to query;return response(server,query).toByteArray()
        }
        override fun authorizeWhoisDomain(ianaText:String,domain:RdapDomain) {authorized=WhoisServers.fromIana(ianaText,domain)}
        override fun close(){closed++}
    }
    private suspend fun run(s:Session,d:RdapDomain=domain)=RegistrationLookup(RunRdapLookup {_,_->error("no RDAP")},RdapTransport {s},dispatcher)
        .run(RegistrationInput(d.ascii,domain=d),RegistrationProtocol.WHOIS) {}
    @Test fun asiaReviewedOfficialServiceAndMetadata() {
        assertEquals("whois.nic.asia",WhoisServers.select(domain));assertTrue(WhoisServers.VERSION.contains("2026-10-28"))
        assertEquals("whois.nic.asia",WhoisServers.fromIana("domain: ASIA\nwhois: whois.nic.asia",domain))
    }
    @Test fun missingBundledSuffixDiscoversOnlyExactIanaTldService()=runTest(dispatcher) {
        val s=Session();s.response={server,query->if(server==WhoisIpData.IANA) {assertEquals("org",query);"domain: ORG\nwhois: whois.example.org"} else "Domain Name: EXAMPLE.ORG"}
        val r=run(s,requireNotNull(RdapDomainSyntax.ascii("example.org")))
        assertEquals(RdapStatus.SUCCESS,r.status);assertEquals(2,r.requests);assertEquals("whois.example.org",s.authorized)
        assertEquals("example.org",s.calls.last().second);assertEquals(1,s.closed)
    }
    @Test fun directoryAbsenceIsNotConnectionFailureOrPrivacyRedaction()=runTest(dispatcher) {
        val s=Session();s.response={_,_->"domain: ORG\norganisation: Example"}
        val r=run(s,requireNotNull(RdapDomainSyntax.ascii("example.org")))
        assertEquals(RdapStatus.WHOIS_SERVICE_NOT_FOUND,r.status);assertEquals(1,s.calls.size);assertNull(r.registration)
    }
    @Test fun knownServiceTimeoutRemainsTimeoutNoOtherServerAttempt()=runTest(dispatcher) {
        val s=Session();s.response={_,_->throw RdapFailure(RdapStatus.TIMEOUT)}
        val r=run(s);assertEquals(RdapStatus.TIMEOUT,r.status);assertEquals("whois.nic.asia",s.calls.single().first)
        assertEquals(1,r.whoisSteps.size);assertEquals(1,s.closed)
    }
    @Test fun directoryCannotAuthorizeMismatchedOrMalformedService() {
        for(text in listOf("domain: com\nwhois: whois.nic.asia","domain: asia\nwhois: 127.0.0.1",
            "domain: asia\nwhois: https://whois.nic.asia/a","domain: asia\nwhois: whois.nic.asia:43",
            "domain: asia\nwhois: whois.nic.asia\nwhois: whois.other.com")) {
            try {WhoisServers.fromIana(text,domain);fail(text)} catch(_:RdapFailure) {}
        }
    }
    @Test fun cancelDirectoryStopsBeforeSelectedRegistry()=runTest(dispatcher) {
        val s=Session();s.response={_,_->awaitCancellation()}
        val job=launch {run(s,requireNotNull(RdapDomainSyntax.ascii("example.org")))};runCurrent();job.cancelAndJoin()
        assertEquals(1,s.calls.size);assertEquals(1,s.closed)
    }
    @Test fun changedNetworkDoesNotAuthorizeLateDirectory()=runTest(dispatcher) {
        val s=Session();s.response={_,_->s.changed=true;"domain: org\nwhois: whois.example.org"}
        assertEquals(RdapStatus.NETWORK_CHANGED,run(s,requireNotNull(RdapDomainSyntax.ascii("example.org"))).status)
        assertNull(s.authorized);assertEquals(1,s.calls.size)
    }
    @Test fun domainFieldsRolesDatesDedupAndRawIntegrity()=runTest(dispatcher) {
        val s=Session();s.response={_,_->body};val r=run(s);val d=requireNotNull(r.registration)
        assertEquals(body,r.whoisSteps.single().text);assertEquals("Example Registrar",d.registrar)
        assertEquals("Example Registrant",d.organization);assertEquals("2030-01-01T00:00:00Z",d.expiresAt)
        assertEquals("2025-01-01T00:00:00Z",d.changedAt);assertEquals(1,d.statuses.size);assertEquals(2,d.nameservers.size)
        assertEquals(RdapDnssec.UNSIGNED,d.dnssec)
    }
    @Test fun unrelatedDomainOrConflictingRegistrarCannotBecomeRegistration() {
        assertNull(WhoisDomainData.parse(body.replace("ZZB.ASIA","OTHER.ASIA"),domain,"whois.nic.asia"))
        assertNull(WhoisDomainData.parse(body+"Registrar: Other Registrar\n",domain,"whois.nic.asia")?.registrar)
        val d=WhoisDomainData.parse("Domain Name: ZZB.ASIA\nRegistrant: Person\nRenewal Date: 2030-01-01\nUpdated Date: unknown",domain,"whois.nic.asia")!!
        assertNull(d.registrar);assertNull(d.organization);assertNull(d.expiresAt);assertNull(d.changedAt)
    }
    @Test fun cnnicDateLabelsAreSourceSpecific() {
        val cn=RdapDomain("example.cn","example.cn")
        val raw="Domain Name: example.cn\nSponsoring Registrar: Example Registrar\nRegistration Time: 2020-01-02 03:04:05\nExpiration Time: 2030-01-02 03:04:05"
        val d=WhoisDomainData.parse(raw,cn,"whois.cnnic.cn")!!
        assertEquals("Example Registrar",d.registrar);assertEquals("2030-01-02T03:04:05",d.expiresAt)
        assertNull(WhoisDomainData.parse(raw,cn,"whois.other.cn")!!.expiresAt)
    }
    @Test fun explicitDenialAndNoObjectDifferFromUnparsedTerms() {
        assertEquals(RdapStatus.ACCESS_RESTRICTED,WhoisDomainData.responseStatus("WHOIS service has been discontinued.","whois.nic.asia",domain.ascii))
        assertEquals(RdapStatus.NOT_FOUND,WhoisDomainData.responseStatus("No match for \"ZZB.ASIA\".","whois.nic.asia",domain.ascii))
        assertEquals(RdapStatus.SUCCESS,WhoisDomainData.responseStatus("Terms: access denied to abusive clients", "whois.nic.asia",domain.ascii))
    }
    @Test fun arinQueryUsesDetailedNetworkFlagsOnlyAtArin() {
        assertEquals("+ n 173.249.200.19",WhoisServers.wireQuery("whois.arin.net",ip.text))
        for(server in WhoisIpData.rirs-"whois.arin.net") {
            assertEquals(ip.text,WhoisServers.wireQuery(server,ip.text));assertFalse(WhoisServers.validWireQuery(server,"+ n ${ip.text}"))
        }
        assertFalse(WhoisServers.validWireQuery("whois.arin.net","+ n 10.0.0.1"))
        assertFalse(WhoisServers.validWireQuery("whois.arin.net","+ n ${ip.text}\r\nother"))
    }
    @Test fun arinSummaryMatchesAreKeptSortedAndNeverInventOrganization() {
        val text="Example Parent LABEL (NET-173-249-192-0-1) 173.249.192.0 - 173.249.255.255\nExample Child LABEL (NET-173-249-200-0-1) 173.249.200.0 - 173.249.201.255"
        val records=WhoisIpData.records(text,ip,"whois.arin.net")
        assertEquals(2,records.size);assertEquals("173.249.200.0 - 173.249.201.255",records.first().range)
        assertTrue(records.all {it.summaryOnly && it.organization==null && it.source=="whois.arin.net"})
        assertEquals("Example Child LABEL",records.first().summaryLabel)
    }
    @Test fun fullArinRecordsNeverBorrowParentOrUnrelatedContactFields() {
        val text="NetRange: 173.249.192.0 - 173.249.255.255\nOrganization: Parent Org\nRegDate: 2020-01-01\n\nNetRange: 173.249.200.0 - 173.249.201.255\nNetHandle: NET-CHILD\n\nOrgName: Unrelated\nCountry: ZZ"
        val r=WhoisIpData.records(text,ip,"whois.arin.net")
        assertEquals(2,r.size);assertNull(r[0].organization);assertTrue(r[0].dates.isEmpty());assertNull(r[0].country)
        assertEquals("Parent Org",r[1].organization);assertFalse(r[1].summaryOnly)
    }
    @Test fun finiteRirFormatsRetainOwnActualFields() {
        for(server in WhoisIpData.rirs-"whois.arin.net") {
            val latin=server=="whois.lacnic.net"
            val body="inetnum: 173.249.200.0/23\nnetname: EXAMPLE\n${if(latin) "owner" else "org"}: Example Org\nstatus: ASSIGNED\ncreated: 20200101\nchanged: contact@example.test 20250101"
            val r=WhoisIpData.records(body,ip,server).single()
            assertEquals(server,r.source);assertEquals("Example Org",r.organization);assertEquals(1,r.dates.size)
        }
    }
    @Test fun rdapEventsAndEntityRolesStayDistinct() {
        val json="""{"rdapConformance":["rdap_level_0"],"objectClassName":"domain","ldhName":"zzb.asia","events":[{"eventAction":"last update of RDAP database","eventDate":"2026-01-01T00:00:00Z"},{"eventAction":"last changed","eventDate":"2025-01-01T00:00:00Z"}],"entities":[{"roles":["registrant"],"vcardArray":["vcard",[["kind",{},"text","org"],["fn",{},"text","Registrant Org"]]]},{"roles":["registrar"],"vcardArray":["vcard",[["fn",{},"text","Registrar Org"]]]}]}"""
        val d=RdapResponseParser(RdapDomainNormalizer {RdapDomainSyntax.ascii(it)}).parse(json.toByteArray(),domain)
        assertEquals("Registrar Org",d.registrar);assertEquals("Registrant Org",d.organization)
        assertEquals("2025-01-01T00:00:00Z",d.changedAt);assertEquals("2026-01-01T00:00:00Z",d.databaseUpdatedAt);assertNull(d.expiresAt)
    }
}
