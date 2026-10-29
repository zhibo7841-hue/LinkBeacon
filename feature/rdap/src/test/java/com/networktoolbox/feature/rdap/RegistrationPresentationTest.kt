package com.networktoolbox.feature.rdap

import com.networktoolbox.core.network.rdap.*
import org.junit.Test
import org.junit.Assert.*

class RegistrationPresentationTest {
    private val domain=RdapDomain("example.com","example.com")
    private fun result()=RdapResult(RdapStatus.SUCCESS,domain,observedAt=1,durationMs=0,protocol=RegistrationProtocol.WHOIS)
    @Test fun whoisRawToParsedToVisibleAndCopyKeepsEveryNewline() {
        val raw="Domain Name: EXAMPLE.COM\r\nRegistrar: Example Registrar\r\nRegistry Expiry Date: 2030-01-01T00:00:00Z\r\nName Server: ns1.example.com\r\n"
        val d=WhoisDomainData.parse(raw,domain,"whois.verisign-grs.com")!!
        val v=registrationPresentation(result().copy(registration=d,whoisSteps=listOf(WhoisStep("whois.verisign-grs.com",domain.ascii,RdapStatus.SUCCESS,raw))))
        assertEquals(RegistrationContent.PARTIAL,v.content)
        assertEquals(listOf("Example Registrar","2030-01-01T00:00:00Z","ns1.example.com"),v.records.single().fields.map {it.value})
        assertEquals(raw,v.raw.single().text);assertTrue(v.allRaw().endsWith(raw))
    }
    @Test fun multiRecordPresentationPreservesIndependentProvenanceAndSummaryFlag() {
        val ip=RegistrationIp.parse("173.249.200.19")!!
        val raw="Parent (NET-173-249-192-0-1) 173.249.192.0 - 173.249.255.255\nChild (NET-173-249-200-0-1) 173.249.200.0 - 173.249.201.255"
        val records=WhoisIpData.records(raw,ip,"whois.arin.net")
        val v=registrationPresentation(result().copy(domain=null,whoisIpRecords=records))
        assertEquals(RegistrationContent.MULTIPLE,v.content);assertEquals(2,v.records.size)
        assertEquals("173.249.200.0 - 173.249.201.255",v.records[0].fields.first().value)
        assertTrue(v.records.all {it.summaryOnly && it.source=="whois.arin.net"})
    }
    @Test fun upstreamCannotBecomeMainRegionalRecord() {
        val record=WhoisIpData.record("inetnum: 8.0.0.0/8",RegistrationIp.parse("8.8.8.8")!!)!!
        val v=registrationPresentation(result().copy(whoisSteps=listOf(WhoisStep(WhoisIpData.IANA,"8.8.8.8",RdapStatus.SUCCESS,"raw",record,true))))
        assertEquals(RegistrationContent.UPSTREAM_ONLY,v.content);assertTrue(v.records.isEmpty())
    }
    @Test fun emptyResponseAndNoServiceDoNotPretendCompleteFields() {
        assertEquals(RegistrationContent.NONE,registrationPresentation(result().copy(status=RdapStatus.WHOIS_SERVICE_NOT_FOUND)).content)
        assertEquals(RegistrationContent.UNPARSED,registrationPresentation(result().copy(whoisText="Unknown valid data")).content)
    }
    @Test fun rdapDomainAndIpUseSameStructuredFieldPresentationWithoutRoleMixup() {
        val d=RdapRegistration("Registrar","2020-01-01",null,"2030-01-01",listOf("ok"),listOf("ns.example.com"),RdapDnssec.SIGNED,false,emptyList())
        val a=registrationPresentation(result().copy(protocol=RegistrationProtocol.RDAP,registration=d))
        assertEquals(RegistrationContent.MAIN_FIELDS,a.content);assertTrue(a.records[0].fields.any {it.label==R.string.rdap_expires})
        val b=registrationPresentation(result().copy(domain=null,protocol=RegistrationProtocol.RDAP,ipNetwork=RdapIpNetwork("8.8.8.0","8.8.8.255",null,null,"Org",null,emptyList(),emptyList())))
        assertTrue(b.records[0].fields.any {it.label==R.string.rdap_org});assertFalse(b.records[0].fields.any {it.label==R.string.rdap_registrar})
    }
}
