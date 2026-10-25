package com.networktoolbox.nbstat

/** Additional single-IP allowlist layered on the Task161 pinned-prefix gate. */
class NbstatScope(val prefix: String, private val targets: Map<String,String>) {
    init {
        require(targets.keys == setOf("T05","T06","T09"))
        require(targets.values.toSet().size == 3)
        require(targets.values.all { ip -> ip in (1..254).map { "$prefix$it" } })
    }
    fun authorize(optIn: String?, id: String, ip: String, windowMs: Int, currentPrefix: String, vpn: Boolean) {
        require(optIn=="true" && !vpn && currentPrefix==prefix) { "policy" }
        require(targets[id]==ip && windowMs in setOf(500,1000)) { "single_ip_or_window" }
    }
}
