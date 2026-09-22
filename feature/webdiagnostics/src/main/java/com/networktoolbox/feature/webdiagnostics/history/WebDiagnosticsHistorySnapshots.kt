package com.networktoolbox.feature.webdiagnostics.history

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.http.HttpFailureReason
import com.networktoolbox.core.network.http.HttpProbeResult
import com.networktoolbox.core.network.http.HttpProtocol
import com.networktoolbox.core.network.http.HttpResponseHeaders
import com.networktoolbox.core.network.http.HttpStatusCategory
import com.networktoolbox.core.network.http.HttpTransportPath
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import com.networktoolbox.core.network.tls.CertificateEvidence
import com.networktoolbox.core.network.tls.CertificateIssue
import com.networktoolbox.core.network.tls.CertificateTrustStatus
import com.networktoolbox.core.network.tls.CertificateValidityStatus
import com.networktoolbox.core.network.tls.HostnameVerificationStatus
import com.networktoolbox.core.network.tls.PresentedCertificate
import com.networktoolbox.core.network.tls.TlsConnectionEvidence
import com.networktoolbox.core.network.tls.TlsFailureReason
import com.networktoolbox.core.network.tls.TlsHandshakeStatus
import com.networktoolbox.core.network.tls.TlsProbeResult
import com.networktoolbox.core.network.tls.TlsSessionEvidence
import com.networktoolbox.core.network.tls.TlsTransportPath
import com.networktoolbox.core.network.website.NormalizedWebsiteTarget
import com.networktoolbox.core.network.website.WebsiteDiagnosticFinding
import com.networktoolbox.core.network.website.WebsiteDiagnosticHop
import com.networktoolbox.core.network.website.WebsiteDiagnosticOutcome
import com.networktoolbox.core.network.website.WebsiteDiagnosticRecommendation
import com.networktoolbox.core.network.website.WebsiteDiagnosticSnapshot
import com.networktoolbox.core.network.website.WebsiteDnsEvidence
import com.networktoolbox.core.network.website.WebsiteDnsFailureReason
import com.networktoolbox.core.network.website.WebsiteFindingArgument
import com.networktoolbox.core.network.website.WebsiteFindingCode
import com.networktoolbox.core.network.website.WebsiteFindingSeverity
import com.networktoolbox.core.network.website.WebsiteHostType
import com.networktoolbox.core.network.website.WebsiteRecommendationCode
import com.networktoolbox.core.network.website.WebsiteRedirectFailureReason
import com.networktoolbox.core.network.website.WebsiteScheme
import com.networktoolbox.core.network.website.WebsiteSessionFailureReason
import com.networktoolbox.core.network.website.WebsiteStageStatus
import com.networktoolbox.core.network.website.WebsiteTargetFailureReason
import com.networktoolbox.core.network.website.WebsiteTcpAttemptEvidence
import com.networktoolbox.core.network.website.WebsiteTcpEvidence
import com.networktoolbox.core.network.website.WebsiteTlsEvidence
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckAnalysis
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckFailureReason
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckFindingCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckOutcome
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckRecommendationCode
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import java.net.URI

sealed interface RestoredWebDiagnosticsHistory {
    val completedAtEpochMs: Long

    data class Tls(
        override val completedAtEpochMs: Long,
        val result: TlsCheckResult,
    ) : RestoredWebDiagnosticsHistory

    data class Website(
        override val completedAtEpochMs: Long,
        val snapshot: WebsiteDiagnosticSnapshot,
    ) : RestoredWebDiagnosticsHistory
}

object WebDiagnosticsHistorySnapshotResolver {
    fun resolve(record: HistoryRecord): RestoredWebDiagnosticsHistory? = when (record.type) {
        HistoryType.TLS_CHECK -> TlsHistorySnapshotMapper.restore(record)
        HistoryType.WEBSITE_DIAGNOSTIC -> WebsiteHistorySnapshotMapper.restore(record)
        else -> null
    }

    fun canOpen(record: HistoryRecord): Boolean = resolve(record) != null
}

object TlsHistorySnapshotMapper {
    const val SCHEMA_VERSION = 1

