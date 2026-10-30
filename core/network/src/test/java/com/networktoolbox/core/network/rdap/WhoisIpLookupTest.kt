package com.networktoolbox.core.network.rdap

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WhoisIpLookupTest {
    private val dispatcher=StandardTestDispatcher()
    private fun ip(s:String="8.8.8.8")=requireNotNull(RegistrationIp.parse(s))
    private val iana="inetnum: 8.0.0.0 - 8.255.255.255\norganisation: Upstream allocation\nrefer: whois.arin.net"
    private val arin="NetRange: 8.8.8.0 - 8.8.8.255\nCIDR: 8.8.8.0/24\nNetName: FIXTURE\nNetHandle: NET-8-8-8-0\nOrganization: Example Organization\nCountry: US\nRegDate: 2020-01-01"
    private class Session: RdapHttpSession {
        override var changed=false;override val vpn=false
        var closed=0;val calls=mutableListOf<Pair<String,String>>();val limits=mutableListOf<Int>()
        var reply:suspend (String)->String={error("Unconfigured")}
        override suspend fun get(url:String,limit:Int,remainingMs:Long,onProgress:(RdapPhase)->Unit):RdapHttpResponse=error("No RDAP fallback")
        override suspend fun whois(server:String,query:String,limit:Int,onProgress:(RdapPhase)->Unit):ByteArray {
            calls+=server to query;limits+=limit;return reply(server).toByteArray()
        }
        override fun close(){closed++}
    }
    private suspend fun run(s:Session,target:String="8.8.8.8")=RegistrationLookup(
        RunRdapLookup {_,_->error("No RDAP fallback")},RdapTransport {s},dispatcher)
        .run(RegistrationInput(target,ip=ip(target)),RegistrationProtocol.WHOIS) {}
    @Test fun ipv4FollowsIanaAndKeepsAllocationSeparateFromFinalRir()=runTest(dispatcher) {
        val s=Session();s.reply={if(it==WhoisIpData.IANA) iana else arin}
        val r=run(s);assertEquals(RdapStatus.SUCCESS,r.status);assertEquals(2,r.requests)
        assertEquals(listOf(WhoisIpData.IANA,"whois.arin.net"),s.calls.map {it.first})
        assertEquals(listOf("8.8.8.8","+ n 8.8.8.8"),s.calls.map {it.second});assertTrue(r.whoisSteps.first().ianaAllocation)
        assertEquals("Upstream allocation",r.whoisSteps.first().record?.organization)
        assertEquals("Example Organization",r.whoisIpRecord?.organization);assertEquals(1,s.closed)
        assertEquals("8.8.8.0/24",r.whoisIpRecord?.cidr);assertEquals("NET-8-8-8-0",r.whoisIpRecord?.handle)
    }
    @Test fun ipv6UsesNormalizedQueryAndContainingNetwork()=runTest(dispatcher) {
        val s=Session();s.reply={if(it==WhoisIpData.IANA) "inet6num: 2001:4800::/23\norganisation: ARIN\nwhois: whois.arin.net" else
            "NetRange: 2001:4860:: - 2001:4860:ffff:ffff:ffff:ffff:ffff:ffff\nCIDR: 2001:4860::/32\nNetName: IPV6"}
        val r=run(s,"2001:4860:4860::8888")
        assertEquals(RdapStatus.SUCCESS,r.status);assertEquals("IPV6",r.whoisIpRecord?.name)
        assertEquals(listOf(ip("2001:4860:4860::8888").text,"+ n "+ip("2001:4860:4860::8888").text),s.calls.map {it.second})
    }
    @Test fun whoisFieldIsAnIanaOnlyReferralAndCannotOverrideAnExplicitConflict() {
        assertEquals("whois.arin.net",WhoisIpData.referral("whois: whois.arin.net",WhoisIpData.IANA))
        assertNull(WhoisIpData.referral("whois: whois.apnic.net","whois.arin.net"))
        try {WhoisIpData.referral("refer: whois.arin.net\nwhois: whois.apnic.net");fail("conflict")}
        catch(e:RdapFailure){assertEquals(RdapStatus.REDIRECT_REJECTED,e.status)}
    }
    @Test fun allFiveRirsComeFromReferralNotInputOctet()=runTest(dispatcher) {
        for(server in WhoisIpData.rirs) {
            val s=Session();s.reply={if(it==WhoisIpData.IANA) "refer: $server" else arin}
            val r=run(s);assertEquals(server,s.calls.last().first);assertEquals(2,r.requests)
        }
    }
    @Test fun absentReferralRetainsIanaWithoutFinalOwnership()=runTest(dispatcher) {
        val s=Session();s.reply={iana.substringBefore("refer:")};val r=run(s)
        assertEquals(RdapStatus.SUCCESS,r.status);assertNull(r.whoisIpRecord);assertEquals(1,s.calls.size)
        assertNotNull(r.whoisSteps.single().record)
    }
    @Test fun malformedConflictingOrUntrustedReferralsNeverConnect()=runTest(dispatcher) {
        for(value in listOf("127.0.0.1","whois.arin.net.evil.test","https://whois.arin.net","whois://whois.arin.net:80",
            "whois://u:p@whois.arin.net","whois://whois.arin.net/path","whois.arin.net\nrefer: whois.apnic.net")) {
            val s=Session();s.reply={"refer: $value"};assertEquals(RdapStatus.REDIRECT_REJECTED,run(s).status)
            assertEquals(1,s.calls.size);assertEquals(1,s.closed)
        }
        assertEquals("whois.arin.net",WhoisIpData.referral("ReferralServer: whois://WHOIS.ARIN.NET:43/"))
        assertNull(WhoisIpData.referral("% refer: whois.arin.net\nRemarks: refer: whois.arin.net"))
    }
    @Test fun loopStopsAtActualVisitedService()=runTest(dispatcher) {
        val s=Session();s.reply={if(it==WhoisIpData.IANA) iana else "refer: whois.iana.org"}
        val r=run(s);assertEquals(RdapStatus.REDIRECT_REJECTED,r.status);assertEquals(2,s.calls.size);assertNull(r.whoisIpRecord)
    }
    @Test fun referralCountHasFiniteSharedLimit()=runTest(dispatcher) {
        val s=Session();s.reply={when(it){WhoisIpData.IANA->"refer: whois.arin.net";"whois.arin.net"->"refer: whois.ripe.net";else->"refer: whois.apnic.net"}}
        val r=run(s);assertEquals(RdapStatus.REDIRECT_REJECTED,r.status);assertEquals(WhoisLimits.SERVERS,s.calls.size)
    }
    @Test fun referralsShareTotalDeadlineAndPreserveFailedStep()=runTest(dispatcher) {
        val s=Session();s.reply={delay(8000);if(it==WhoisIpData.IANA) iana else arin}
        val start=testScheduler.currentTime;val r=run(s)
        assertEquals(15_000,testScheduler.currentTime-start);assertEquals(RdapStatus.TIMEOUT,r.status)
        assertEquals(2,s.calls.size);assertEquals(RdapStatus.TIMEOUT,r.whoisSteps.last().status);assertEquals(1,s.closed)
    }
    @Test fun totalResponseBudgetCannotResetPerServer()=runTest(dispatcher) {
        val s=Session();s.reply={val next=if(it==WhoisIpData.IANA) "whois.arin.net" else "whois.ripe.net"
            ("refer: $next\n").padEnd(WhoisLimits.BODY_BYTES,' ')}
        val r=run(s);assertEquals(RdapStatus.RESPONSE_TOO_LARGE,r.status);assertEquals(2,s.calls.size)
    }
    @Test fun smallerRemainingBudgetIsPassedToThirdServer()=runTest(dispatcher) {
        val s=Session();s.reply={when(it){WhoisIpData.IANA->"refer: whois.arin.net\n".padEnd(60000,' ')
            "whois.arin.net"->"refer: whois.ripe.net\n".padEnd(60000,' ');else->arin}}
        assertEquals(RdapStatus.SUCCESS,run(s).status)
        assertEquals(WhoisLimits.TOTAL_BYTES-120000,s.limits.last())
    }
    @Test fun cancelStopsReferralAndClosesSession()=runTest(dispatcher) {
        val s=Session();s.reply={awaitCancellation()};val job=launch {run(s)};runCurrent();job.cancelAndJoin()
        assertEquals(1,s.calls.size);assertEquals(1,s.closed)
    }
    @Test fun changedNetworkRejectsLateResponseAndNextReferral()=runTest(dispatcher) {
        val s=Session();s.reply={s.changed=true;iana};val r=run(s)
        assertEquals(RdapStatus.NETWORK_CHANGED,r.status);assertEquals(1,s.calls.size);assertNull(r.whoisText);assertEquals(1,s.closed)
    }
    @Test fun rirFormatsKeepOnlyReliableActualFields() {
        for(text in listOf("inetnum: 8.8.8.0 - 8.8.8.255\nnetname: TEST\norg: ORG-TEST\ncountry: US\nstatus: ASSIGNED PA\nlast-modified: 2025-01-01T00:00:00Z",
            "inetnum: 8.8.8.0/24\nowner: Example Company\ncountry: BR\ncreated: 20200101\nchanged: contact@example.test 20250101",arin)) {
            val r=requireNotNull(WhoisIpData.record(text,ip()));assertNotNull(r.country);assertNotNull(r.organization)
            assertFalse(r.dates.any {"@" in it.second})
        }
        assertNotNull(WhoisIpData.record("inet6num: 2001:4860::/32\nnetname: V6",ip("2001:4860:4860::8888")))
        assertNull(WhoisIpData.record("inetnum: 1.1.1.0/24\nowner: unrelated",ip()))
        assertNull(WhoisIpData.record("person: Personal Contact\ncountry: US",ip()))
    }
    @Test fun mostSpecificRecordWinsWithoutBorrowingUnrelatedContact() {
        val text="NetRange: 8.0.0.0 - 8.255.255.255\nOrganization: Parent\n\n$arin\n\nperson: unrelated\ncountry: ZZ"
        assertEquals("Example Organization",WhoisIpData.record(text,ip())?.organization)
    }
    @Test fun privateAndMappingUserIpNeverOpenSession()=runTest(dispatcher) {
        val s=Session();for(value in listOf("198.18.0.1","10.0.0.1","::1")) assertEquals(RdapStatus.INVALID_INPUT,run(s,value).status)
        assertEquals(0,s.calls.size)
    }
    @Test fun regionalFieldsSurviveAFailedLaterReferralWithoutMergingSources()=runTest(dispatcher) {
        val s=Session();s.reply={when(it) {
            WhoisIpData.IANA->iana
            "whois.arin.net"->arin+"\nReferralServer: whois://whois.ripe.net"
            else->throw RdapFailure(RdapStatus.TIMEOUT)
        }}
        val r=run(s)
        assertEquals(RdapStatus.TIMEOUT,r.status);assertEquals("Example Organization",r.whoisIpRecord?.organization)
        assertEquals("whois.arin.net",r.whoisIpRecords.single().source);assertEquals(3,r.whoisSteps.size)
    }
}
