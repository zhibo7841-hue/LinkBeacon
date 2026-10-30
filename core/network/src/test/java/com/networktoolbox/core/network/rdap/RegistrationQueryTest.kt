package com.networktoolbox.core.network.rdap

import java.io.File
import java.net.IDN
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationQueryTest {
    private val dispatcher=StandardTestDispatcher()
    // JVM IDNA2003 surrogate only; Android ICU/IDNA2008 behavior is exercised on API31.
    private val normalize=RdapDomainNormalizer {raw->runCatching {
        if(!RdapDomainSyntax.allowedInput(raw)) null else RdapDomainSyntax.ascii(IDN.toASCII(raw,IDN.USE_STD3_ASCII_RULES or IDN.ALLOW_UNASSIGNED))
    }.getOrNull()}
    private val rules=RegistrationSuffixRules.parse("""// ===BEGIN ICANN DOMAINS===
com
net
cn
com.cn
uk
co.uk
io
*.ck
!www.ck
// ===END ICANN DOMAINS===
// ===BEGIN PRIVATE DOMAINS===
blogspot.com
github.io
// ===END PRIVATE DOMAINS===
""",normalize)
    private val resolver=RegistrationInputResolver(normalize,rules)
    private fun input(s:String)=requireNotNull(resolver.resolve(s,RegistrationProtocol.RDAP))
    private fun ip(s:String)=requireNotNull(RegistrationIp.parse(s))
    private fun failAs(status:RdapStatus,block:()->Unit) {
        try {block();fail("Expected failure")} catch(e:RdapFailure) {assertEquals(status,e.status)}
    }
    @Test fun fullHostnamesUseIcannRulesAndPreserveAllForms() {
        for((raw,expected) in mapOf("www.baidu.com" to "baidu.com","mail.google.com" to "google.com",
            "www.example.com.cn" to "example.com.cn","blog.example.co.uk" to "example.co.uk")) {
            val q=input(raw);assertEquals(raw,q.original);assertEquals(raw,q.normalizedHost?.ascii)
            assertEquals(expected,q.query);assertTrue(q.converted)
        }
        assertEquals("example.com",input(" EXAMPLE.COM. ").query)
    }
    @Test fun privateHostingRulesNeverBecomeRegistryLevelOwnership() {
        assertEquals("blogspot.com",input("user.blogspot.com").query)
        assertEquals("github.io",input("user.github.io").query)
    }
    @Test fun wildcardExceptionAndUnknownSuffixFailClosed() {
        assertEquals("a.b.ck",input("x.a.b.ck").query)
        assertEquals("www.ck",input("x.www.ck").query)
        for(s in listOf("co.uk","a.ck","a.unknown-tld","com","https://example.com","example.com/path",
            "user@example.com","printer.local","example.com:43","*.example.com","a\n.com","a..com"))
            assertNull(s,resolver.resolve(s,RegistrationProtocol.RDAP))
    }
    @Test fun unicodeAndAlabelQuerySameRegistrationTarget() {
        assertEquals(input("www.中文.cn").query,input("www.xn--fiq228c.cn").query)
        assertEquals("xn--fiq228c.cn",input("www.中文.cn").query)
    }
    @Test fun publicIpHasNoHostDnsAndBothProtocolsAcceptIt() {
        for(s in listOf("8.8.8.8","2001:4860:4860::8888")) {
            val q=input(s);assertNotNull(q.ip);assertNull(q.domain);assertNull(q.normalizedHost)
            assertEquals(q,resolver.resolve(s,RegistrationProtocol.WHOIS))
        }
    }
    @Test fun privateReservedAndAmbiguousIpInputsAreRejected() {
        for(s in listOf("10.0.0.1","127.0.0.1","169.254.1.1","100.64.0.1","192.0.2.1","198.18.1.1",
            "224.1.1.1","240.0.0.1","0.0.0.0","::1","fe80::1","fc00::1","2001:db8::1","2001:2::1",
            "3fff::1","::ffff:8.8.8.8","[2001:4860::1]","fe80::1%wlan0","8.8.8.08","8.8.8.256","8.8.8.8/32"))
            assertNull(s,resolver.resolve(s,RegistrationProtocol.RDAP))
    }
    @Test fun bundledRulesIncludePrivateButOnlyIcannBoundaryIsUsed() {
        val bundled=RegistrationSuffixRules.parse(File("src/main/assets/rdap/public_suffix_list.dat").readText(),normalize)
        assertEquals("example.co.uk",bundled.registrable("x.example.co.uk"))
        assertEquals("github.io",bundled.registrable("user.github.io"))
        assertEquals("blogspot.com",bundled.registrable("user.blogspot.com"))
        assertNull(bundled.registrable("x.unknown-tld"))
    }
    @Test fun bootstrapLongestPrefixIndependentOfEntryOrderAndFamily() {
        for(order in listOf(listOf("8.0.0.0/8","8.8.0.0/16"),listOf("8.8.0.0/16","8.0.0.0/8"))) {
            val services=order.joinToString(",") {"[[\"$it\"],[\"https://r${it.substringAfter('/')}.example.com/\"]]"}
            val b=RdapIpBootstrap.parse("""{"version":"1.0","publication":"2026-01-01T00:00:00Z","services":[$services]}""".toByteArray(),"ipv4")
            assertEquals(listOf("https://r16.example.com/"),b.select(ip("8.8.8.8")))
            assertNull(b.select(ip("1.1.1.1")));assertNull(b.select(ip("2001:4860::1")))
        }
        assertTrue(requireNotNull(RegistrationCidr.parse("2001:4860::/32")).contains(ip("2001:4860:4860::8888")))
        assertFalse(requireNotNull(RegistrationCidr.parse("2001:4860::/32")).contains(ip("2001:4861::1")))
        assertNull(RegistrationCidr.parse("8.8.0.1/16"));assertNull(RegistrationCidr.parse("::/129"))
    }
    @Test fun reviewedIanaSnapshotsSelectRealHttpsServices() {
        val v4=RdapIpBootstrap.parse(File("src/main/assets/rdap/ipv4.json").readBytes(),"ipv4")
        val v6=RdapIpBootstrap.parse(File("src/main/assets/rdap/ipv6.json").readBytes(),"ipv6")
        assertEquals("https://rdap.arin.net/registry/",v4.select(ip("8.8.8.8"))?.first())
        assertEquals("https://rdap.arin.net/registry/",v6.select(ip("2001:4860:4860::8888"))?.first())
        failAs(RdapStatus.INVALID_RESPONSE) {RdapIpBootstrap.parse(File("src/main/assets/rdap/ipv4.json").readBytes(),"ipv6")}
    }
    private fun networkBody(start:String="8.8.8.0",end:String="8.8.8.255",version:String="v4") =
        """{"rdapConformance":["rdap_level_0"],"objectClassName":"ip network","ipVersion":"$version","startAddress":"$start","endAddress":"$end","name":"NET-FIXTURE","handle":"HANDLE","country":"US","status":["active"],"events":[{"eventAction":"registration","eventDate":"2020-01-01T00:00:00Z"}],"entities":[{"vcardArray":["vcard",[["fn",{},"text","Personal Contact"]]]}]}""".toByteArray()
    @Test fun ipNetworkMustContainQueryAndNeverInventOrganizationOrLocation() {
        val n=RdapIpResponseParser.parse(networkBody(),ip("8.8.8.8"))
        assertEquals("8.8.8.0",n.start);assertEquals("US",n.country);assertNull(n.organization)
        assertEquals(1,n.events.size)
        failAs(RdapStatus.INVALID_RESPONSE) {RdapIpResponseParser.parse(networkBody(),ip("8.8.4.4"))}
        failAs(RdapStatus.INVALID_RESPONSE) {RdapIpResponseParser.parse(networkBody("8.8.8.255","8.8.8.0"),ip("8.8.8.8"))}
        failAs(RdapStatus.INVALID_RESPONSE) {RdapIpResponseParser.parse(networkBody(version="v6"),ip("8.8.8.8"))}
        assertNotNull(RdapIpResponseParser.parse(networkBody("2001:4860::","2001:4860:ffff:ffff:ffff:ffff:ffff:ffff","v6"),ip("2001:4860:4860::8888")))
    }
    @Test fun ipUrlOnlyPermitsNumericObjectAndSameSelectedEndpoint() {
        val url=RdapEndpointPolicy.ipQuery("https://rdap.arin.net/registry/",ip("2001:4860:4860::8888"))
        assertTrue(RdapEndpointPolicy.requestAllowed(url))
        for(bad in listOf("https://r.example.com/ip/::1%wlan0","https://r.example.com/ip/a:b",
            "http://r.example.com/ip/8.8.8.8","https://u:p@r.example.com/ip/2001:4860::1")) assertFalse(bad,RdapEndpointPolicy.requestAllowed(bad))
        assertNull(RdapEndpointPolicy.redirect(url,"https://evil.example.com/ip/2001:4860:4860::8888",setOf(url)))
    }
    @Test fun whoisUtf8CrLfAndReferralsRemainPlainText() {
        val s="Domain Name: EXAMPLE.COM\r\nRegistrar WHOIS Server: 127.0.0.1\r\n备注：示例\r\n"
        val result=WhoisText.parse(s.toByteArray());assertTrue("127.0.0.1" in result);assertTrue("备注：示例" in result)
        assertEquals(s,result)
        for(bytes in listOf(byteArrayOf(0xc3.toByte()),"\u001b[31mBAD".toByteArray(),"\u202eevil".toByteArray(),byteArrayOf()))
            failAs(RdapStatus.INVALID_RESPONSE) {WhoisText.parse(bytes)}
        failAs(RdapStatus.RESPONSE_TOO_LARGE) {WhoisText.parse(ByteArray(WhoisLimits.BODY_BYTES+1))}
    }
    @Test fun whoisAllowlistCannotSelectArbitraryServer() {
        assertEquals("whois.verisign-grs.com",WhoisServers.select(requireNotNull(input("www.example.com").domain)))
        assertFalse(WhoisServers.allowed("127.0.0.1",requireNotNull(input("example.com").domain)))
        assertFalse(WhoisServers.allowed("whois.cnnic.cn",requireNotNull(input("example.com").domain)))
        assertNull(WhoisServers.select(requireNotNull(input("example.io").domain)))
    }
    private class Session: RdapHttpSession {
        override var changed=false;override val vpn=false
        var closes=0;var calls=0;var action:suspend ()->ByteArray={"Fixture response".toByteArray()}
        override suspend fun get(url:String,limit:Int,remainingMs:Long,onProgress:(RdapPhase)->Unit):RdapHttpResponse=error("No HTTP")
        override suspend fun whois(server:String,query:String,limit:Int,onProgress:(RdapPhase)->Unit):ByteArray {calls++;return action()}
        override fun close(){closes++}
    }
    @Test fun whoisTimeoutCancellationNetworkChangeAlwaysCloseNoFallback()=runTest(dispatcher) {
        for(kind in listOf("timeout","cancel","network","failure")) {
            val s=Session();s.action={when(kind) {"network"->{s.changed=true;throw RdapFailure(RdapStatus.NETWORK_CHANGED)}
                "failure"->throw RdapFailure(RdapStatus.NETWORK_UNAVAILABLE)
                else->awaitCancellation()}}
            val service=RegistrationLookup(RunRdapLookup {_,_->error("No RDAP fallback")},RdapTransport {s},dispatcher)
            var result:RdapResult?=null
            val job=launch {result=service.run(input("example.com"),RegistrationProtocol.WHOIS) {}}
            runCurrent();if(kind=="cancel") job.cancelAndJoin() else advanceUntilIdle()
            assertEquals(1,s.calls);assertEquals(1,s.closes)
            when(kind) {"cancel"->assertNull(result);"timeout"->assertEquals(RdapStatus.TIMEOUT,result?.status)
                "network"->assertEquals(RdapStatus.NETWORK_CHANGED,result?.status)
                else->assertEquals(RdapStatus.NETWORK_UNAVAILABLE,result?.status)}
        }
    }
    @Test fun whoisRawFailureWordsAreNotStandardizedAndReferralNeverFetched()=runTest(dispatcher) {
        val s=Session();s.action={"No match. Referral: whois://127.0.0.1\nrate limit?".toByteArray()}
        val r=RegistrationLookup(RunRdapLookup {_,_->error("No fallback")},RdapTransport {s},dispatcher)
            .run(input("example.com"),RegistrationProtocol.WHOIS) {}
        assertEquals(RdapStatus.SUCCESS,r.status);assertEquals(1,r.requests);assertEquals(RegistrationProtocol.WHOIS,r.protocol)
        assertNull(r.registration);assertTrue(requireNotNull(r.whoisText).contains("No match"));assertEquals(1,s.calls)
    }
    @Test fun ipRdapUsesExistingBoundedPipelineAndRejectsPrivateBeforeOpen()=runTest(dispatcher) {
        var calls=0;var closes=0
        val session=object:RdapHttpSession {
            override val changed=false;override val vpn=false
            override suspend fun get(url:String,limit:Int,remainingMs:Long,onProgress:(RdapPhase)->Unit):RdapHttpResponse {
                calls++;assertEquals("https://rdap.arin.net/registry/ip/8.8.8.8",url);return RdapHttpResponse(200,networkBody())
            }
            override fun close(){closes++}
        }
        val store=object:RdapBootstrapStore {
            override fun load()=listOf(RdapBootstrapSnapshot(File("src/main/assets/rdap/ipv4.json").readBytes(),1000,RdapBootstrapSource.BUNDLED))
            override fun save(bytes:ByteArray,fetchedAt:Long)=error("No refresh")
        }
        val engine=DefaultRdapLookup(RdapTransport {session},store,RdapResponseParser(normalize),clock={1001},io=dispatcher,ipStores={store})
        assertEquals(RdapStatus.INVALID_INPUT,engine.runIp(ip("10.0.0.1")) {}.status);assertEquals(0,calls)
        val r=engine.runIp(ip("8.8.8.8")) {};assertEquals(RdapStatus.SUCCESS,r.status)
        assertNotNull(r.ipNetwork);assertEquals(1,calls);assertEquals(1,closes)
    }
}