    fun toHistoryRecord(result: TlsCheckResult, completedAtEpochMs: Long): HistoryRecord? {
        if (result.analysis.outcome == TlsCheckOutcome.NETWORK_CHANGED) return null
        val target = result.normalizedTarget ?: result.enteredTarget
        val targetDisplay = "$target:${result.port}"
        val json = historyJsonObject(
            "schemaVersion" to SCHEMA_VERSION.toString(),
            "kind" to historyJsonString("TLS_CHECK"),
            "completedAt" to completedAtEpochMs.toString(),
            "targetDisplay" to historyJsonString(targetDisplay),
            "enteredTarget" to historyJsonString(result.enteredTarget),
            "normalizedTarget" to historyJsonNullable(result.normalizedTarget),
            "selectedAddress" to historyJsonNullable(result.selectedAddress),
            "port" to result.port.toString(),
            "totalDurationMs" to result.totalDurationMs.toString(),
            "outcome" to historyJsonString(result.analysis.outcome.name),
            "failureReason" to historyJsonNullable(result.failureReason?.name),
            "primaryFindingCode" to historyJsonNullable(result.analysis.findings.firstOrNull()?.name),
            "tlsProtocol" to historyJsonNullable(result.probeResult?.session?.protocol),
            "trustStatus" to historyJsonNullable(result.probeResult?.trustStatus?.name),
            "findings" to historyJsonStringArray(result.analysis.findings.map { it.name }),
            "recommendations" to historyJsonStringArray(result.analysis.recommendations.map { it.name }),
            "probe" to (result.probeResult?.let(TlsProbeHistoryCodec::encode) ?: "null"),
        )
        return HistoryRecord(
            timestamp = completedAtEpochMs,
            type = HistoryType.TLS_CHECK,
            title = "TLS_CHECK",
            summary = result.analysis.outcome.name,
            detailJson = json,
        )
    }

    fun restore(record: HistoryRecord): RestoredWebDiagnosticsHistory.Tls? = runCatching {
        if (record.type != HistoryType.TLS_CHECK) return null
        val root = HistoryJsonParser(record.detailJson).parse() as? HistoryJsonObject ?: return null
        if (root.int("schemaVersion") != SCHEMA_VERSION || root.string("kind") != "TLS_CHECK") return null
        val outcome = root.enum<TlsCheckOutcome>("outcome") ?: return null
        val findings = root.stringList("findings")?.map { TlsCheckFindingCode.valueOf(it) } ?: return null
        val recommendations = root.stringList("recommendations")?.map { TlsCheckRecommendationCode.valueOf(it) } ?: return null
        RestoredWebDiagnosticsHistory.Tls(
            completedAtEpochMs = root.long("completedAt") ?: record.timestamp,
            result = TlsCheckResult(
                enteredTarget = root.string("enteredTarget") ?: return null,
                normalizedTarget = root.string("normalizedTarget"),
                selectedAddress = root.string("selectedAddress"),
                port = root.int("port") ?: return null,
                probeResult = root.obj("probe")?.let(TlsProbeHistoryCodec::decode),
                failureReason = root.string("failureReason")?.let(TlsCheckFailureReason::valueOf),
                analysis = TlsCheckAnalysis(outcome, findings, recommendations),
                totalDurationMs = root.long("totalDurationMs") ?: return null,
            ),
        )
    }.getOrNull()
}

object WebsiteHistorySnapshotMapper {
    const val SCHEMA_VERSION = 1

    fun toHistoryRecord(snapshot: WebsiteDiagnosticSnapshot): HistoryRecord? {
        if (snapshot.outcome in setOf(WebsiteDiagnosticOutcome.STOPPED, WebsiteDiagnosticOutcome.NETWORK_CHANGED)) return null
        val targetDisplay = snapshot.normalizedTarget?.displayUrlRedacted
            ?.let(::redactUrlForHistory)
            ?: snapshot.enteredInputRedacted.let(::redactUrlForHistory)
            ?: snapshot.normalizedTarget?.asciiHost
            ?: ""
        val lastHttp = snapshot.hops.lastOrNull()?.http
        val json = historyJsonObject(
            "schemaVersion" to SCHEMA_VERSION.toString(),
            "kind" to historyJsonString("WEBSITE_DIAGNOSTIC"),
            "completedAt" to snapshot.endedAtEpochMs.toString(),
            "targetDisplay" to historyJsonString(targetDisplay),
            "outcome" to historyJsonString(snapshot.outcome.name),
            "httpStatus" to historyJsonNullable(lastHttp?.statusCode),
            "primaryFindingCode" to historyJsonNullable(snapshot.findings.firstOrNull()?.code?.name),
            "snapshot" to encodeSnapshot(snapshot),
        )
        return HistoryRecord(
            timestamp = snapshot.endedAtEpochMs,
            type = HistoryType.WEBSITE_DIAGNOSTIC,
            title = "WEBSITE_DIAGNOSTIC",
            summary = snapshot.outcome.name,
            detailJson = json,
        )
    }

