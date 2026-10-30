package com.networktoolbox.core.network.rdap

import java.net.IDN
import java.net.InetAddress
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RdapCoreTest {
    private val dispatcher=StandardTestDispatcher()
    // JVM surrogate covers pure boundaries, not ICU UTS46. Android ICU is tested on device.
    private val normalizer=RdapDomainNormalizer {s->runCatching {
        if(!RdapDomainSyntax.allowedInput(s)) null else {
            val a=IDN.toASCII(s,IDN.USE_STD3_ASCII_RULES)
            RdapDomainSyntax.ascii(a,IDN.toUnicode(a))
        }
    }.getOrNull()}
    private val domain=requireNotNull(normalizer.normalize("example.com"))
    private val parser=RdapResponseParser(normalizer)
    private val now=1_790_000_000_000L
    private fun directory(urls:String="\"https://rdap.registry.example.net/\"",keys:String="\"com\"") =
        """{"version":"1.0","publication":"2026-01-01T00:00:00Z","services":[[[$keys],[$urls]]]}""".toByteArray()
    private fun body(name:String="example.com") = """{"rdapConformance":["rdap_level_0"],"objectClassName":"domain","ldhName":"$name"}""".toByteArray()
    private class Store(val snapshots:List<RdapBootstrapSnapshot>):RdapBootstrapStore {
        var loads=0;var writes=0
        override fun load()=snapshots.also {loads++}
        override fun save(bytes:ByteArray,fetchedAt:Long) {writes++}
    }
    private class Session(val action:suspend (String,Int)->RdapHttpResponse):RdapHttpSession {
        val urls=mutableListOf<String>();var closes=0
        override var changed=false
        override val vpn=false
        override suspend fun get(url:String,limit:Int,remainingMs:Long,onProgress:(RdapPhase)->Unit):RdapHttpResponse {
            urls+=url;return action(url,limit)
        }
        override fun close() {closes++}
    }
    private fun store(bytes:ByteArray=directory(),at:Long=now)=Store(listOf(RdapBootstrapSnapshot(bytes,at,RdapBootstrapSource.BUNDLED)))
    private fun engine(session:Session,store:Store=store())=DefaultRdapLookup(RdapTransport {session},store,parser,{now},io=dispatcher)
    private fun failure(status:RdapStatus,action:()->Unit) {
        try {action();fail("Expected closed failure")} catch(e:RdapFailure) {assertEquals(status,e.status)}
    }
    @Test fun asciiIdnAndWholeSubdomainAreNotGuessedOrResolved() {
        assertEquals("example.com",normalizer.normalize("EXAMPLE.COM.")?.ascii)
        assertNotNull(normalizer.normalize("中文.cn"))
        assertEquals("a.b.example.co.uk",normalizer.normalize("a.b.example.co.uk")?.ascii)
        for(v in listOf("http://example.com","u:p@example.com","example.com/a","example.com?q=1","127.0.0.1",
            "[::1]","localhost","a..com","*.com","a_b.com","printer.local","xn--.com","a\u0000.com","a".repeat(64)+".com"))
            assertNull(v,normalizer.normalize(v))
    }
    @Test fun bootstrapUsesLabelWiseLongestSuffixIncludingRoot() {
        val b=RdapBootstrap.parse("""{"version":"1.0","publication":"2026-01-01T00:00:00Z","services":[[[""],["https://root.example.net/"]],[["uk"],["https://uk.example.net/"]],[["co.uk"],["https://specific.example.net/"]]]}""".toByteArray())
        assertEquals(listOf("https://specific.example.net/"),b.select(requireNotNull(normalizer.normalize("a.b.example.co.uk"))))
        assertEquals(listOf("https://root.example.net/"),b.select(domain))
        assertEquals(listOf("https://uk.example.net/"),b.select(requireNotNull(normalizer.normalize("badco.uk"))))
    }
    @Test fun malformedBootstrapAndUnknownDeepFieldsAreRejected() {
        for(raw in listOf("{}","""{"version":"1.0","publication":"bad","services":[]}""",
            """{"version":"1.0","publication":"2026-01-01T00:00:00Z","services":[[["com","com"],["https://x.example.net/"]]]}"""))
            failure(RdapStatus.INVALID_RESPONSE) {RdapBootstrap.parse(raw.toByteArray())}
        failure(RdapStatus.INVALID_RESPONSE) {BoundedRdapJson.parse(("{\"unknown\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}").toByteArray(),10000)}
    }
    @Test fun strictJsonRejectsDuplicatesInvalidUtf8NumbersSurrogatesAndTrailingData() {
        for(s in listOf("{\"x\":1,\"x\":2}","{\"x\":01}","{\"x\":1e}","{\"x\":NaN}","{\"x\":\"\\uD800\"}","{} {}","{\"x\":[1,]}"))
            failure(RdapStatus.INVALID_RESPONSE) {BoundedRdapJson.parse(s.toByteArray(),10000)}
        failure(RdapStatus.INVALID_RESPONSE) {BoundedRdapJson.parse(byteArrayOf(0xc0.toByte(),0xaf.toByte()),100)}
    }
    @Test fun boundedReadDoesNotDependOnContentLengthAndClosesAtCaller() {
        val input=object:ByteArrayInputStream(ByteArray(1100)) {var closed=false;override fun close(){closed=true;super.close()}}
        failure(RdapStatus.RESPONSE_TOO_LARGE) {input.use {readRdapBody(it,1024)}}
        assertTrue(input.closed);assertEquals(75,input.available())
        assertEquals(1024,readRdapBody(ByteArrayInputStream(ByteArray(1024)),1024).size)
    }
    @Test fun parserKeepsProvidedDatesRegistrarDnssecAndPlainNoticesOnly() {
        val data=parser.parse("""{"rdapConformance":["rdap_level_0"],"objectClassName":"domain","ldhName":"EXAMPLE.COM.","events":[{"eventAction":"registration","eventDate":"2020-01-01T00:00:00Z"},{"eventAction":"expiration","eventDate":"broken"}],"status":["client transfer prohibited"],"nameservers":[{"ldhName":"ns1.example.net."}],"secureDNS":{"delegationSigned":false},"redacted":[{}],"entities":[{"roles":["registrant"],"vcardArray":["vcard",[["fn",{},"text","private-person-secret"]]]},{"roles":["registrar"],"vcardArray":["vcard",[["fn",{},"text","Registrar Name"]]]}],"notices":[{"description":["<b>Plain only</b>"],"links":[{"href":"http://localhost/private"}]}]}""".toByteArray(),domain)
        assertEquals("Registrar Name",data.registrar);assertEquals("2020-01-01T00:00:00Z",data.registeredAt)
        assertNull(data.expiresAt);assertNull(data.changedAt);assertTrue(data.restricted)
        assertEquals(RdapDnssec.UNSIGNED,data.dnssec);assertEquals(listOf("ns1.example.net"),data.nameservers)
        assertFalse("private-person-secret" in data.toString());assertFalse("localhost" in data.toString())
        assertEquals(listOf("<b>Plain only</b>"),data.notices)
    }
    @Test fun missingFieldsStayMissingAndWrongDomainNeverBecomesParentLookup() {
        val r=parser.parse(body(),domain)
        assertNull(r.registrar);assertNull(r.expiresAt);assertEquals(RdapDnssec.NOT_PROVIDED,r.dnssec)
        failure(RdapStatus.INVALID_RESPONSE) {parser.parse(body("parent.com"),domain)}
        failure(RdapStatus.INVALID_RESPONSE) {parser.parse("""{"objectClassName":"entity","ldhName":"example.com"}""".toByteArray(),domain)}
    }
    @Test fun everyHttpFailureIsSingleAttemptWithoutFallback()=runTest(dispatcher) {
        val mappings=mapOf(400 to RdapStatus.QUERY_REJECTED,401 to RdapStatus.ACCESS_RESTRICTED,403 to RdapStatus.ACCESS_RESTRICTED,
            404 to RdapStatus.NOT_FOUND,429 to RdapStatus.RATE_LIMITED,500 to RdapStatus.SERVER_ERROR,503 to RdapStatus.SERVER_ERROR)
        for((code,status) in mappings) {
            val s=Session {_,_->RdapHttpResponse(code)};val result=engine(s).run(domain) {}
            assertEquals(status,result.status);assertEquals(code,result.httpStatus);assertEquals(1,s.urls.size);assertEquals(1,s.closes)
        }
    }
    @Test fun successfulLookupIsInMemoryAndDoesNotRefreshFreshDirectory()=runTest(dispatcher) {
        val st=store();val s=Session {_,_->RdapHttpResponse(200,body())};val e=engine(s,st)
        assertEquals(0,st.loads);assertTrue(s.urls.isEmpty())
        val result=e.run(domain) {}
        assertEquals(RdapStatus.SUCCESS,result.status);assertEquals(0,st.writes)
        assertEquals("https://rdap.registry.example.net/domain/example.com",result.source)
        assertEquals(RdapBootstrapSource.BUNDLED,result.bootstrapSource)
    }
    @Test fun expiredDirectoryRefreshAndQueryShareOneBudget()=runTest(dispatcher) {
        val st=store(at=now-RdapLimits.BOOTSTRAP_AGE_MS-1)
        val s=Session {url,_->if(url==RdapLimits.BOOTSTRAP_URL) RdapHttpResponse(200,directory()) else RdapHttpResponse(200,body())}
        val r=engine(s,st).run(domain) {}
        assertEquals(RdapStatus.SUCCESS,r.status);assertEquals(2,r.requests);assertEquals(1,st.writes)
        assertEquals(RdapBootstrapSource.UPDATED,r.bootstrapSource)
    }
    @Test fun refreshFailureNeverUsesStaleEndpointsAndHttpIsNotRewritten()=runTest(dispatcher) {
        val st=store(at=1)
        val s=Session {_,_->RdapHttpResponse(503)}
        assertEquals(RdapStatus.BOOTSTRAP_UNAVAILABLE,engine(s,st).run(domain) {}.status)
        assertEquals(listOf(RdapLimits.BOOTSTRAP_URL),s.urls);assertEquals(0,st.writes)
        val http=Session {_,_->fail("No downgrade");error("unreachable")}
        assertEquals(RdapStatus.HTTP_ONLY,engine(http,store(directory("\"http://rdap.registry.example.net/\""))).run(domain) {}.status)
        assertTrue(http.urls.isEmpty())
    }
    @Test fun unmatchedTldAndInvalidRefreshAreExplicit()=runTest(dispatcher) {
        val d=requireNotNull(normalizer.normalize("example.zznotregistered"))
        val s=Session {_,_->RdapHttpResponse(200,directory())}
        assertEquals(RdapStatus.UNSUPPORTED,engine(s).run(d) {}.status)
        val bad=Session {_,_->RdapHttpResponse(200,"{}".toByteArray())}
        assertEquals(RdapStatus.INVALID_RESPONSE,engine(bad,store(at=1)).run(domain) {}.status)
    }
    @Test fun redirectsMustRemainOnApprovedServiceAndExactObject()=runTest(dispatcher) {
        for(location in listOf("http://rdap.registry.example.net/domain/example.com","https://evil.example.net/domain/example.com",
            "https://127.0.0.1/domain/example.com","/domain/different.com","/domain/example.com?token=1","https://u:p@rdap.registry.example.net/domain/example.com")) {
            val s=Session {_,_->RdapHttpResponse(302,location=location)}
            assertEquals(location,RdapStatus.REDIRECT_REJECTED,engine(s).run(domain) {}.status)
            assertEquals(1,s.urls.size)
        }
    }
    @Test fun redirectLoopAndMaximumRequestBudgetAreEnforced()=runTest(dispatcher) {
        val bases=(1..5).map {"https://rdap$it.example.net/"}
        val st=store(directory(bases.joinToString(",") {"\"$it\""}))
        var index=0;val s=Session {_,_->RdapHttpResponse(302,location=bases[++index]+"domain/example.com")}
        val r=engine(s,st).run(domain) {}
        assertEquals(RdapStatus.REDIRECT_REJECTED,r.status);assertEquals(4,s.urls.size);assertEquals(4,r.requests)
        val loop=Session {url,_->RdapHttpResponse(302,location=url)}
        assertEquals(RdapStatus.REDIRECT_REJECTED,engine(loop).run(domain) {}.status);assertEquals(1,loop.urls.size)
    }
    @Test fun privateSpecialMappedAndRebindingDestinationsAreRejected() {
        for(ip in listOf("127.0.0.1","10.0.0.1","100.64.0.1","169.254.1.1","172.16.0.1","192.168.0.1",
            "192.0.2.1","198.18.0.1","198.51.100.1","203.0.113.1","224.0.0.1","::1","fe80::1","fd00::1","2001:db8::1","3fff::1","2002:7f00:1::"))
            assertFalse(ip,RdapAddressPolicy.public(InetAddress.getByName(ip)))
        val good=InetAddress.getByName("1.1.1.1");val other=InetAddress.getByName("8.8.8.8")
        assertTrue(RdapAddressPolicy.verified(listOf(good),good))
        assertFalse(RdapAddressPolicy.verified(listOf(good),other))
        assertFalse(RdapAddressPolicy.verified(listOf(good,InetAddress.getByName("10.0.0.1")),good))
        assertTrue(RdapAddressPolicy.public(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun cancellationClosesSessionAndSingleFlightRejectsSecondAction()=runTest(dispatcher) {
        val s=Session {_,_->awaitCancellation()};val e=engine(s)
        val job=launch {e.run(domain) {}};runCurrent()
        assertEquals(RdapStatus.BUSY,e.run(domain) {}.status)
        job.cancelAndJoin();assertEquals(1,s.closes);assertEquals(1,s.urls.size)
    }
    @Test fun transportSecurityAndNetworkFailuresNeverTriggerFallback()=runTest(dispatcher) {
        for(status in listOf(RdapStatus.PROXY_UNSUPPORTED,RdapStatus.UNSAFE_ENDPOINT,RdapStatus.DNS_FAILED,
            RdapStatus.TLS_FAILED,RdapStatus.NETWORK_UNAVAILABLE,RdapStatus.TIMEOUT)) {
            val session=Session {_,_->throw RdapFailure(status)}
            val result=engine(session).run(domain) {}
            assertEquals(status,result.status);assertEquals(1,session.urls.size);assertEquals(1,session.closes)
            assertEquals(1,result.requests);assertNull(result.registration)
        }
    }
    @Test fun timeoutAndNetworkChangeCloseAndRejectLateResults()=runTest(dispatcher) {
        val slow=Session {_,_->awaitCancellation()}
        assertEquals(RdapStatus.TIMEOUT,engine(slow).run(domain) {}.status);assertEquals(1,slow.closes)
        lateinit var moved:Session
        moved=Session {_,_->moved.changed=true;RdapHttpResponse(200,body())}
        assertEquals(RdapStatus.NETWORK_CHANGED,engine(moved).run(domain) {}.status);assertEquals(1,moved.closes)
    }
}
