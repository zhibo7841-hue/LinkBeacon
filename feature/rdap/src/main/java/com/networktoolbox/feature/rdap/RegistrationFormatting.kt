package com.networktoolbox.feature.rdap

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Presentation only: never assigns a timezone to date-only or unzoned registry values. */
fun formatRegistrationDate(value: String, locale: Locale, zone: ZoneId): String {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    return runCatching {
        val local = OffsetDateTime.parse(value).atZoneSameInstant(zone)
        time.format(local)
    }.getOrNull()
        ?: runCatching { date.format(LocalDate.parse(value)) }.getOrNull()
        ?: runCatching { time.format(LocalDateTime.parse(value)) }.getOrNull()
        ?: value
}

fun isRegistrationDate(label: Int): Boolean = label in setOf(
    R.string.rdap_registered, R.string.rdap_changed, R.string.rdap_expires, R.string.rdap_database_updated,
)

/** Only recognized registry codes are translated; unknown codes and original text stay available. */
fun registrationStatusLabel(value: String): Int? {
    val code = value.trim().substringBefore(" https://").substringBefore(" http://")
        .lowercase(Locale.ROOT).replace(" ", "")
    return when (code) {
        "active", "ok" -> R.string.registration_status_active
        "inactive" -> R.string.registration_status_inactive
        "pending", "pendingcreate" -> R.string.registration_status_pending
        "pendingdelete" -> R.string.registration_status_pending_delete
        "pendingtransfer" -> R.string.registration_status_pending_transfer
        "clienttransferprohibited" -> R.string.registration_status_client_transfer
        "servertransferprohibited" -> R.string.registration_status_server_transfer
        "clienthold" -> R.string.registration_status_client_hold
        "serverhold" -> R.string.registration_status_server_hold
        "reassigned" -> R.string.registration_status_reassigned
        "directallocation" -> R.string.registration_status_allocation
        else -> null
    }
}

fun commonRegistrationField(records: List<RegistrationRecord>, label: Int): String? =
    records.takeIf { it.size > 1 }?.map { record ->
        record.fields.singleOrNull { it.label == label }?.value
    }?.distinct()?.singleOrNull()

fun commonRegistrationSource(records: List<RegistrationRecord>): String? =
    records.map { it.source }.distinct().singleOrNull()