    fun restore(record: HistoryRecord): RestoredWebDiagnosticsHistory.Website? = runCatching {
        if (record.type != HistoryType.WEBSITE_DIAGNOSTIC) return null
        val root = HistoryJsonParser(record.detailJson).parse() as? HistoryJsonObject ?: return null
        if (root.int("schemaVersion") != SCHEMA_VERSION || root.string("kind") != "WEBSITE_DIAGNOSTIC") return null
        RestoredWebDiagnosticsHistory.Website(
            completedAtEpochMs = root.long("completedAt") ?: record.timestamp,
            snapshot = decodeSnapshot(root.obj("snapshot") ?: return null) ?: return null,
        )
    }.getOrNull()

    private fun encodeSnapshot(snapshot: WebsiteDiagnosticSnapshot): String = historyJsonObject(
        "enteredInputRedacted" to historyJsonNullable(redactUrlForHistory(snapshot.enteredInputRedacted)),
        "normalizedTarget" to (snapshot.normalizedTarget?.let(::encodeTarget) ?: "null"),
        "targetFailure" to historyJsonNullable(snapshot.targetFailure?.name),
        "sessionFailure" to historyJsonNullable(snapshot.sessionFailure?.name),
        "networkContext" to encodeNetworkContext(snapshot.networkContext),
        "networkFingerprint" to historyJsonString(snapshot.networkFingerprint),
        "startedAt" to snapshot.startedAtEpochMs.toString(),
        "endedAt" to snapshot.endedAtEpochMs.toString(),
        "totalDurationMs" to snapshot.totalDurationMs.toString(),
        "hops" to historyJsonArray(snapshot.hops.map(::encodeHop)),
        "outcome" to historyJsonString(snapshot.outcome.name),
        "findings" to historyJsonArray(snapshot.findings.map(::encodeFinding)),
        "recommendations" to historyJsonArray(snapshot.recommendations.map { historyJsonString(it.code.name) }),
    )

    private fun decodeSnapshot(root: HistoryJsonObject): WebsiteDiagnosticSnapshot? {
        val hops = root.array("hops")?.values?.map {
            decodeHop(it as? HistoryJsonObject ?: return null) ?: return null
        } ?: return null
        val findings = root.array("findings")?.values?.map {
            decodeFinding(it as? HistoryJsonObject ?: return null) ?: return null
        } ?: return null
        return WebsiteDiagnosticSnapshot(
            schemaVersion = SCHEMA_VERSION,
            enteredInputRedacted = root.string("enteredInputRedacted") ?: "",
            normalizedTarget = root.obj("normalizedTarget")?.let(::decodeTarget),
            targetFailure = root.string("targetFailure")?.let(WebsiteTargetFailureReason::valueOf),
            sessionFailure = root.string("sessionFailure")?.let(WebsiteSessionFailureReason::valueOf),
            networkContext = root.obj("networkContext")?.let(::decodeNetworkContext) ?: return null,
            networkFingerprint = root.string("networkFingerprint") ?: return null,
            startedAtEpochMs = root.long("startedAt") ?: return null,
            endedAtEpochMs = root.long("endedAt") ?: return null,
            totalDurationMs = root.long("totalDurationMs") ?: return null,
            hops = hops,
            outcome = root.enum<WebsiteDiagnosticOutcome>("outcome") ?: return null,
            findings = findings,
            recommendations = root.stringList("recommendations")
                ?.map { WebsiteDiagnosticRecommendation(WebsiteRecommendationCode.valueOf(it)) }
                ?: return null,
        )
    }

