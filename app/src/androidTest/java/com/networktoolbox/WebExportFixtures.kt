package com.networktoolbox

import com.networktoolbox.core.network.http.*
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.*
import com.networktoolbox.core.network.website.*
import com.networktoolbox.feature.webdiagnostics.domain.*

/** Synthetic data only; fail-on-call real engines remain replaced by RecreationNetworkModule. */
internal object WebExportFixtures {
    val timestamp=1_700_000_000_100L
    val secrets=listOf("path-sentinel","query-sentinel","password-sentinel","dn-sentinel","private-sentinel.lan","10.23.45.67","ssid-sentinel","raw-sentinel")
    fun tls():TlsCheckResult {
        val chain=(1..12).map {i->PresentedCertificate("CN=dn-sentinel-$i","CN=private-sentinel.lan",
            listOf("private-sentinel.lan"),listOf("10.23.45.67"),timestamp-100000,timestamp+100000,
            CertificateValidityStatus.VALID,1,false)}
        val probe=TlsProbeResult("example.com","example.com","10.23.45.67",443,
            connection=TlsConnectionEvidence(TcpConnectOutcome.CONNECTED,"10.23.45.67",443,3),
            session=TlsSessionEvidence(TlsHandshakeStatus.SUCCESS,12,"TLSv1.3","TLS_AES_128_GCM_SHA256","h2"),
            certificate=CertificateEvidence(chain.first(),chain,chain.size),trustStatus=CertificateTrustStatus.UNTRUSTED,
            hostnameStatus=HostnameVerificationStatus.MATCH,certificateIssues=setOf(CertificateIssue.UNTRUSTED_CHAIN),
            failureReason=TlsFailureReason.TRUST_FAILED,networkFingerprint="raw-sentinel",vpnActive=true)
        return TlsCheckResult("example.com","example.com","10.23.45.67",443,probe,null,
            TlsCheckAnalysis(TlsCheckOutcome.ATTENTION,listOf(TlsCheckFindingCode.UNTRUSTED),listOf(TlsCheckRecommendationCode.CHECK_PRIVATE_CA_OR_SELF_SIGNED)),45)
    }
    fun website():WebsiteDiagnosticSnapshot {
        val url="https://example.com/path-sentinel?query-sentinel#fragment-sentinel"
        val target=NormalizedWebsiteTarget(WebsiteScheme.HTTPS,false,"example.com","example.com",WebsiteHostType.DOMAIN,443,
            "/path-sentinel","query-sentinel","fragment-sentinel",url,url)
        val hop=WebsiteDiagnosticHop(0,target,HttpTransportPath.HTTP_PROXY,
            WebsiteDnsEvidence(WebsiteStageStatus.PASS,null,listOf("10.23.45.67"),false,4,null),
            WebsiteTcpEvidence(WebsiteStageStatus.PASS,listOf(WebsiteTcpAttemptEvidence("10.23.45.67",TcpConnectOutcome.CONNECTED,5)),"10.23.45.67",5),
            WebsiteTlsEvidence(WebsiteStageStatus.ATTENTION,tls().probeResult,12),
            HttpProbeResult(url,"GET",403,HttpStatusCategory.CLIENT_RESPONSE_4XX,HttpProtocol.HTTP_2,
                HttpResponseHeaders("https://example.com/path-sentinel?query-sentinel","raw-sentinel","text/html","raw-sentinel"),
                10,HttpTransportPath.HTTP_PROXY,0,null),null,null,31)
        return WebsiteDiagnosticSnapshot(enteredInputRedacted=url,normalizedTarget=target,targetFailure=null,sessionFailure=null,
            networkContext=NetworkContext.unknown().copy(wifiName="ssid-sentinel",ipv4Address="10.23.45.67",vpnActive=true),
            networkFingerprint="raw-sentinel",startedAtEpochMs=timestamp-100,endedAtEpochMs=timestamp,totalDurationMs=100,
            hops=listOf(hop,hop.copy(index=1)),outcome=WebsiteDiagnosticOutcome.ATTENTION,
            findings=listOf(WebsiteDiagnosticFinding(WebsiteFindingCode.HTTP_FORBIDDEN,WebsiteFindingSeverity.WARNING,
                listOf(WebsiteFindingArgument("private","raw-sentinel")))),recommendations=emptyList())
    }
}
