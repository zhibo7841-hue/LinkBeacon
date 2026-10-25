package com.networktoolbox.feature.webdiagnostics.export

import com.networktoolbox.core.network.tls.*
import com.networktoolbox.core.network.website.*
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckResult
import com.networktoolbox.feature.webdiagnostics.history.RestoredWebDiagnosticsHistory
import java.net.IDN
import java.net.URI
import java.time.Instant
import java.util.Locale

enum class WebExportFormat(val extension:String,val mimeType:String) {
    TXT("txt","text/plain"), MARKDOWN("md","text/markdown"), PDF("pdf","application/pdf")
}
sealed interface WebExportSource {
    val savedId:Long?
    val key:String
    val available:Boolean
    data class Tls(val result:TlsCheckResult,val observedAt:Long?,override val savedId:Long?=null):WebExportSource {
        override val key get()="tls:${savedId ?: "live"}:$observedAt"
        override val available get()=(result.normalizedTarget?.isNotBlank()==true || result.probeResult!=null) &&
            result.port in 1..65535 && result.totalDurationMs>=0
    }
    data class Website(val snapshot:WebsiteDiagnosticSnapshot,override val savedId:Long?=null):WebExportSource {
        override val key get()="web:${savedId ?: "live"}:${snapshot.endedAtEpochMs}"
        override val available get()=snapshot.schemaVersion==1 && snapshot.normalizedTarget?.port in 1..65535 &&
            snapshot.totalDurationMs>=0 && snapshot.endedAtEpochMs>=snapshot.startedAtEpochMs &&
            snapshot.outcome!=WebsiteDiagnosticOutcome.STOPPED
    }
    companion object {
        fun restored(value:RestoredWebDiagnosticsHistory,id:Long?)=when(value) {
            is RestoredWebDiagnosticsHistory.Tls -> Tls(value.result,value.completedAtEpochMs,id)
            is RestoredWebDiagnosticsHistory.Website -> Website(value.snapshot,id)
        }
    }
}
data class WebExportLine(val text:String,val heading:Boolean=false)
/** Contains only frozen, localized, allowlisted strings. No original snapshot or arbitrary metadata. */
data class WebExportDocument(val title:String,val lines:List<WebExportLine>,val chinese:Boolean) {
    fun text()=(listOf(title)+lines.map {it.text}).joinToString("\n")
    fun markdown()=(listOf("# "+escape(title))+lines.map { (if(it.heading) "## " else "")+escape(it.text) }).joinToString("\n")
    companion object {
        private fun escape(s:String)=buildString {
            s.forEach {c->if(c in "\\`*_{}[]<>#!|-=+~.") append('\\');append(c)}
        }
    }
}