    private fun encodeTarget(target: NormalizedWebsiteTarget): String {
        val safeDisplay = redactUrlForHistory(target.displayUrlRedacted) ?: ""
        return historyJsonObject(
            "scheme" to historyJsonString(target.scheme.name),
            "schemeWasInferred" to target.schemeWasInferred.toString(),
            "originalHost" to historyJsonString(target.originalHost),
            "asciiHost" to historyJsonString(target.asciiHost),
            "hostType" to historyJsonString(target.hostType.name),
            "port" to target.port.toString(),
            "encodedPath" to historyJsonString(target.encodedPath),
            "displayUrlRedacted" to historyJsonString(safeDisplay),
        )
    }

    private fun decodeTarget(root: HistoryJsonObject): NormalizedWebsiteTarget? {
        val scheme = root.enum<WebsiteScheme>("scheme") ?: return null
        val host = root.string("asciiHost") ?: return null
        val port = root.int("port") ?: return null
        val path = root.string("encodedPath") ?: "/"
        val display = root.string("displayUrlRedacted") ?: return null
        return NormalizedWebsiteTarget(
            scheme = scheme,
            schemeWasInferred = root.boolean("schemeWasInferred") ?: false,
            originalHost = root.string("originalHost") ?: host,
            asciiHost = host,
            hostType = root.enum<WebsiteHostType>("hostType") ?: return null,
            port = port,
            encodedPath = path,
            encodedQuery = null,
            fragment = null,
            executionUrl = display,
            displayUrlRedacted = display,
        )
    }

    private fun encodeHop(hop: WebsiteDiagnosticHop): String = historyJsonObject(
        "index" to hop.index.toString(),
        "target" to encodeTarget(hop.target),
        "plannedHttpTransport" to historyJsonString(hop.plannedHttpTransport.name),
        "dns" to encodeDns(hop.dns),
        "tcp" to encodeTcp(hop.tcp),
        "tls" to encodeTls(hop.tls),
        "http" to (hop.http?.let(::encodeHttp) ?: "null"),
        "httpFailure" to historyJsonNullable(hop.httpFailure?.name),
        "redirectFailure" to historyJsonNullable(hop.redirectFailure?.name),
        "durationMs" to hop.durationMs.toString(),
    )

    private fun decodeHop(root: HistoryJsonObject): WebsiteDiagnosticHop? {
        return WebsiteDiagnosticHop(
            index = root.int("index") ?: return null,
            target = root.obj("target")?.let(::decodeTarget) ?: return null,
            plannedHttpTransport = root.enum<HttpTransportPath>("plannedHttpTransport") ?: return null,
            dns = root.obj("dns")?.let(::decodeDns) ?: return null,
            tcp = root.obj("tcp")?.let(::decodeTcp) ?: return null,
            tls = root.obj("tls")?.let(::decodeTls) ?: return null,
            http = root.obj("http")?.let(::decodeHttp),
            httpFailure = root.string("httpFailure")?.let(HttpFailureReason::valueOf),
            redirectFailure = root.string("redirectFailure")?.let(WebsiteRedirectFailureReason::valueOf),
            durationMs = root.long("durationMs") ?: return null,
        )
    }

    private fun encodeDns(value: WebsiteDnsEvidence): String = historyJsonObject(
        "status" to historyJsonString(value.status.name),
        "candidateAddresses" to historyJsonStringArray(value.candidateAddresses),
        "fakeIpDetected" to value.fakeIpDetected.toString(),
        "durationMs" to historyJsonNullable(value.durationMs),
        "failureReason" to historyJsonNullable(value.failureReason?.name),
    )

    private fun decodeDns(root: HistoryJsonObject): WebsiteDnsEvidence? {
        return WebsiteDnsEvidence(
            status = root.enum<WebsiteStageStatus>("status") ?: return null,
            result = null,
            candidateAddresses = root.stringList("candidateAddresses") ?: return null,
            fakeIpDetected = root.boolean("fakeIpDetected") ?: false,
            durationMs = root.long("durationMs"),
            failureReason = root.string("failureReason")?.let(WebsiteDnsFailureReason::valueOf),
        )
    }

