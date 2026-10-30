package com.networktoolbox.feature.rdap

import com.networktoolbox.core.network.rdap.*

enum class RegistrationContent { MAIN_FIELDS, PARTIAL, UNPARSED, MULTIPLE, UPSTREAM_ONLY, NONE }
data class RegistrationField(val label:Int,val value:String)
data class RegistrationRecord(val source:String?,val fields:List<RegistrationField>,val more:List<RegistrationField>,val summaryOnly:Boolean=false)
data class RegistrationRaw(val source:String,val target:String,val text:String)
data class RegistrationPresentation(val content:RegistrationContent,val records:List<RegistrationRecord>,val raw:List<RegistrationRaw>) {
    // Keep every original body verbatim; added source separators are deliberately outside each body.
    fun allRaw():String=raw.joinToString("\n\n") {"[${it.source} · ${it.target}]\n${it.text}"}
}
fun registrationPresentation(r:RdapResult):RegistrationPresentation {
    fun fields(vararg values:Pair<Int,String?>)=values.mapNotNull {(label,v)->v?.takeIf {it.isNotBlank()}?.let {RegistrationField(label,it)}}
    fun dates(values:List<Pair<String,String>>)=values.map {(key,date)->RegistrationField(when(key) {
        "registration","regdate","created"->R.string.rdap_registered
        "expiration"->R.string.rdap_expires
        "last changed","updated","last-modified","changed"->R.string.rdap_changed
        else->R.string.rdap_database_updated
    },date)}.distinct()
    val source=r.source?.let {runCatching {java.net.URI(it).host}.getOrNull()}
    val records=mutableListOf<RegistrationRecord>()
    r.registration?.let {d->
        records.add(RegistrationRecord(source,fields(R.string.rdap_registrar to d.registrar,
            R.string.rdap_registered to d.registeredAt,R.string.rdap_expires to d.expiresAt,
            R.string.rdap_changed to d.changedAt,R.string.rdap_domain_status to d.statuses.takeIf {it.isNotEmpty()}?.joinToString("\n"),
            R.string.rdap_nameservers to d.nameservers.takeIf {it.isNotEmpty()}?.joinToString("\n")),
            fields(R.string.rdap_org to d.organization,R.string.rdap_database_updated to d.databaseUpdatedAt)))
    }
    r.ipNetwork?.let {d->records.add(RegistrationRecord(source,
        fields(R.string.rdap_org to d.organization,R.string.rdap_ip_range to "${d.start} – ${d.end}",
            R.string.rdap_network_name to d.name,R.string.rdap_handle to d.handle),
        fields(R.string.rdap_country to d.country,R.string.rdap_recorded_status to d.statuses.takeIf {it.isNotEmpty()}?.joinToString("\n"))+dates(d.events))) }
    val whois=r.whoisIpRecords.ifEmpty {listOfNotNull(r.whoisIpRecord)}
    whois.forEach {d->records.add(RegistrationRecord(d.source ?: source,
        fields(R.string.rdap_org to d.organization,R.string.rdap_ip_range to d.range,
            R.string.rdap_network_name to d.name,R.string.rdap_handle to d.handle,R.string.registration_summary_label to d.summaryLabel),
        fields(R.string.whois_cidr to d.cidr,R.string.rdap_country to d.country,
            R.string.rdap_recorded_status to d.statuses.takeIf {it.isNotEmpty()}?.joinToString("\n"))+dates(d.dates),d.summaryOnly))}
    val raw=if(r.protocol==RegistrationProtocol.WHOIS) r.whoisSteps.mapNotNull {s->s.text?.let {RegistrationRaw(s.server,s.target,it)}}
        .ifEmpty {listOfNotNull(r.whoisText?.let {RegistrationRaw(source.orEmpty(),r.input?.query.orEmpty(),it)})}
        else listOfNotNull(r.rawRdap?.let {RegistrationRaw(source.orEmpty(),r.input?.query ?: r.domain?.ascii.orEmpty(),it)})
    val content=when {
        records.size>1->RegistrationContent.MULTIPLE
        records.any {it.fields.isNotEmpty()} -> {
            val d=r.registration
            if(records.any {it.summaryOnly} || (d!=null && (d.registrar==null || d.registeredAt==null || d.expiresAt==null)))
                RegistrationContent.PARTIAL else RegistrationContent.MAIN_FIELDS
        }
        r.whoisSteps.any {it.ianaAllocation && it.record!=null}->RegistrationContent.UPSTREAM_ONLY
        raw.isNotEmpty()->RegistrationContent.UNPARSED
        else->RegistrationContent.NONE
    }
    return RegistrationPresentation(content,records.toList(),raw)
}
