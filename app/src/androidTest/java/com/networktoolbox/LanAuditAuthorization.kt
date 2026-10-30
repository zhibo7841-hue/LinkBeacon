package com.networktoolbox

/** QA-only fail-closed scope. Pinned prefix is supplied by an ignored, locally reviewed test asset. */
internal object LanAuditAuthorization {
    data class Plan(val prefix: String, val run: String) {
        fun checkAddress(ip: String) {
            require(ip in (1..254).map { "$prefix$it" }) { "Outside approved QA range" }
        }
        fun checkRange(hosts: List<String>) {
            require(hosts.toSet() == (1..254).map { "$prefix$it" }.toSet() && hosts.size == 254) {
                "Actual range differs from approved QA range"
            }
        }
    }

    fun validate(packageName: String, pinnedPrefix: String?, args: Map<String, String?>): Plan {
        require(packageName in setOf("com.networktoolbox.wifiqa", "com.networktoolbox.task160a")) { "QA package required" }
        require(args["allowRealLan"] == "true") { "Explicit opt-in required" }
        val pin = requireNotNull(pinnedPrefix) { "Local authorization asset required" }.trim()
        val octets = pin.removeSuffix(".").split('.')
        require(pin.endsWith('.') && octets.size == 3 && octets.all {
            val n = it.toIntOrNull()
            n != null && n in 0..255 && n.toString() == it
        }) { "Invalid pinned range" }
        require(args["authorizedPrefix"] == pin) { "Arguments cannot override the pinned range" }
        val run = requireNotNull(args["auditRun"])
        require(run.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,59}"))) { "Invalid QA run ID" }
        return Plan(pin, run)
    }
}