    private fun encodeTcp(value: WebsiteTcpEvidence): String = historyJsonObject(
        "status" to historyJsonString(value.status.name),
        "attempts" to historyJsonArray(value.attempts.map { attempt -> historyJsonObject(
            "address" to historyJsonString(attempt.address),
            "outcome" to historyJsonString(attempt.outcome.name),
            "durationMs" to historyJsonNullable(attempt.durationMs),
        ) }),
        "selectedAddress" to historyJsonNullable(value.selectedAddress),
        "durationMs" to historyJsonNullable(value.durationMs),
    )

    private fun decodeTcp(root: HistoryJsonObject): WebsiteTcpEvidence? {
        val attempts = root.array("attempts")?.values?.map { raw ->
            val item = raw as? HistoryJsonObject ?: return null
            WebsiteTcpAttemptEvidence(
                address = item.string("address") ?: return null,
                outcome = item.enum<TcpConnectOutcome>("outcome") ?: return null,
                durationMs = item.long("durationMs"),
            )
        } ?: return null
        return WebsiteTcpEvidence(
            status = root.enum<WebsiteStageStatus>("status") ?: return null,
            attempts = attempts,
            selectedAddress = root.string("selectedAddress"),
            durationMs = root.long("durationMs"),
        )
    }

    private fun encodeTls(value: WebsiteTlsEvidence): String = historyJsonObject(
        "status" to historyJsonString(value.status.name),
        "result" to (value.result?.let(TlsProbeHistoryCodec::encode) ?: "null"),
        "durationMs" to historyJsonNullable(value.durationMs),
    )

    private fun decodeTls(root: HistoryJsonObject): WebsiteTlsEvidence? {
        return WebsiteTlsEvidence(
            status = root.enum<WebsiteStageStatus>("status") ?: return null,
            result = root.obj("result")?.let(TlsProbeHistoryCodec::decode),
            durationMs = root.long("durationMs"),
        )
    }

    private fun encodeHttp(value: HttpProbeResult): String = historyJsonObject(
        "requestUrlRedacted" to historyJsonNullable(redactUrlForHistory(value.requestUrlRedacted)),
        "method" to historyJsonString(value.method),
        "statusCode" to historyJsonNullable(value.statusCode),
        "statusCategory" to historyJsonNullable(value.statusCategory?.name),
        "protocol" to historyJsonNullable(value.protocol?.name),
        "headers" to historyJsonObject(
            "location" to historyJsonNullable(value.responseHeaders.location?.let(::redactUrlForHistory)),
            "server" to historyJsonNullable(value.responseHeaders.server),
            "contentType" to historyJsonNullable(value.responseHeaders.contentType),
            "via" to historyJsonNullable(value.responseHeaders.via),
        ),
        "durationMs" to value.durationMs.toString(),
        "transportPath" to historyJsonString(value.transportPath.name),
        "failureReason" to historyJsonNullable(value.failureReason?.name),
    )

    private fun decodeHttp(root: HistoryJsonObject): HttpProbeResult? {
        val headers = root.obj("headers") ?: return null
        return HttpProbeResult(
            requestUrlRedacted = root.string("requestUrlRedacted") ?: "",
            method = root.string("method") ?: return null,
            statusCode = root.int("statusCode"),
            statusCategory = root.string("statusCategory")?.let(HttpStatusCategory::valueOf),
            protocol = root.string("protocol")?.let(HttpProtocol::valueOf),
            responseHeaders = HttpResponseHeaders(
                location = headers.string("location"),
                server = headers.string("server"),
                contentType = headers.string("contentType"),
                via = headers.string("via"),
            ),
            durationMs = root.long("durationMs") ?: return null,
            transportPath = root.enum<HttpTransportPath>("transportPath") ?: return null,
            bodyBytesRead = 0,
            failureReason = root.string("failureReason")?.let(HttpFailureReason::valueOf),
        )
    }

    private fun encodeFinding(value: WebsiteDiagnosticFinding): String = historyJsonObject(
        "code" to historyJsonString(value.code.name),
        "severity" to historyJsonString(value.severity.name),
        "arguments" to historyJsonArray(value.arguments.map { argument -> historyJsonObject(
            "name" to historyJsonString(argument.name),
            "value" to historyJsonString(argument.value),
        ) }),
    )

