package com.networktoolbox

import org.junit.Assert.*
import org.junit.Test

/** Pure negative cases: no network adapter, database or Android network lookup is created. */
class LanAuditAuthorizationTest {
    private val prefix = "192.0.2." // Documentation-only fixture, never probed.
    private val valid = mapOf("allowRealLan" to "true", "authorizedPrefix" to prefix, "auditRun" to "guard-test")
    private fun rejected(packageName: String = "com.networktoolbox.wifiqa", pin: String? = prefix,
                         args: Map<String,String?> = valid, hosts: List<String>? = null) {
        var probeStarts = 0
        try {
            val plan = LanAuditAuthorization.validate(packageName,pin,args)
            if(hosts != null) plan.checkRange(hosts)
            probeStarts++
            fail("Rejected input reached probe boundary")
        } catch (_: IllegalArgumentException) { /* Expected fail-closed gate. */ }
        assertEquals(0,probeStarts)
    }
    @Test fun missingOptIn() = rejected(args=valid-"allowRealLan")
    @Test fun falseOptIn() = rejected(args=valid+("allowRealLan" to "false"))
    @Test fun missingPin() = rejected(pin=null)
    @Test fun missingRange() = rejected(args=valid-"authorizedPrefix")
    @Test fun callerCannotChooseDifferentNetwork() = rejected(args=valid+("authorizedPrefix" to "198.51.100."))
    @Test fun malformedPin() = rejected(pin="192.0.")
    @Test fun nonCanonicalPin() = rejected(pin="192.000.2.")
    @Test fun missingRun() = rejected(args=valid-"auditRun")
    @Test fun pathTraversalRun() = rejected(args=valid+("auditRun" to "../other"))
    @Test fun formalPackage() = rejected(packageName="com.networktoolbox")
    @Test fun suffixLookalikePackage() = rejected(packageName="other.wifiqa")
    @Test fun actualNetworkMismatch() = rejected(hosts=(1..254).map{"198.51.100.$it"})
    @Test fun extraHost() = rejected(hosts=(1..255).map{"$prefix$it"})
    @Test fun missingHost() = rejected(hosts=(1..253).map{"$prefix$it"})
    @Test fun authorizedPlanOnlyDoesNotSendPackets() {
        val plan=LanAuditAuthorization.validate("com.networktoolbox.wifiqa",prefix,valid)
        plan.checkRange((1..254).map{"$prefix$it"})
        plan.checkAddress("${prefix}1")
        assertThrows(IllegalArgumentException::class.java) { plan.checkAddress("198.51.100.1") }
    }
}