/** No resolver, Engine, repository or Android dependencies. Policy v1 is intentionally conservative. */
object WebExportMapper {
    fun freeze(source:WebExportSource,chinese:Boolean,exportedAt:Long):WebExportDocument {
        require(source.available)
        val w=Words(chinese);val lines=mutableListOf<WebExportLine>()
        fun row(en:String,zh:String,value:String?) {lines+=WebExportLine(w.t(en,zh)+": "+(value ?: w.missing))}
        fun head(en:String,zh:String) {lines+=WebExportLine(w.t(en,zh),true)}
        fun number(v:Long?)=v?.takeIf {it>=0}?.let {"$it ms"}
        fun date(v:Long?)=v?.takeIf {it>0}?.let {runCatching {Instant.ofEpochMilli(it).toString()}.getOrNull()}
        fun tls(p:TlsProbeResult?) {
            head("TLS evidence","TLS 证据")
            if(p==null) {row("Measurement","检测",w.unmeasured);return}
            row("TLS transport","TLS 检测路径",w.t("Direct app socket","应用直接 Socket"))
            row("TCP outcome","TCP 结果",p.connection.outcome?.let(w::code))
            row("TCP duration","TCP 耗时",number(p.connection.durationMs))
            row("Connected address","连接地址",w.redacted)
            row("Handshake","握手",w.code(p.session.status))
            row("Handshake duration","握手耗时",number(p.session.durationMs))
            row("TLS protocol","TLS 协议",safeToken(p.session.protocol,protocols,w.redacted))
            row("Cipher suite","密码套件",safeToken(p.session.cipherSuite,ciphers,w.redacted))
            row("ALPN","ALPN",safeToken(p.session.applicationProtocol,setOf("h2","http/1.1","http/1.0"),w.redacted))
            row("Trust","证书信任",w.code(p.trustStatus));row("Hostname verification","域名校验",w.code(p.hostnameStatus))
            row("Failure category","失败原因类别",p.failureReason?.let(w::code))
            row("VPN","VPN",p.vpnActive?.let(w::yesNo))
            head("Presented certificates","服务器呈现的证书")
            row("Presented chain length","呈现的证书链长度",p.certificate.presentedChainLength.takeIf {it>=0}?.toString())
            row("Subject / issuer / SAN","主题 / 签发者 / SAN",w.redacted)
            val certificates=if(p.certificate.presentedChain.isNotEmpty()) p.certificate.presentedChain.take(16)
                else listOfNotNull(p.certificate.leaf)
            if(certificates.isEmpty()) row("Certificate","证书",w.missing)
            certificates.forEachIndexed {i,c->
                row("Certificate ${i+1} validity","证书 ${i+1} 有效期状态",w.code(c.validityStatus))
                row("Valid from","有效期开始",date(c.validFromEpochMs));row("Valid until","有效期结束",date(c.validUntilEpochMs))
                row("Self-signed observation","自签名观察",w.yesNo(c.selfSigned))
            }
            if(p.certificate.presentedChain.size>16) row("Additional certificate details","其余证书详情",w.redacted)
        }
        row("Source","来源",if(source.savedId!=null) w.t("Saved schema-1 snapshot","已保存的 Schema 1 快照") else w.t("Completed live result","已完成的实时结果"))
        row("Exported at (UTC)","实际导出时间（UTC）",date(exportedAt))
        row("Redaction policy","脱敏策略","1")
        val title=when(source) {
            is WebExportSource.Tls -> {
                val r=source.result
                row("Observed completion (UTC)","原始完成时间（UTC）",date(source.observedAt))
                row("Target","目标",publicHost(r.normalizedTarget ?: r.probeResult?.normalizedServerName,w.redacted))
                row("Port","端口",r.port.takeIf {it in 1..65535}?.toString())
                row("Outcome","检测结果",w.code(r.analysis.outcome));row("Total duration","总耗时",number(r.totalDurationMs))
                row("Failure category","失败原因类别",r.failureReason?.let(w::code))
                tls(r.probeResult)
                head("Recorded findings","已记录的诊断项")
                r.analysis.findings.take(64).forEach {lines+=WebExportLine(w.code(it))}
                head("Recommendations","建议")
                r.analysis.recommendations.take(32).forEach {lines+=WebExportLine(w.code(it))}
                w.t("LinkBeacon SSL/TLS report","LinkBeacon SSL/TLS 检测报告")
            }
            is WebExportSource.Website -> {
                val s=source.snapshot
                row("Observed start (UTC)","原始开始时间（UTC）",date(s.startedAtEpochMs))
                row("Observed completion (UTC)","原始完成时间（UTC）",date(s.endedAtEpochMs))
                row("Target","目标",s.normalizedTarget?.let {safeUrl(it.executionUrl,w.redacted)} ?: w.missing)
                row("Port","端口",s.normalizedTarget?.port?.toString())
                row("Outcome","检测结果",w.code(s.outcome));row("Total duration","总耗时",number(s.totalDurationMs))
                row("Target failure category","目标失败类别",s.targetFailure?.let(w::code))
                row("Session failure category","会话失败类别",s.sessionFailure?.let(w::code))
                row("VPN","VPN",s.networkContext.vpnActive?.let(w::yesNo))
                row("Local network / DNS / proxy identifiers","本地网络 / DNS / 代理标识",w.redacted)
                s.hops.take(16).forEachIndexed {i,h->
                    head("Request ${i+1}","请求 ${i+1}")
                    row("Target","目标",safeUrl(h.target.executionUrl,w.redacted))
                    row("Port","端口",h.target.port.takeIf {it in 1..65535}?.toString())
                    row("HTTP planned transport","HTTP 计划路径",w.code(h.plannedHttpTransport))
                    row("Request duration","请求耗时",number(h.durationMs))
                    row("DNS outcome","DNS 结果",w.code(h.dns.status));row("DNS duration","DNS 耗时",number(h.dns.durationMs))
                    row("DNS failure category","DNS 失败类别",h.dns.failureReason?.let(w::code))
                    row("DNS candidate addresses","DNS 候选地址",if(h.dns.candidateAddresses.isEmpty()) w.missing else w.redacted)
                    row("Special/Fake-IP observation","特殊 / Fake-IP 观察",w.yesNo(h.dns.fakeIpDetected))
                    row("TCP stage","TCP 阶段",w.code(h.tcp.status));row("TCP duration","TCP 耗时",number(h.tcp.durationMs))
                    h.tcp.attempts.take(8).forEach {a->row("TCP attempt","TCP 尝试",w.code(a.outcome)+"; "+(number(a.durationMs)?:w.missing))}
                    row("TLS stage","TLS 阶段",w.code(h.tls.status));row("TLS stage duration","TLS 阶段耗时",number(h.tls.durationMs))
                    tls(h.tls.result)
                    head("HTTP evidence","HTTP 证据")
                    val http=h.http
                    row("HTTP response","HTTP 响应",when {
                        http?.responded==true -> w.t("Response received","已收到响应")
                        h.httpFailure!=null || http?.failureReason!=null -> w.t("Failed","检测失败")
                        http==null -> w.unmeasured
                        else -> w.missing
                    })
                    row("HTTP actual transport","HTTP 实际路径",http?.transportPath?.let(w::code))
                    row("HTTP status","HTTP 状态码",http?.statusCode?.takeIf {it in 100..599}?.toString())
                    row("HTTP protocol","HTTP 协议",http?.protocol?.let(w::code))
                    row("HTTP duration","HTTP 耗时",number(http?.durationMs))
                    row("HTTP failure category","HTTP 失败类别",(h.httpFailure ?: http?.failureReason)?.let(w::code))
                    row("Redirect failure category","重定向失败类别",h.redirectFailure?.let(w::code))
                    row("Location","Location",http?.responseHeaders?.location?.let {safeUrl(it,w.redacted)})
                    row("Content-Type","Content-Type",safeToken(http?.responseHeaders?.contentType
                        ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT),contentTypes,w.redacted))
                }
                if(s.hops.size>16) row("Further requests","其余请求",w.redacted)
                head("Recorded findings","已记录的诊断项")
                s.findings.take(64).forEach {lines+=WebExportLine(w.code(it.severity)+": "+w.code(it.code))}
                // Finding arguments may contain URL paths, server text or local identities: never dump them.
                row("Finding free text / headers / certificate identities","诊断自由文本 / 原始响应头 / 证书身份字段",w.redacted)
                head("Recommendations","建议")
                s.recommendations.take(32).forEach {lines+=WebExportLine(w.code(it.code))}
                w.t("LinkBeacon Website Diagnostics report","LinkBeacon 网站诊断报告")
            }
        }
        head("Privacy","隐私")
        lines+=WebExportLine(w.t("Local export with redaction. Public target names and observed results can still identify a service. Review before sharing.",
            "本地导出已脱敏。公开目标名称与检测结果仍可能标识某项服务，请检查后再分享。"))
        return WebExportDocument(title,lines.toList(),chinese)
    }
    private fun safeToken(v:String?,allowed:Set<String>,redacted:String)=v?.let {if(it in allowed) it else redacted}
    private val contentTypes=setOf("text/html","text/plain","text/css","text/xml","application/json",
        "application/xml","application/xhtml+xml","application/pdf","application/octet-stream",
        "image/png","image/jpeg","image/gif","image/webp","image/svg+xml")
    private val protocols=setOf("TLSv1.3","TLSv1.2","TLSv1.1","TLSv1","SSLv3")
    private val ciphers=setOf("TLS_AES_128_GCM_SHA256","TLS_AES_256_GCM_SHA384","TLS_CHACHA20_POLY1305_SHA256")+
        listOf("ECDHE_RSA","ECDHE_ECDSA","DHE_RSA","RSA").flatMap {k->
            listOf("AES_128_GCM_SHA256","AES_256_GCM_SHA384","AES_128_CBC_SHA","AES_256_CBC_SHA","CHACHA20_POLY1305_SHA256").map {"TLS_${k}_WITH_$it"}
        }
    /** All numeric connect/DNS addresses are omitted, including public ones, in policy v1. */
    internal fun publicHost(value:String?,redacted:String):String? {
        if(value==null) return null
        if(value.length>253 || value.any {it.isWhitespace() || it in "/:@%?#[]\\"}) return redacted
        val host=runCatching {IDN.toASCII(value.trimEnd('.'),IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)}.getOrNull() ?: return redacted
        val labels=host.split('.')
        val local=setOf("local","localdomain","localhost","lan","internal","home","corp","test","example","invalid","arpa","onion")
        if(labels.size<2 || labels.last() in local || labels.last().all(Char::isDigit) ||
            labels.any {it.isEmpty() || it.length>63 || it.startsWith('-') || it.endsWith('-')} ||
            !labels.last().matches(Regex("[a-z]{2,63}|xn--[a-z0-9-]+"))) return redacted
        return host
    }
    internal fun safeUrl(value:String,redacted:String):String = runCatching {
        if(value.length>8192 || value.any {it.code<32 || it=='\\'}) return redacted
        val u=URI(value)
        if(u.scheme !in setOf("http","https") || u.isOpaque) return redacted
        val host=publicHost(u.host,redacted) ?: return redacted
        if(host==redacted || u.port !in -1..65535 || u.port==0) return redacted
        val authority=host+(if(u.port!=-1) ":${u.port}" else "")
        val extra=u.rawUserInfo!=null || !u.rawPath.isNullOrEmpty() && u.rawPath!="/" || u.rawQuery!=null || u.rawFragment!=null
        "${u.scheme}://$authority/"+(if(extra) " [$redacted]" else "")
    }.getOrDefault(redacted)
}