    private fun decodeFinding(root: HistoryJsonObject): WebsiteDiagnosticFinding? {
        val arguments = root.array("arguments")?.values?.map { raw ->
            val item = raw as? HistoryJsonObject ?: return null
            WebsiteFindingArgument(item.string("name") ?: return null, item.string("value") ?: return null)
        } ?: return null
        return WebsiteDiagnosticFinding(
            code = root.enum<WebsiteFindingCode>("code") ?: return null,
            severity = root.enum<WebsiteFindingSeverity>("severity") ?: return null,
            arguments = arguments,
        )
    }
}

private object TlsProbeHistoryCodec {
    fun encode(value: TlsProbeResult): String = historyJsonObject(
        "serverName" to historyJsonString(value.serverName),
        "normalizedServerName" to historyJsonNullable(value.normalizedServerName),
        "connectAddress" to historyJsonString(value.connectAddress),
        "port" to value.port.toString(),
        "transportPath" to historyJsonString(value.transportPath.name),
        "connection" to historyJsonObject(
            "outcome" to historyJsonNullable(value.connection.outcome?.name),
            "connectAddress" to historyJsonString(value.connection.connectAddress),
            "port" to value.connection.port.toString(),
            "durationMs" to historyJsonNullable(value.connection.durationMs),
        ),
        "session" to historyJsonObject(
            "status" to historyJsonString(value.session.status.name),
            "durationMs" to historyJsonNullable(value.session.durationMs),
            "protocol" to historyJsonNullable(value.session.protocol),
            "cipherSuite" to historyJsonNullable(value.session.cipherSuite),
            "applicationProtocol" to historyJsonNullable(value.session.applicationProtocol),
        ),
        "certificate" to historyJsonObject(
            "leaf" to (value.certificate.leaf?.let { encodeCertificate(it, includeSans = true) } ?: "null"),
            "presentedChain" to historyJsonArray(value.certificate.presentedChain.map { encodeCertificate(it, includeSans = false) }),
            "presentedChainLength" to value.certificate.presentedChainLength.toString(),
        ),
        "trustStatus" to historyJsonString(value.trustStatus.name),
        "hostnameStatus" to historyJsonString(value.hostnameStatus.name),
        "certificateIssues" to historyJsonStringArray(value.certificateIssues.map { it.name }),
        "failureReason" to historyJsonNullable(value.failureReason?.name),
        "networkFingerprint" to historyJsonNullable(value.networkFingerprint),
        "vpnActive" to historyJsonNullable(value.vpnActive),
    )

    fun decode(root: HistoryJsonObject): TlsProbeResult? {
        val connection = root.obj("connection") ?: return null
        val session = root.obj("session") ?: return null
        val certificate = root.obj("certificate") ?: return null
        return TlsProbeResult(
            serverName = root.string("serverName") ?: return null,
            normalizedServerName = root.string("normalizedServerName"),
            connectAddress = root.string("connectAddress") ?: return null,
            port = root.int("port") ?: return null,
            transportPath = root.enum<TlsTransportPath>("transportPath") ?: return null,
            connection = TlsConnectionEvidence(
                outcome = connection.string("outcome")?.let(TcpConnectOutcome::valueOf),
                connectAddress = connection.string("connectAddress") ?: return null,
                port = connection.int("port") ?: return null,
                durationMs = connection.long("durationMs"),
            ),
            session = TlsSessionEvidence(
                status = session.enum<TlsHandshakeStatus>("status") ?: return null,
                durationMs = session.long("durationMs"),
                protocol = session.string("protocol"),
                cipherSuite = session.string("cipherSuite"),
                applicationProtocol = session.string("applicationProtocol"),
            ),
            certificate = CertificateEvidence(
                leaf = certificate.obj("leaf")?.let(::decodeCertificate),
                presentedChain = certificate.array("presentedChain")?.values?.map {
                    decodeCertificate(it as? HistoryJsonObject ?: return null) ?: return null
                } ?: return null,
                presentedChainLength = certificate.int("presentedChainLength") ?: return null,
            ),
            trustStatus = root.enum<CertificateTrustStatus>("trustStatus") ?: return null,
            hostnameStatus = root.enum<HostnameVerificationStatus>("hostnameStatus") ?: return null,
            certificateIssues = root.stringList("certificateIssues")?.map(CertificateIssue::valueOf)?.toSet() ?: return null,
            failureReason = root.string("failureReason")?.let(TlsFailureReason::valueOf),
            networkFingerprint = root.string("networkFingerprint"),
            vpnActive = root.boolean("vpnActive"),
        )
    }

