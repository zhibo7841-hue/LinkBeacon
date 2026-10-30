package com.networktoolbox.feature.webdiagnostics.export

import com.networktoolbox.core.network.http.*
import com.networktoolbox.core.network.model.*
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.*
import com.networktoolbox.core.network.website.*
import com.networktoolbox.feature.webdiagnostics.domain.*
import com.networktoolbox.feature.webdiagnostics.history.*
import org.junit.Assert.*
import org.junit.Test

class WebExportMapperTest {
    private fun probe()=TlsProbeResult("raw-secret", "example.com","10.22.33.44",443,
        connection=TlsConnectionEvidence(TcpConnectOutcome.CONNECTED,"10.22.33.44",443,5),
        session=TlsSessionEvidence(TlsHandshakeStatus.SUCCESS,7,"TLSv1.3","TLS_AES_128_GCM_SHA256","h2"),
        certificate=PresentedCertificate("CN=dn-secret","CN=issuer-secret",listOf("san-secret.lan"),listOf("10.22.33.44"),
            1000,2000,CertificateValidityStatus.EXPIRED,-1,true).let {CertificateEvidence(it,listOf(it),1)},
        trustStatus=CertificateTrustStatus.UNTRUSTED,hostnameStatus=HostnameVerificationStatus.MISMATCH,
        certificateIssues=setOf(CertificateIssue.EXPIRED),failureReason=TlsFailureReason.TRUST_FAILED,
        networkFingerprint="fingerprint-secret",vpnActive=true)
    private fun tls()=TlsCheckResult("userinfo-secret","example.com","10.22.33.44",443,probe(),null,
        TlsCheckAnalysis(TlsCheckOutcome.FAILED,listOf(TlsCheckFindingCode.UNTRUSTED),emptyList()),42)
    private fun website():WebsiteDiagnosticSnapshot {
        val target=NormalizedWebsiteTarget(WebsiteScheme.HTTPS,false,"example.com","example.com",WebsiteHostType.DOMAIN,443,
            "/path-secret","query-secret","fragment-secret","https://user-secret:password-secret@example.com/path-secret?query-secret#fragment-secret","raw-secret")
        val hop=WebsiteDiagnosticHop(0,target,HttpTransportPath.HTTP_PROXY,
            WebsiteDnsEvidence(WebsiteStageStatus.PASS,null,listOf("10.22.33.44"),false,4,null),
            WebsiteTcpEvidence(WebsiteStageStatus.PASS,listOf(WebsiteTcpAttemptEvidence("10.22.33.44",TcpConnectOutcome.CONNECTED,5)),"10.22.33.44",5),
            WebsiteTlsEvidence(WebsiteStageStatus.FAIL,probe(),7),
            HttpProbeResult("raw-secret","GET",403,HttpStatusCategory.CLIENT_RESPONSE_4XX,HttpProtocol.HTTP_2,
                HttpResponseHeaders("https://example.com/location-secret?query-secret","server-secret","text/html;token=header-secret","via-secret"),
                12,HttpTransportPath.PAC,0,null),null,null,20)
        return WebsiteDiagnosticSnapshot(enteredInputRedacted="raw-secret",normalizedTarget=target,targetFailure=null,sessionFailure=null,
            networkContext=NetworkContext.unknown().copy(ipv4Address="10.22.33.44",wifiName="ssid-secret",interfaceName="interface-secret",
                dnsServers=listOf("10.22.33.44"),proxyHost="proxy-secret",proxyPacUrl="pac-secret",privateDnsServerName="dns-secret",vpnActive=true),
            networkFingerprint="fingerprint-secret",startedAtEpochMs=1000,endedAtEpochMs=2000,totalDurationMs=100,
            hops=listOf(hop),outcome=WebsiteDiagnosticOutcome.ATTENTION,
            findings=listOf(WebsiteDiagnosticFinding(WebsiteFindingCode.HTTP_FORBIDDEN,WebsiteFindingSeverity.WARNING,
                listOf(WebsiteFindingArgument("location","argument-secret")))),recommendations=emptyList())
    }
    @Test fun allSurfacesAreRedactedWhileFailuresAndPathsStayDistinct() {
        for(zh in listOf(false,true)) {
            val doc=WebExportMapper.freeze(WebExportSource.Website(website()),zh,3000)
            val output=doc.text()+doc.markdown()
            listOf("raw-secret","dn-secret","issuer-secret","san-secret","10.22.33.44","fingerprint-secret","ssid-secret",
                "interface-secret","proxy-secret","pac-secret","dns-secret","server-secret","header-secret","via-secret",
                "path-secret","location-secret","query-secret","fragment-secret","user-secret","password-secret","argument-secret").forEach {
                assertFalse("Leaked $it",it in output)
            }
            assertTrue("example.com" in output);assertTrue("403" in output);assertTrue("TLSv1.3" in output)
            assertTrue((if(zh) "端口: 443" else "Port: 443") in doc.text())
            assertTrue("Content-Type: text/html" in doc.text())
            assertTrue(if(zh) "失败" in output else "fail" in output)
            assertTrue(if(zh) "PAC 路径" in output else "pac" in output)
            assertTrue(if(zh) "应用直接 Socket" in output else "Direct app socket" in output)
        }
    }
    @Test fun tlsLiveAndSchemaOneHistoryUseSameFactsWithoutMutation() {
        val result=tls();val record=requireNotNull(TlsHistorySnapshotMapper.toHistoryRecord(result,2000)).copy(id=73)
        val json=record.detailJson
        val restored=requireNotNull(WebDiagnosticsHistorySnapshotResolver.resolve(record))
        val a=WebExportMapper.freeze(WebExportSource.Tls(result,2000),false,3000)
        val b=WebExportMapper.freeze(WebExportSource.restored(restored,73),false,3000)
        assertEquals(a.lines.drop(1),b.lines.drop(1))
        assertEquals(json,record.detailJson);assertEquals(73L,record.id)
        assertFalse("dn-secret" in b.text());assertTrue("untrusted" in b.text())
    }
    @Test fun websiteSchemaOneRestorationDoesNotInventDnsOrCertificateFields() {
        val record=requireNotNull(WebsiteHistorySnapshotMapper.toHistoryRecord(website())).copy(id=74)
        val restored=WebDiagnosticsHistorySnapshotResolver.resolve(record) as RestoredWebDiagnosticsHistory.Website
        assertNull(restored.snapshot.hops.single().dns.result)
        val text=WebExportMapper.freeze(WebExportSource.restored(restored,74),false,3000).text()
        assertTrue("403" in text);assertFalse("TTL" in text);assertFalse("SHA-256" in text);assertFalse("DER" in text)
        assertFalse("10.22.33.44" in text);assertFalse("path-secret" in text)
    }
    @Test fun localAndNumericHostsFailClosedWithoutDns() {
        listOf("localhost","printer.local","printer.localdomain","device.example","private.lan","router.home.arpa","10.0.0.1","127.0.0.1","100.64.1.1",
            "fe80::1%wlan0","[fd00::1]","2001:4860:4860::8888","one-label","x.onion","x.corp","x\\secret.com").forEach {
            assertEquals(it,"REDACTED",WebExportMapper.publicHost(it,"REDACTED"))
        }
        assertEquals("xn--bcher-kva.de",WebExportMapper.publicHost("bücher.de","REDACTED"))
    }
    @Test fun safeUrlsRemoveEveryNonRootIdentifier() {
        assertEquals("https://example.com:8443/ [REDACTED]",WebExportMapper.safeUrl("https://u:p@example.com:8443/a?b=c#d","REDACTED"))
        assertEquals("https://example.com/",WebExportMapper.safeUrl("https://example.com/","REDACTED"))
        listOf("/private","file:///secret","https://printer.local/a","https://[fd00::1]/a","https://example.com/\nsecret").forEach {
            assertEquals("REDACTED",WebExportMapper.safeUrl(it,"REDACTED"))
        }
    }
    @Test fun unknownNegotiatedTextCannotEscapeThroughProtocolFields() {
        val r=tls().copy(probeResult=probe().copy(session=probe().session.copy(protocol="protocol-secret",cipherSuite="cipher-secret",applicationProtocol="alpn-secret")))
        val s=WebExportMapper.freeze(WebExportSource.Tls(r,null),true,3000).text()
        listOf("protocol-secret","cipher-secret","alpn-secret").forEach {assertFalse(it in s)}
        assertTrue("字段缺失" in s);assertTrue("已脱敏" in s)
    }
    @Test fun notMeasuredAndMissingAreNotRedactionOrSuccess() {
        val r=tls().copy(probeResult=null,failureReason=TlsCheckFailureReason.DNS_TIMEOUT)
        val s=WebExportMapper.freeze(WebExportSource.Tls(r,null),true,3000).text()
        assertTrue("未检测" in s);assertTrue("字段缺失" in s);assertTrue("失败" in s);assertTrue("DNS 超时" in s)
    }
    @Test fun malformedOrUnavailableSnapshotDoesNotBecomeExportable() {
        assertNull(WebDiagnosticsHistorySnapshotResolver.resolve(com.networktoolbox.core.common.history.HistoryRecord(id=1,timestamp=1,
            type=com.networktoolbox.core.common.history.HistoryType.TLS_CHECK,title="",summary="",detailJson="{broken")))
        val unavailable=WebExportSource.Tls(tls().copy(normalizedTarget=null,probeResult=null),null)
        assertFalse(unavailable.available)
        assertTrue(runCatching {WebExportMapper.freeze(unavailable,false,3000)}.isFailure)
    }
    @Test fun markdownEscapesStructureAndUnicodeIsRetained() {
        val doc=WebExportDocument("[Title]",listOf(WebExportLine("![图片](https://example.com) <script> `code` **bold**")),true)
        assertFalse("![图片]" in doc.markdown());assertFalse("<script>" in doc.markdown())
        assertTrue("图片" in doc.markdown());assertTrue("\\[Title\\]" in doc.markdown())
    }
    @Test fun projectionFreezesMutableSnapshotLists() {
        val s=website();val hops=s.hops.toMutableList()
        val doc=WebExportMapper.freeze(WebExportSource.Website(s.copy(hops=hops)),false,3000)
        val before=doc.text();hops.clear();assertEquals(before,doc.text())
        assertTrue("1970-01-01T00:00:02Z" in before);assertTrue("1970-01-01T00:00:03Z" in before)
    }
    @Test fun failedHttpResponseIsNotLabelledNotMeasured() {
        val s=website();val h=s.hops.single().copy(http=null,httpFailure=HttpFailureReason.HEADERS_TIMEOUT)
        val text=WebExportMapper.freeze(WebExportSource.Website(s.copy(hops=listOf(h))),false,3000).text()
        assertTrue("HTTP response: Failed" in text);assertTrue("headers timeout" in text)
    }
    @Test fun invalidPortAndUnknownSchemaAreUnavailable() {
        assertFalse(WebExportSource.Tls(tls().copy(port=65536),2000).available)
        assertFalse(WebExportSource.Website(website().copy(schemaVersion=99)).available)
    }
    @Test fun syntacticallyValidMimeCannotLeakArbitraryHeaderText() {
        val s=website();val h=s.hops.single();val http=requireNotNull(h.http)
        val snapshot=s.copy(hops=listOf(h.copy(http=http.copy(responseHeaders=
            http.responseHeaders.copy(contentType="private-header-secret/credential-secret")))))
        val doc=WebExportMapper.freeze(WebExportSource.Website(snapshot),false,3000)
        assertFalse("private-header-secret" in doc.text()+doc.markdown())
        assertFalse("credential-secret" in doc.text()+doc.markdown())
        assertTrue("Content-Type: Redacted" in doc.text())
    }
    @Test fun localSuffixNamesAreRedactedFromTlsAndWebsiteDocuments() {
        for(host in listOf("printer.localdomain","device.example")) {
            val s=website()
            val target=requireNotNull(s.normalizedTarget).copy(originalHost=host,asciiHost=host,
                executionUrl="https://$host/private",displayUrlRedacted="https://$host/private")
            val sources=listOf(WebExportSource.Tls(tls().copy(normalizedTarget=host),2000),
                WebExportSource.Website(s.copy(normalizedTarget=target,hops=listOf(s.hops.single().copy(target=target)))))
            for(source in sources) for(zh in listOf(false,true)) {
                val doc=WebExportMapper.freeze(source,zh,3000)
                assertFalse(host in doc.text()+doc.markdown())
                assertTrue((if(zh) "已脱敏" else "Redacted") in doc.text())
            }
        }
    }
}
