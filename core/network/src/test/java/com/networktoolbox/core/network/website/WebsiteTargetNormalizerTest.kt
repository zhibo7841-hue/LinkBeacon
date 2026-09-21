package com.networktoolbox.core.network.website

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteTargetNormalizerTest {
    @Test fun `domain defaults to https`() {
        val target = valid("example.com")
        assertEquals(WebsiteScheme.HTTPS, target.scheme)
        assertTrue(target.schemeWasInferred)
        assertEquals(443, target.port)
    }

    @Test fun `explicit http is retained`() {
        val target = valid("http://example.com")
        assertEquals(WebsiteScheme.HTTP, target.scheme)
        assertFalse(target.schemeWasInferred)
        assertEquals(80, target.port)
    }

    @Test fun `custom port path query and fragment are parsed without sending fragment`() {
        val target = valid("https://example.com:8443/a/b?q=secret#section")
        assertEquals(8443, target.port)
        assertEquals("/a/b", target.encodedPath)
        assertEquals("q=secret", target.encodedQuery)
        assertEquals("section", target.fragment)
        assertTrue(target.executionUrl.contains("q=secret"))
        assertFalse(target.executionUrl.contains("section"))
        assertFalse(target.displayUrlRedacted.contains("secret"))
        assertTrue(target.displayUrlRedacted.contains("?<redacted>"))
    }

    @Test fun `domain with path is normalized`() {
        assertEquals("/path", valid("www.example.com/path").encodedPath)
    }

    @Test fun `ipv4 literal is supported`() {
        assertEquals(WebsiteHostType.IPV4_LITERAL, valid("http://192.168.1.1").hostType)
    }

    @Test fun `bracketed ipv6 literal is supported`() {
        val target = valid("https://[2001:db8::1]/path")
        assertEquals(WebsiteHostType.IPV6_LITERAL, target.hostType)
        assertEquals("2001:db8::1", target.asciiHost)
    }

    @Test fun `unscoped link local ipv6 is rejected`() {
        assertInvalid(
            "https://[fe80::1]/",
            WebsiteTargetFailureReason.UNSUPPORTED_IPV6_SCOPE,
        )
    }

    @Test fun `idn keeps original host and uses ascii network host`() {
        val target = valid("https://例子.测试/")
        assertEquals("例子.测试", target.originalHost)
        assertTrue(target.asciiHost.startsWith("xn--"))
    }

    @Test fun `unsupported schemes are rejected`() {
        listOf("ftp://example.com", "file:///tmp/a", "ws://example.com", "javascript:alert(1)")
            .forEach { assertInvalid(it, WebsiteTargetFailureReason.UNSUPPORTED_SCHEME) }
    }

    @Test fun `user info is rejected`() {
        assertInvalid(
            "https://user:password@example.com/",
            WebsiteTargetFailureReason.USER_INFO_NOT_SUPPORTED,
        )
    }

    @Test fun `invalid input is rejected`() {
        assertInvalid("not a host", WebsiteTargetFailureReason.INVALID_URL)
        assertInvalid("", WebsiteTargetFailureReason.INVALID_URL)
    }

    @Test fun `relative and absolute redirects use standard resolution`() {
        val current = valid("https://example.com/a/index.html")
        val relative = WebsiteTargetNormalizer.resolveRedirect(current, "../login") as WebsiteTargetNormalization.Valid
        val absolute = WebsiteTargetNormalizer.resolveRedirect(current, "http://other.example/x") as WebsiteTargetNormalization.Valid
        assertEquals("https://example.com/login", relative.target.executionUrl)
        assertEquals("other.example", absolute.target.asciiHost)
        assertEquals(WebsiteScheme.HTTP, absolute.target.scheme)
    }

    @Test fun `raw input redaction removes user info query and fragment`() {
        val redacted = WebsiteTargetNormalizer.redactRawInput(
            "https://user:password@example.com/login?token=secret#fragment",
        )
        assertFalse(redacted.contains("password"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("fragment"))
    }

    private fun valid(input: String): NormalizedWebsiteTarget =
        (WebsiteTargetNormalizer.normalize(input) as WebsiteTargetNormalization.Valid).target

    private fun assertInvalid(input: String, expected: WebsiteTargetFailureReason) {
        val result = WebsiteTargetNormalizer.normalize(input) as WebsiteTargetNormalization.Invalid
        assertEquals(expected, result.reason)
    }
}
