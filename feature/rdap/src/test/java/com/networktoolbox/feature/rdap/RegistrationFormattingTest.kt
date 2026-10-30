package com.networktoolbox.feature.rdap

import com.networktoolbox.core.network.rdap.RdapDnssec
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class RegistrationFormattingTest {
    @Test fun instantUsesLocaleAndTimezoneWhileDateOnlyDoesNotShift() {
        val instant="2026-03-18T23:10:35Z"
        val sh=ZoneId.of("Asia/Shanghai")
        val la=ZoneId.of("America/Los_Angeles")
        assertTrue(formatRegistrationDate(instant,Locale.SIMPLIFIED_CHINESE,sh).contains("19"))
        assertTrue(formatRegistrationDate(instant,Locale.US,la).contains("18"))
        assertNotEquals(formatRegistrationDate(instant,Locale.US,sh),formatRegistrationDate(instant,Locale.SIMPLIFIED_CHINESE,sh))
        assertEquals(formatRegistrationDate("2026-03-18",Locale.US,sh),formatRegistrationDate("2026-03-18",Locale.US,la))
    }
    @Test fun unzonedTimeIsNotAssignedAZoneAndUnknownValueIsNotInvented() {
        assertEquals(formatRegistrationDate("2026-03-18T23:10:00",Locale.US,ZoneId.of("UTC")),
            formatRegistrationDate("2026-03-18T23:10:00",Locale.US,ZoneId.of("Asia/Shanghai")))
        assertEquals("not a date",formatRegistrationDate("not a date",Locale.US,ZoneId.of("UTC")))
    }
    @Test fun localizedInstantDoesNotRepeatTimezoneSuffix() {
        val value = "2026-03-18T23:10:35Z"
        val result = formatRegistrationDate(value, Locale.US, ZoneId.of("Asia/Shanghai"))
        assertEquals(java.time.format.DateTimeFormatter.ofLocalizedDateTime(
            java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT).withLocale(Locale.US)
            .format(java.time.OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Shanghai"))),result)
        assertFalse(result.contains("CST"))
    }
    @Test fun statusCodesAreFiniteAndUnknownPreservesRaw() {
        assertEquals(R.string.registration_status_active,registrationStatusLabel("active"))
        assertEquals(R.string.registration_status_client_transfer,registrationStatusLabel("clientTransferProhibited https://icann.org/epp"))
        assertEquals(R.string.registration_status_client_transfer,registrationStatusLabel("client transfer prohibited"))
        assertNull(registrationStatusLabel("unknown status"))
    }
    @Test fun dnssecUnknownIsNotUnsigned() {
        assertNotEquals(dnssecText(RdapDnssec.NOT_PROVIDED),dnssecText(RdapDnssec.UNSIGNED))
        assertNotEquals(dnssecText(RdapDnssec.SIGNED),dnssecText(RdapDnssec.UNSIGNED))
    }
    @Test fun sharedFieldsRequireExactAgreementAcrossEveryRecord() {
        fun record(org:String?,source:String?)=RegistrationRecord(source,listOfNotNull(org?.let {RegistrationField(R.string.rdap_org,it)}),emptyList())
        val a=record("Org A","whois.arin.net")
        assertEquals("Org A",commonRegistrationField(listOf(a,a),R.string.rdap_org))
        assertNull(commonRegistrationField(listOf(a,record(null,"whois.arin.net")),R.string.rdap_org))
        assertNull(commonRegistrationField(listOf(a,record("Org B","whois.arin.net")),R.string.rdap_org))
        assertNull(commonRegistrationSource(listOf(a,record("Org A","whois.ripe.net"))))
    }
}