    private fun encodeCertificate(value: PresentedCertificate, includeSans: Boolean): String = historyJsonObject(
        "subject" to historyJsonString(value.subject),
        "issuer" to historyJsonString(value.issuer),
        "dnsSans" to historyJsonStringArray(if (includeSans) value.dnsSubjectAlternativeNames else emptyList()),
        "ipSans" to historyJsonStringArray(if (includeSans) value.ipSubjectAlternativeNames else emptyList()),
        "validFrom" to value.validFromEpochMs.toString(),
        "validUntil" to value.validUntilEpochMs.toString(),
        "validityStatus" to historyJsonString(value.validityStatus.name),
        "remainingValidityDays" to historyJsonNullable(value.remainingValidityDays),
        "selfSigned" to value.selfSigned.toString(),
    )

    private fun decodeCertificate(root: HistoryJsonObject): PresentedCertificate? {
        return PresentedCertificate(
            subject = root.string("subject") ?: return null,
            issuer = root.string("issuer") ?: return null,
            dnsSubjectAlternativeNames = root.stringList("dnsSans") ?: return null,
            ipSubjectAlternativeNames = root.stringList("ipSans") ?: return null,
            validFromEpochMs = root.long("validFrom") ?: return null,
            validUntilEpochMs = root.long("validUntil") ?: return null,
            validityStatus = root.enum<CertificateValidityStatus>("validityStatus") ?: return null,
            remainingValidityDays = root.long("remainingValidityDays"),
            selfSigned = root.boolean("selfSigned") ?: return null,
        )
    }
}

private fun encodeNetworkContext(value: NetworkContext): String = historyJsonObject(
    "connectionType" to historyJsonString(value.connectionType.name),
    "ipv4Address" to historyJsonNullable(value.ipv4Address),
    "ipv6Address" to historyJsonNullable(value.ipv6Address),
    "gateway" to historyJsonNullable(value.gateway),
    "dnsServers" to historyJsonStringArray(value.dnsServers),
    "vpnActive" to historyJsonNullable(value.vpnActive),
    "activeNetworkAvailable" to historyJsonNullable(value.activeNetworkAvailable),
    "validated" to historyJsonNullable(value.validated),
    "ipv6Addresses" to historyJsonStringArray(value.ipv6Addresses),
    "privateDnsActive" to historyJsonNullable(value.privateDnsActive),
    "privateDnsServerName" to historyJsonNullable(value.privateDnsServerName),
    "proxyHost" to historyJsonNullable(value.proxyHost),
    "proxyPort" to historyJsonNullable(value.proxyPort),
)

private fun decodeNetworkContext(root: HistoryJsonObject): NetworkContext? {
    return NetworkContext(
        connectionType = root.enum<ConnectionType>("connectionType") ?: return null,
        ipv4Address = root.string("ipv4Address"),
        ipv6Address = root.string("ipv6Address"),
        gateway = root.string("gateway"),
        dnsServers = root.stringList("dnsServers") ?: return null,
        vpnActive = root.boolean("vpnActive"),
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = root.boolean("activeNetworkAvailable"),
        validated = root.boolean("validated"),
        ipv6Addresses = root.stringList("ipv6Addresses") ?: emptyList(),
        privateDnsActive = root.boolean("privateDnsActive"),
        privateDnsServerName = root.string("privateDnsServerName"),
        proxyHost = root.string("proxyHost"),
        proxyPort = root.int("proxyPort"),
    )
}

internal fun redactUrlForHistory(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.rawUserInfo == null)
    if (uri.isAbsolute) {
        val host = uri.host ?: return@runCatching null
        URI(uri.scheme.lowercase(), null, host, uri.port, uri.rawPath?.ifBlank { "/" } ?: "/", null, null).toASCIIString()
    } else {
        val path = uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/"
        URI(null, null, path, null, null).toASCIIString()
    }
}.getOrNull()
