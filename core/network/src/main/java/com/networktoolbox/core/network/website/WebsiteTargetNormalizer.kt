package com.networktoolbox.core.network.website

import java.net.IDN
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface WebsiteTargetNormalization {
    data class Valid(val target: NormalizedWebsiteTarget) : WebsiteTargetNormalization
    data class Invalid(val reason: WebsiteTargetFailureReason) : WebsiteTargetNormalization
}

object WebsiteTargetNormalizer {
    fun normalize(rawInput: String): WebsiteTargetNormalization {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank() || trimmed.any(Char::isWhitespace)) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_URL)
        }

        val explicitScheme = SCHEME_PREFIX.find(trimmed)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        if (explicitScheme != null && explicitScheme !in SUPPORTED_SCHEMES) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.UNSUPPORTED_SCHEME)
        }
        if (UNSUPPORTED_COLON_SCHEME.containsMatchIn(trimmed)) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.UNSUPPORTED_SCHEME)
        }

        val inferred = explicitScheme == null
        val candidate = if (inferred) "https://$trimmed" else trimmed
        val parsed = candidate.toHttpUrlOrNull()
            ?: return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_URL)
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || authorityContainsAt(candidate)) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.USER_INFO_NOT_SUPPORTED)
        }

        val scheme = when (parsed.scheme.lowercase(Locale.ROOT)) {
            WebsiteScheme.HTTP.value -> WebsiteScheme.HTTP
            WebsiteScheme.HTTPS.value -> WebsiteScheme.HTTPS
            else -> return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.UNSUPPORTED_SCHEME)
        }
        val originalHost = extractOriginalHost(candidate)
            ?: return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_HOST)
        val hostType = when {
            parsed.host.contains(':') -> WebsiteHostType.IPV6_LITERAL
            parsed.host.isIpv4Literal() -> WebsiteHostType.IPV4_LITERAL
            else -> WebsiteHostType.DOMAIN
        }
        val asciiHost = when (hostType) {
            WebsiteHostType.DOMAIN -> runCatching {
                IDN.toASCII(originalHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= MAX_DOMAIN_LENGTH }
                ?: return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_HOST)
            WebsiteHostType.IPV4_LITERAL,
            WebsiteHostType.IPV6_LITERAL,
            -> parsed.host
        }
        if (
            hostType == WebsiteHostType.IPV6_LITERAL &&
            parsed.host.lowercase(Locale.ROOT).startsWith("fe80:") &&
            !originalHost.contains('%')
        ) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.UNSUPPORTED_IPV6_SCOPE)
        }

        val executionUrl = parsed.newBuilder()
            .host(asciiHost)
            .fragment(null)
            .build()
            .toString()
        val redactedBase = parsed.newBuilder()
            .host(asciiHost)
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val redacted = if (parsed.encodedQuery != null) "$redactedBase?<redacted>" else redactedBase

        return WebsiteTargetNormalization.Valid(
            NormalizedWebsiteTarget(
                scheme = scheme,
                schemeWasInferred = inferred,
                originalHost = originalHost,
                asciiHost = asciiHost,
                hostType = hostType,
                port = parsed.port,
                encodedPath = parsed.encodedPath,
                encodedQuery = parsed.encodedQuery,
                fragment = parsed.fragment,
                executionUrl = executionUrl,
                displayUrlRedacted = redacted,
            ),
        )
    }

    fun resolveRedirect(
        current: NormalizedWebsiteTarget,
        location: String,
    ): WebsiteTargetNormalization {
        if (location.isBlank()) {
            return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_URL)
        }
        if (SCHEME_WITH_COLON.containsMatchIn(location)) {
            return normalize(location)
        }
        val resolved = current.executionUrl.toHttpUrlOrNull()?.resolve(location)
            ?: return WebsiteTargetNormalization.Invalid(WebsiteTargetFailureReason.INVALID_URL)
        return normalize(resolved.toString())
    }

    fun redactRawInput(rawInput: String): String = rawInput.trim()
        .substringBefore('#')
        .let { withoutFragment ->
            if ('?' in withoutFragment) "${withoutFragment.substringBefore('?')}?<redacted>" else withoutFragment
        }
        .replace(USER_INFO_REDACTION, "//<redacted>@")

    private fun authorityContainsAt(url: String): Boolean {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return '@' in authority
    }

    private fun extractOriginalHost(url: String): String? {
        val authority = url.substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (authority.isBlank() || '@' in authority) return null
        return if (authority.startsWith('[')) {
            authority.substringAfter('[').substringBefore(']').takeIf(String::isNotBlank)
        } else {
            val colonCount = authority.count { it == ':' }
            if (colonCount == 1) authority.substringBeforeLast(':') else authority
        }
    }

    private fun String.isIpv4Literal(): Boolean {
        val parts = split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotBlank() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
        }
    }

    private const val MAX_DOMAIN_LENGTH = 253
    private val SUPPORTED_SCHEMES = setOf("http", "https")
    private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
    private val SCHEME_WITH_COLON = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val UNSUPPORTED_COLON_SCHEME = Regex(
        "^(?:ftp|file|ws|wss|intent|javascript):",
        RegexOption.IGNORE_CASE,
    )
    private val USER_INFO_REDACTION = Regex("//[^/@]+@")
}
