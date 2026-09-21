package com.networktoolbox.core.network.tls

import java.math.BigInteger
import java.net.Socket
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsEvidenceAndTrustTest {
    @Test
    fun recordingTrustManagerDelegatesSuccessAndRecordsPresentedChain() {
        val delegate = SpyTrustManager()
        val wrapper = RecordingX509TrustManager(delegate)
        val chain = arrayOf(TlsTestFixtures.certificate)

        wrapper.checkServerTrusted(chain, "RSA")

        assertEquals(1, delegate.serverChecks.size)
        assertEquals(chain.toList(), wrapper.presentedChain)
        assertEquals(CertificateTrustStatus.SYSTEM_TRUSTED, wrapper.trustStatus)
    }

    @Test
    fun recordingTrustManagerPreservesDelegateFailureAndStillRecordsChain() {
        val expected = CertificateException("test-only rejection")
        val wrapper = RecordingX509TrustManager(SpyTrustManager(expected))
        val chain = arrayOf(TlsTestFixtures.certificate)

        val actual = runCatching { wrapper.checkServerTrusted(chain, "RSA") }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals(chain.toList(), wrapper.presentedChain)
        assertEquals(CertificateTrustStatus.UNTRUSTED, wrapper.trustStatus)
    }

    @Test
    fun recordingTrustManagerIsSafeAcrossConcurrentChecks() {
        val wrapper = RecordingX509TrustManager(SpyTrustManager())
        val chain = arrayOf(TlsTestFixtures.certificate)

        val threads = List(16) { Thread { wrapper.checkServerTrusted(chain, "RSA") } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(chain.toList(), wrapper.presentedChain)
        assertEquals(CertificateTrustStatus.SYSTEM_TRUSTED, wrapper.trustStatus)
    }

    @Test
    fun certificateEvidenceSeparatesValidExpiredAndFutureState() {
        val base = TlsTestFixtures.certificate
        val now = 2_000_000_000_000L
        val mapper = CertificateEvidenceMapper(FixedClock(now))

        val valid = mapper.map(listOf(DateOverrideCertificate(base, now - DAY, now + 10 * DAY))).leaf!!
        val expired = mapper.map(listOf(DateOverrideCertificate(base, now - 10 * DAY, now - DAY))).leaf!!
        val future = mapper.map(listOf(DateOverrideCertificate(base, now + DAY, now + 10 * DAY))).leaf!!

        assertEquals(CertificateValidityStatus.VALID, valid.validityStatus)
        assertEquals(10L, valid.remainingValidityDays)
        assertEquals(CertificateValidityStatus.EXPIRED, expired.validityStatus)
        assertEquals(null, expired.remainingValidityDays)
        assertEquals(CertificateValidityStatus.NOT_YET_VALID, future.validityStatus)
        assertEquals(null, future.remainingValidityDays)
    }

    @Test
    fun certificateEvidenceMapsSansChainAndReliableSelfSignedProof() {
        val evidence = CertificateEvidenceMapper(FixedClock(System.currentTimeMillis())).map(
            listOf(TlsTestFixtures.certificate),
        )

        assertEquals(1, evidence.presentedChainLength)
        assertTrue(evidence.leaf!!.dnsSubjectAlternativeNames.contains("localhost.test"))
        assertTrue(evidence.leaf!!.dnsSubjectAlternativeNames.contains("*.example.test"))
        assertTrue(evidence.leaf!!.ipSubjectAlternativeNames.contains("127.0.0.1"))
        assertTrue(evidence.leaf!!.selfSigned)
        val issues = evidence.issues(
            CertificateTrustStatus.UNTRUSTED,
            HostnameVerificationStatus.MISMATCH,
        )
        assertTrue(CertificateIssue.SELF_SIGNED in issues)
        assertTrue(CertificateIssue.UNTRUSTED_CHAIN in issues)
        assertTrue(CertificateIssue.HOSTNAME_MISMATCH in issues)
    }

    @Test
    fun sameSubjectAndIssuerWithoutValidSignatureIsNotCalledSelfSigned() {
        val certificate = DateOverrideCertificate(
            delegate = TlsTestFixtures.certificate,
            notBeforeMillis = TlsTestFixtures.certificate.notBefore.time,
            notAfterMillis = TlsTestFixtures.certificate.notAfter.time,
            failVerification = true,
        )

        val leaf = CertificateEvidenceMapper(FixedClock(System.currentTimeMillis())).map(listOf(certificate)).leaf!!

        assertFalse(leaf.selfSigned)
    }

    private class SpyTrustManager(
        private val failure: CertificateException? = null,
    ) : X509ExtendedTrustManager() {
        val serverChecks = ConcurrentLinkedQueue<List<X509Certificate>>()

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            serverChecks += chain.orEmpty().toList()
            failure?.let { throw it }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
            checkServerTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
            checkServerTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class FixedClock(private val now: Long) : TlsClock {
        override fun currentTimeMillis(): Long = now
        override fun nanoTime(): Long = 0L
    }

    private class DateOverrideCertificate(
        private val delegate: X509Certificate,
        private val notBeforeMillis: Long,
        private val notAfterMillis: Long,
        private val failVerification: Boolean = false,
    ) : X509Certificate() {
        override fun getNotBefore(): Date = Date(notBeforeMillis)
        override fun getNotAfter(): Date = Date(notAfterMillis)
        override fun verify(key: PublicKey?) {
            if (failVerification) throw CertificateException("test-only invalid self-signature")
            delegate.verify(key)
        }
        override fun verify(key: PublicKey?, sigProvider: String?) {
            if (failVerification) throw CertificateException("test-only invalid self-signature")
            delegate.verify(key, sigProvider)
        }
        override fun checkValidity() = delegate.checkValidity()
        override fun checkValidity(date: Date?) = delegate.checkValidity(date)
        override fun getVersion(): Int = delegate.version
        override fun getSerialNumber(): BigInteger = delegate.serialNumber
        override fun getIssuerDN(): Principal = delegate.issuerDN
        override fun getSubjectDN(): Principal = delegate.subjectDN
        override fun getTBSCertificate(): ByteArray = delegate.tbsCertificate
        override fun getSignature(): ByteArray = delegate.signature
        override fun getSigAlgName(): String = delegate.sigAlgName
        override fun getSigAlgOID(): String = delegate.sigAlgOID
        override fun getSigAlgParams(): ByteArray? = delegate.sigAlgParams
        override fun getIssuerUniqueID(): BooleanArray? = delegate.issuerUniqueID
        override fun getSubjectUniqueID(): BooleanArray? = delegate.subjectUniqueID
        override fun getKeyUsage(): BooleanArray? = delegate.keyUsage
        override fun getBasicConstraints(): Int = delegate.basicConstraints
        @Throws(CertificateEncodingException::class)
        override fun getEncoded(): ByteArray = delegate.encoded
        override fun toString(): String = delegate.toString()
        override fun getPublicKey(): PublicKey = delegate.publicKey
        override fun getCriticalExtensionOIDs(): Set<String>? = delegate.criticalExtensionOIDs
        override fun getNonCriticalExtensionOIDs(): Set<String>? = delegate.nonCriticalExtensionOIDs
        override fun getExtensionValue(oid: String?): ByteArray? = delegate.getExtensionValue(oid)
        override fun hasUnsupportedCriticalExtension(): Boolean = delegate.hasUnsupportedCriticalExtension()
        override fun getSubjectX500Principal(): X500Principal = delegate.subjectX500Principal
        override fun getIssuerX500Principal(): X500Principal = delegate.issuerX500Principal
        override fun getSubjectAlternativeNames(): Collection<List<*>>? = delegate.subjectAlternativeNames
        override fun getIssuerAlternativeNames(): Collection<List<*>>? = delegate.issuerAlternativeNames
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
