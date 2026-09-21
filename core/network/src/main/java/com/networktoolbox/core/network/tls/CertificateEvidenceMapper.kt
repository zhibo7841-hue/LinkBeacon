package com.networktoolbox.core.network.tls

import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class CertificateEvidenceMapper(
    private val clock: TlsClock,
) {
    fun map(chain: List<X509Certificate>): CertificateEvidence {
        val presented = chain.map(::mapCertificate)
        return CertificateEvidence(
            leaf = presented.firstOrNull(),
            presentedChain = presented,
            presentedChainLength = presented.size,
        )
    }

    private fun mapCertificate(certificate: X509Certificate): PresentedCertificate {
        val now = clock.currentTimeMillis()
        val notBefore = certificate.notBefore.time
        val notAfter = certificate.notAfter.time
        val validity = when {
            now < notBefore -> CertificateValidityStatus.NOT_YET_VALID
            now > notAfter -> CertificateValidityStatus.EXPIRED
            else -> CertificateValidityStatus.VALID
        }
        val remainingDays = if (validity == CertificateValidityStatus.VALID) {
            TimeUnit.MILLISECONDS.toDays((notAfter - now).coerceAtLeast(0L))
        } else {
            null
        }
        val alternativeNames = runCatching { certificate.subjectAlternativeNames }.getOrNull().orEmpty()
        val dnsNames = mutableListOf<String>()
        val ipAddresses = mutableListOf<String>()
        alternativeNames.forEach { entry ->
            val type = entry.getOrNull(0) as? Int ?: return@forEach
            val value = entry.getOrNull(1) as? String ?: return@forEach
            when (type) {
                DNS_SAN_TYPE -> dnsNames += value
                IP_SAN_TYPE -> ipAddresses += value
            }
        }
        return PresentedCertificate(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            dnsSubjectAlternativeNames = dnsNames.distinct(),
            ipSubjectAlternativeNames = ipAddresses.distinct(),
            validFromEpochMs = notBefore,
            validUntilEpochMs = notAfter,
            validityStatus = validity,
            remainingValidityDays = remainingDays,
            selfSigned = certificate.isReliablySelfSigned(),
        )
    }

    private fun X509Certificate.isReliablySelfSigned(): Boolean {
        if (subjectX500Principal != issuerX500Principal) return false
        return runCatching {
            verify(publicKey)
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val DNS_SAN_TYPE = 2
        const val IP_SAN_TYPE = 7
    }
}

internal fun CertificateEvidence.issues(
    trustStatus: CertificateTrustStatus,
    hostnameStatus: HostnameVerificationStatus,
): Set<CertificateIssue> = buildSet {
    when (leaf?.validityStatus) {
        CertificateValidityStatus.EXPIRED -> add(CertificateIssue.EXPIRED)
        CertificateValidityStatus.NOT_YET_VALID -> add(CertificateIssue.NOT_YET_VALID)
        CertificateValidityStatus.VALID,
        CertificateValidityStatus.UNKNOWN,
        null,
        -> Unit
    }
    if (leaf?.selfSigned == true && trustStatus == CertificateTrustStatus.UNTRUSTED) {
        add(CertificateIssue.SELF_SIGNED)
    }
    if (trustStatus == CertificateTrustStatus.UNTRUSTED) {
        add(CertificateIssue.UNTRUSTED_CHAIN)
    }
    if (hostnameStatus == HostnameVerificationStatus.MISMATCH) {
        add(CertificateIssue.HOSTNAME_MISMATCH)
    }
}
