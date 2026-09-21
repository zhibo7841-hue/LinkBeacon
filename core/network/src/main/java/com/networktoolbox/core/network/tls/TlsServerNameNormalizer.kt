package com.networktoolbox.core.network.tls

import java.net.IDN
import java.net.Inet6Address
import java.util.Locale

internal sealed interface TlsServerNameNormalization {
    data class Valid(
        val value: String,
        val sendSni: Boolean,
    ) : TlsServerNameNormalization

    data class Invalid(val reason: TlsFailureReason) : TlsServerNameNormalization
}

internal object TlsServerNameNormalizer {
    fun normalize(request: TlsProbeRequest): TlsServerNameNormalization = when (request.serverNameType) {
        TlsServerNameType.DOMAIN -> normalizeDomain(request.serverName)
        TlsServerNameType.IPV4_LITERAL -> TlsServerNameNormalization.Valid(
            value = request.connectAddress.hostAddress.orEmpty(),
            sendSni = false,
        )

        TlsServerNameType.IPV6_LITERAL -> normalizeIpv6(request)
    }

    private fun normalizeDomain(value: String): TlsServerNameNormalization {
        val normalized = runCatching {
            IDN.toASCII(value.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
                .lowercase(Locale.ROOT)
        }.getOrNull()
        return if (normalized.isNullOrBlank() || normalized.length > MAX_DOMAIN_LENGTH) {
            TlsServerNameNormalization.Invalid(TlsFailureReason.INVALID_TARGET)
        } else {
            TlsServerNameNormalization.Valid(normalized, sendSni = true)
        }
    }

    private fun normalizeIpv6(request: TlsProbeRequest): TlsServerNameNormalization {
        val address = request.connectAddress as Inet6Address
        if (address.isLinkLocalAddress && address.scopeId == 0) {
            return TlsServerNameNormalization.Invalid(TlsFailureReason.UNSUPPORTED_SCOPE)
        }
        return TlsServerNameNormalization.Valid(
            value = address.hostAddress.orEmpty().substringBefore('%'),
            sendSni = false,
        )
    }

    private const val MAX_DOMAIN_LENGTH = 253
}