internal class Words(val zh:Boolean) {
    fun t(en:String,cn:String)=if(zh) cn else en
    val missing get()=t("Not recorded","字段缺失")
    val unmeasured get()=t("Not measured","未检测")
    val redacted get()=t("Redacted","已脱敏")
    fun yesNo(v:Boolean)=if(v) t("Yes","是") else t("No","否")
    fun code(v:Enum<*>)=when(v.name) {
        "HTTP_1_0" -> "HTTP/1.0"
        "HTTP_1_1" -> "HTTP/1.1"
        "HTTP_2" -> "HTTP/2"
        else -> if(zh) chinese[v.name] ?: v.name.replace('_',' ') else v.name.lowercase(Locale.ROOT).replace('_',' ')
    }
    private val chinese=mapOf(
        "HEALTHY" to "正常","ATTENTION" to "需注意","FAILED" to "失败","FAIL" to "失败","PASS" to "通过",
        "SUCCESS" to "成功","UNKNOWN" to "未确定","NOT_STARTED" to "未检测","NOT_EVALUATED" to "未评估",
        "NOT_APPLICABLE" to "不适用","SKIPPED" to "未执行","STOPPED" to "已停止","NETWORK_CHANGED" to "网络已变化",
        "SYSTEM_TRUSTED" to "系统信任","UNTRUSTED" to "不受信任","MATCH" to "匹配","MISMATCH" to "不匹配",
        "VALID" to "有效","EXPIRED" to "已过期","NOT_YET_VALID" to "尚未生效","DIRECT" to "直接连接",
        "HTTP_PROXY" to "HTTP 代理","PAC" to "PAC 路径","UNKNOWN_PROXY" to "未确定的代理路径",
        "CONNECTED" to "连接成功","REFUSED" to "连接被拒绝","TIMEOUT" to "超时","UNREACHABLE" to "不可达",
        "NO_ROUTE" to "无可用路由","CANCELLED" to "已取消","TCP_CONNECTED" to "TCP 连接成功",
        "TCP_REFUSED" to "TCP 连接被拒绝","TCP_TIMEOUT" to "TCP 超时","TCP_UNREACHABLE" to "TCP 不可达",
        "TCP_NO_ROUTE" to "TCP 无路由","TCP_ERROR" to "TCP 错误","TLS_CONNECTED" to "TLS 握手成功",
        "TLS_HANDSHAKE_SUCCEEDED" to "TLS 握手成功","TLS_HANDSHAKE_FAILED" to "TLS 握手失败",
        "TLS_TIMEOUT" to "TLS 超时","TLS_PROTOCOL_ERROR" to "TLS 协议错误","TLS_FAILURE" to "TLS 失败",
        "TRUST_FAILED" to "信任校验失败","TLS_TRUST_FAILED" to "TLS 信任校验失败","HOSTNAME_MISMATCH" to "域名不匹配",
        "TLS_HOSTNAME_MISMATCH" to "TLS 域名不匹配","TRUSTED" to "受信任","HOSTNAME_MATCH" to "域名匹配",
        "SELF_SIGNED" to "自签名","EXPIRING_SOON" to "即将过期","NO_CERTIFICATE" to "未取得证书",
        "CERTIFICATE_EXPIRED" to "证书已过期","CERTIFICATE_NOT_YET_VALID" to "证书尚未生效",
        "VPN_ACTIVE" to "检测到 VPN","DNS_NXDOMAIN" to "域名不存在","NXDOMAIN" to "域名不存在",
        "DNS_NO_RECORDS" to "DNS 无记录","NO_RECORDS" to "无记录","DNS_TIMEOUT" to "DNS 超时",
        "DNS_FAILED" to "DNS 失败","DNS_RESOLUTION_SUCCEEDED" to "DNS 解析成功","FAKE_IP_DETECTED" to "检测到特殊地址",
        "NETWORK_ERROR" to "网络错误","INVALID_RESPONSE" to "响应无效","UNEXPECTED_ERROR" to "未预期错误",
        "NO_USABLE_ADDRESS" to "没有可用地址","INVALID_QUERY" to "查询无效","INVALID_TARGET" to "目标无效",
        "INVALID_PORT" to "端口无效","INVALID_URL" to "URL 无效","INVALID_HOST" to "主机无效",
        "UNSUPPORTED_SCHEME" to "不支持的 URL 协议","USER_INFO_NOT_SUPPORTED" to "不支持 URL 用户凭据",
        "UNSUPPORTED_SCOPE" to "不支持的地址作用域","UNSUPPORTED_IPV6_SCOPE" to "不支持的 IPv6 作用域",
        "SESSION_TIMEOUT" to "会话超时","CONNECT_TIMEOUT" to "连接超时","HEADERS_TIMEOUT" to "响应头超时",
        "CONNECTION_FAILED" to "连接失败","PROXY_FAILURE" to "代理失败","CLEARTEXT_NOT_PERMITTED" to "不允许明文连接",
        "HTTP_RESPONDED" to "已收到 HTTP 响应","HTTP_AUTH_REQUIRED" to "HTTP 需要认证",
        "HTTP_PROXY_AUTH_REQUIRED" to "代理需要认证","HTTP_FORBIDDEN" to "HTTP 禁止访问","HTTP_NOT_FOUND" to "HTTP 资源不存在",
        "HTTP_RATE_LIMITED" to "HTTP 请求受限","HTTP_SERVER_ERROR" to "HTTP 服务器错误","HTTP_FAILED" to "HTTP 失败",
        "REDIRECTED" to "发生重定向","REDIRECT_LOOP" to "重定向循环","LOOP" to "重定向循环",
        "TOO_MANY_REDIRECTS" to "重定向过多","REDIRECT_INVALID" to "重定向无效","LOCATION_MISSING" to "缺少 Location",
        "INVALID_LOCATION" to "Location 无效","CLEARTEXT_USED" to "使用明文 HTTP","HTTPS_DOWNGRADE" to "HTTPS 降级",
        "PROXY_PATH_USED" to "使用代理路径","INFO" to "信息","NOTICE" to "提示","WARNING" to "警告","ERROR" to "错误",
        "CHECK_HOST_AND_PORT" to "检查主机和端口","CHECK_DOMAIN_SPELLING" to "检查域名拼写","CHECK_DNS_CONFIGURATION" to "检查 DNS 配置",
        "CHECK_SERVICE_PORT" to "检查服务端口","CHECK_FIREWALL_OR_PATH" to "检查防火墙和网络路径",
        "CHECK_PRIVATE_CA_OR_SELF_SIGNED" to "检查私有 CA 或自签名证书","CHECK_CERTIFICATE_SAN" to "检查证书域名",
        "RENEW_CERTIFICATE" to "更新证书","CHECK_DEVICE_AND_SERVER_TIME" to "检查设备和服务器时间",
        "RETRY_ON_STABLE_NETWORK" to "在稳定网络重试","CHECK_AUTHENTICATION" to "检查认证要求",
        "CHECK_ACCESS_POLICY" to "检查访问策略","CHECK_RESOURCE_PATH" to "检查资源路径","RETRY_AFTER_RATE_LIMIT" to "稍后重试",
        "CHECK_SERVER_OR_UPSTREAM" to "检查服务器或上游","CHECK_PROXY_CONFIGURATION" to "检查代理配置"
    )
}
