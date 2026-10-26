package com.networktoolbox.core.network.rdap

import org.junit.Assert.*
import org.junit.Test

class RdapParserLimitsTest {
    private fun invalid(s:String) {
        try {BoundedRdapJson.parse(s.toByteArray(),RdapLimits.BODY_BYTES);fail("Expected bounded rejection")}
        catch(e:RdapFailure) {assertEquals(RdapStatus.INVALID_RESPONSE,e.status)}
    }
    @Test fun stringLimitAppliesToKeysKnownAndUnknownFields() {
        val value="a".repeat(65_537)
        for(key in listOf("status","entities","notices","events","nameservers","unknown")) invalid("{\"$key\":\"$value\"}")
        invalid("{\"$value\":null}")
        assertEquals(65_536,BoundedRdapJson.parse("{\"ok\":\"${"a".repeat(65_536)}\"}".toByteArray(),RdapLimits.BODY_BYTES).str("ok")!!.length)
    }
    @Test fun allArraysAndUnknownContainersShareTheNodeAndDepthBudgets() {
        for(key in listOf("status","entities","notices","events","nameservers","unknown")) {
            invalid("{\"$key\":["+List(50_000) {"null"}.joinToString(",")+"]}")
            invalid("{\"$key\":"+"[".repeat(32)+"null"+"]".repeat(32)+"}")
        }
        BoundedRdapJson.parse(("{\"ok\":"+"[".repeat(30)+"null"+"]".repeat(30)+"}").toByteArray(),RdapLimits.BODY_BYTES)
    }
    @Test fun bodyAndBootstrapLimitsDoNotDependOnADeclaredLength() {
        try {BoundedRdapJson.parse(ByteArray(RdapLimits.BOOTSTRAP_BYTES+1),RdapLimits.BOOTSTRAP_BYTES);fail()}
        catch(e:RdapFailure) {assertEquals(RdapStatus.RESPONSE_TOO_LARGE,e.status)}
        try {BoundedRdapJson.parse(ByteArray(RdapLimits.BODY_BYTES+1),RdapLimits.BODY_BYTES);fail()}
        catch(e:RdapFailure) {assertEquals(RdapStatus.RESPONSE_TOO_LARGE,e.status)}
    }
    @Test fun presentationTruncationNeverSplitsAValidSurrogatePair() {
        val fn="a".repeat(511)+"😀"
        val normalizer=RdapDomainNormalizer {RdapDomainSyntax.ascii(it)}
        val json="""{"rdapConformance":["rdap_level_0"],"objectClassName":"domain","ldhName":"example.com","entities":[{"roles":["registrar"],"vcardArray":["vcard",[["fn",{},"text","$fn"]]]}]}"""
        val name=RdapResponseParser(normalizer).parse(json.toByteArray(),normalizer.normalize("example.com")!!).registrar!!
        assertFalse(name.last().isHighSurrogate());assertTrue(name.length<=512)
    }
}
