package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.common.ipv4.IPv4Address
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.LanNetworkFingerprint
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import com.networktoolbox.feature.lanscan.domain.upnpNetworkIdentity
import java.security.MessageDigest
import java.util.Locale
import java.util.TreeMap

/** Pure interpretation of one device already present in the caller's current completed scan. */
object LanRecognitionMapper {
    const val MAX_HINTS = 32
    const val MAX_CONFLICT_VALUES = 4
    const val MAX_TEXT_LENGTH = 128
    const val MAX_HOSTNAME_LENGTH = 255
    private const val MAX_SERVICE_TYPE_LENGTH = 64
    private const val MAX_TXT_ATTRIBUTES = 16
    private const val MAX_TXT_KEY_LENGTH = 64
    private const val ESPHOME_SERVICE = "_esphomelib._tcp"
    private val serviceTypePattern = Regex("_[a-z0-9-]+\\._(?:tcp|udp)")
    private val espToken = Regex("(?<![\\p{L}\\p{N}\\p{M}])esp(?:32|8266)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE)
    private val negatedEspToken = Regex("(?<![\\p{L}\\p{N}\\p{M}])(?:not|no|non)[ ._-]+esp(?:32|8266)(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE)
    private val allowedTxt = setOf("platform", "board", "friendly_name", "version")

    /**
     * Device lookup happens inside [session]: callers cannot pass an unrelated saved profile.
     * mDNS/PTR have no per-record generation in the existing model; the upstream generation-
     * guarded session remains their provenance boundary. This function cannot recover discarded
     * records or authenticate self-reported values. It has no clock, IO or repository dependency.
     */
    fun map(
        session: LanScanSession,
        ipAddress: String,
        current: LanRecognitionContext,
    ): LanRecognitionResult {
        val network = session.initialNetworkContext
        if (current.sessionId <= 0L || session.sessionId != current.sessionId ||
            session.networkFingerprint != current.networkFingerprint ||
            session.networkFingerprint != LanNetworkFingerprint.from(network) ||
            session.status != LanScanStatus.COMPLETED || session.networkChanged ||
            network.activeNetworkAvailable != true || network.vpnActive != false ||
            network.connectionType !in setOf(ConnectionType.WIFI, ConnectionType.ETHERNET)
        ) return LanRecognitionResult(LanRecognitionStatus.NOT_CURRENT)

        val ip = IPv4Address.parse(ipAddress)?.toDottedDecimal()
            ?: return LanRecognitionResult(LanRecognitionStatus.DEVICE_UNAVAILABLE)
        val device = session.discoveredDevices.singleOrNull { it.ipAddress == ip }
            ?: return LanRecognitionResult(LanRecognitionStatus.DEVICE_UNAVAILABLE)
        val output = Output(listOf(session.sessionId.toString(), session.networkFingerprint, ip))
        if (device.hostNameSource == LanDeviceNameSource.REVERSE_DNS) {
            output.safeText(device.hostName, MAX_HOSTNAME_LENGTH)?.let { name ->
                output.nameClues(name, output.reference(LanRecognitionSource.SYSTEM_REVERSE_DNS,
                    LanRecognitionField.HOSTNAME, listOf(name), null))
            }
        }
        device.mdnsObservations.forEach { observation ->
            if (observation.source != LanDeviceNameSource.MDNS ||
                ip !in observation.ipv4Addresses || observation.observedAt < session.startedAt
            ) {
                output.limitations += LanRecognitionLimitation.OBSERVATION_NOT_CURRENT
            } else {
                output.mdns(observation)
            }
        }
        device.upnpObservations.forEach { observation ->
            if (observation.source != LanDeviceNameSource.UPNP ||
                observation.generation != session.sessionId ||
                observation.networkIdentity != network.upnpNetworkIdentity() ||
                observation.observedAt < session.startedAt
            ) {
                output.limitations += LanRecognitionLimitation.OBSERVATION_NOT_CURRENT
            } else {
                output.upnp(observation)
            }
        }
        return output.finish()
    }

    private class Output(scope: List<String>) {
        private val scopeKey = sha256(tuple(scope))
        val limitations = mutableSetOf<LanRecognitionLimitation>()
        // Keep a deterministic prefix rather than sorting or retaining unbounded output.
        private val hints = TreeMap<String, LanRecognitionHint>()
        private val platforms = TreeMap<String, Pair<String, LanRecognitionEvidence>>()

        fun reference(
            source: LanRecognitionSource,
            field: LanRecognitionField,
            permittedFields: List<String>,
            observedAt: Long?,
        ): LanRecognitionEvidence = scopedReference(scopeKey, source, field, permittedFields, observedAt)

        fun safeText(value: String?, maxLength: Int = MAX_TEXT_LENGTH): String? {
            if (value == null || value.isBlank()) return null
            if (value.length > maxLength || value.codePoints().anyMatch { point ->
                    Character.getType(point) in unsafeCharacterTypes
                }
            ) {
                limitations += LanRecognitionLimitation.INVALID_VALUE_IGNORED
                return null
            }
            return value.trim().takeIf(String::isNotEmpty)
        }

        fun mdns(observation: LanMdnsObservation) {
            val name = safeText(observation.serviceName) ?: return
            val type = safeText(observation.serviceType, MAX_SERVICE_TYPE_LENGTH)
                ?.lowercase(Locale.ROOT)?.removeSuffix(".") ?: return
            if (!serviceTypePattern.matches(type)) {
                limitations += LanRecognitionLimitation.INVALID_VALUE_IGNORED
                return
            }
            val port = observation.port?.takeIf { it in 1..65_535 }
            val hostname = safeText(observation.hostname, MAX_HOSTNAME_LENGTH)
            val txt = if (type == ESPHOME_SERVICE) permittedTxt(observation.txtAttributes) else emptyMap()
            val fields = listOf(name, type, port?.toString().orEmpty(), hostname.orEmpty()) +
                txt.toSortedMap().flatMap { listOf(it.key, it.value) }
            val ref = reference(LanRecognitionSource.MDNS, LanRecognitionField.SERVICE,
                fields, observation.observedAt)
            add(LanRecognitionHint(LanRecognitionCode.SERVICE_ADVERTISED,
                LanRecognitionCategory.OBSERVED_SERVICE, LanRecognitionDimension.SERVICE,
                LanRecognitionArguments.Service(type, name, port), listOf(ref)))
            nameClues(name, ref.copy(field = LanRecognitionField.INSTANCE_NAME))
            // The adapter's address-derived hostname is not an original SRV target.
            // Only the actual service instance name and explicit PTR source form name clues.
            txt["platform"]?.let { value ->
                val platformRef = ref.copy(field = LanRecognitionField.PLATFORM)
                add(LanRecognitionHint(LanRecognitionCode.PLATFORM_REPORTED,
                    LanRecognitionCategory.DEVICE_REPORTED, LanRecognitionDimension.PLATFORM,
                    LanRecognitionArguments.Platform(value, reportedPlatform(value)), listOf(platformRef)))
                val key = value.lowercase(Locale.ROOT)
                val candidate = value to platformRef
                val previous = platforms[key]
                if (previous == null || pairKey(candidate) < pairKey(previous)) platforms[key] = candidate
                if (platforms.size > MAX_CONFLICT_VALUES) {
                    platforms.pollLastEntry()
                    limitations += LanRecognitionLimitation.OUTPUT_LIMIT_REACHED
                }
            }
            listOf(
                Triple("board", LanRecognitionCode.BOARD_REPORTED, LanRecognitionDimension.BOARD),
                Triple("friendly_name", LanRecognitionCode.FRIENDLY_NAME_REPORTED, LanRecognitionDimension.NAME),
                Triple("version", LanRecognitionCode.VERSION_REPORTED, LanRecognitionDimension.FIRMWARE_VERSION),
            ).forEach { (key, code, dimension) ->
                txt[key]?.let { text(code, dimension, it, ref.copy(field = when (key) {
                    "board" -> LanRecognitionField.BOARD
                    "version" -> LanRecognitionField.VERSION
                    else -> LanRecognitionField.FRIENDLY_NAME
                })) }
            }
        }

        private fun permittedTxt(attributes: Map<String, String>): Map<String, String> {
            if (attributes.size > MAX_TXT_ATTRIBUTES) {
                limitations += LanRecognitionLimitation.INVALID_VALUE_IGNORED
                return emptyMap()
            }
            val grouped = attributes.entries.mapNotNull { (key, value) ->
                val safeKey = safeText(key, MAX_TXT_KEY_LENGTH) ?: return@mapNotNull null
                if (safeKey != key || key.any { it.code !in 0x21..0x7E || it == '=' }) {
                    limitations += LanRecognitionLimitation.INVALID_VALUE_IGNORED
                    null
                } else {
                    safeKey.lowercase(Locale.ROOT).takeIf { it in allowedTxt }?.let { it to value }
                }
            }.groupBy({ it.first }, { it.second })
            return grouped.mapNotNull { (key, values) ->
                if (values.size != 1) {
                    limitations += LanRecognitionLimitation.DUPLICATE_TXT_KEY
                    null
                } else {
                    safeText(values.single())?.let { key to it }
                }
            }.toMap()
        }

        fun upnp(observation: LanUpnpObservation) {
            val fields = listOf(
                Triple(observation.friendlyName, LanRecognitionCode.FRIENDLY_NAME_REPORTED, LanRecognitionDimension.NAME),
                Triple(observation.manufacturer, LanRecognitionCode.MANUFACTURER_REPORTED, LanRecognitionDimension.MANUFACTURER),
                Triple(observation.modelName, LanRecognitionCode.MODEL_NAME_REPORTED, LanRecognitionDimension.MODEL_NAME),
                Triple(observation.modelNumber, LanRecognitionCode.MODEL_NUMBER_REPORTED, LanRecognitionDimension.MODEL_NUMBER),
                Triple(observation.modelDescription, LanRecognitionCode.MODEL_DESCRIPTION_REPORTED, LanRecognitionDimension.MODEL_DESCRIPTION),
                Triple(observation.deviceType, LanRecognitionCode.DEVICE_TYPE_REPORTED, LanRecognitionDimension.PROTOCOL_DEVICE_TYPE),
            ).mapNotNull { (value, code, dimension) -> safeText(value)?.let { Triple(it, code, dimension) } }
            val ref = reference(LanRecognitionSource.UPNP, LanRecognitionField.SERVICE,
                fields.flatMap { listOf(it.second.name, it.first) }, observation.observedAt)
            fields.forEach { (value, code, dimension) ->
                text(code, dimension, value, ref.copy(field = when (code) {
                    LanRecognitionCode.FRIENDLY_NAME_REPORTED -> LanRecognitionField.FRIENDLY_NAME
                    LanRecognitionCode.MANUFACTURER_REPORTED -> LanRecognitionField.MANUFACTURER
                    LanRecognitionCode.MODEL_NAME_REPORTED -> LanRecognitionField.MODEL_NAME
                    LanRecognitionCode.MODEL_NUMBER_REPORTED -> LanRecognitionField.MODEL_NUMBER
                    LanRecognitionCode.MODEL_DESCRIPTION_REPORTED -> LanRecognitionField.MODEL_DESCRIPTION
                    else -> LanRecognitionField.DEVICE_TYPE
                }))
            }
        }

        fun nameClues(name: String, ref: LanRecognitionEvidence) {
            if (negatedEspToken.containsMatchIn(name)) return
            espToken.findAll(name).map { reportedPlatform(it.value) }
                .filter { it != LanReportedPlatform.OTHER }.distinct().forEach { platform ->
                add(LanRecognitionHint(LanRecognitionCode.ESP_NAME_CLUE,
                    LanRecognitionCategory.WEAK_NAME_CLUE, LanRecognitionDimension.PLATFORM,
                    LanRecognitionArguments.NameClue(name, platform), listOf(ref)))
            }
        }

        private fun text(code: LanRecognitionCode, dimension: LanRecognitionDimension,
            value: String, ref: LanRecognitionEvidence,
        ) = add(LanRecognitionHint(code, LanRecognitionCategory.DEVICE_REPORTED,
            dimension, LanRecognitionArguments.Text(value), listOf(ref)))

        private fun add(hint: LanRecognitionHint) {
            hints[hintKey(hint)] = hint
            if (hints.size > MAX_HINTS) {
                hints.pollLastEntry()
                limitations += LanRecognitionLimitation.OUTPUT_LIMIT_REACHED
            }
        }

        fun finish(): LanRecognitionResult {
            if (platforms.size > 1) {
                val observations = platforms.values.toList()
                add(LanRecognitionHint(LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT,
                    LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING, LanRecognitionDimension.PLATFORM,
                    LanRecognitionArguments.Conflict(observations.map { it.first }), observations.map { it.second }))
            }
            if (hints.isEmpty()) {
                add(LanRecognitionHint(LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE,
                    LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING, LanRecognitionDimension.AVAILABILITY,
                    LanRecognitionArguments.None, listOf(reference(LanRecognitionSource.SCAN_CONTEXT,
                        LanRecognitionField.SCAN, emptyList(), null))))
            }
            return LanRecognitionResult(LanRecognitionStatus.CURRENT, hints.values.toList(),
                limitations.sortedBy { it.name }.toSet())
        }
    }

    private fun reportedPlatform(value: String): LanReportedPlatform = when (value.lowercase(Locale.ROOT)) {
        "esp32" -> LanReportedPlatform.ESP32
        "esp8266" -> LanReportedPlatform.ESP8266
        else -> LanReportedPlatform.OTHER
    }

    private fun scopedReference(scopeKey: String, source: LanRecognitionSource, field: LanRecognitionField,
        permittedFields: List<String>, observedAt: Long?,
    ): LanRecognitionEvidence {
        val key = sha256(tuple(listOf(scopeKey, source.name, observedAt?.toString().orEmpty()) + permittedFields))
        return LanRecognitionEvidence(source, field, key, observedAt)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0F])
            }
        }
    }

    private fun pairKey(value: Pair<String, LanRecognitionEvidence>): String =
        tuple(listOf(value.first, evidenceKey(value.second)))

    private fun hintKey(hint: LanRecognitionHint): String {
        val args = when (val value = hint.arguments) {
            LanRecognitionArguments.None -> emptyList()
            is LanRecognitionArguments.Text -> listOf(value.value)
            is LanRecognitionArguments.Service -> listOf(value.type, value.name.orEmpty(), value.port?.toString().orEmpty())
            is LanRecognitionArguments.Platform -> listOf(value.value, value.platform.name)
            is LanRecognitionArguments.NameClue -> listOf(value.name, value.platform.name)
            is LanRecognitionArguments.Conflict -> value.values
        }
        return priority(hint).toString() +
            tuple(listOf(hint.code.name, hint.category.name, hint.dimension.name, tuple(args),
                tuple(hint.evidence.map(::evidenceKey))))
    }

    /** Output-budget precedence, not a confidence ranking of hardware identities. */
    private fun priority(hint: LanRecognitionHint): Int = when (hint.code) {
        LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT -> 0
        LanRecognitionCode.PLATFORM_REPORTED -> 1
        else -> when (hint.category) {
            LanRecognitionCategory.DEVICE_REPORTED -> 2
            LanRecognitionCategory.OBSERVED_SERVICE -> 3
            LanRecognitionCategory.WEAK_NAME_CLUE -> 4
            LanRecognitionCategory.INSUFFICIENT_OR_CONFLICTING -> 5
        }
    }

    private fun evidenceKey(value: LanRecognitionEvidence): String = tuple(listOf(
        value.source.name, value.field.name, value.observationKey, value.observedAt?.toString().orEmpty()))

    private fun tuple(values: List<String>): String = values.joinToString("") { "${it.length}:$it" }

    private val unsafeCharacterTypes = setOf(
        Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt(),
        Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt(),
    )
}
