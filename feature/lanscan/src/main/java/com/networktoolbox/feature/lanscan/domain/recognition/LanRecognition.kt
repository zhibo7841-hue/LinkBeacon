package com.networktoolbox.feature.lanscan.domain.recognition

/** Supplied by the caller from its current scan, never inferred from a device name. */
data class LanRecognitionContext(
    val sessionId: Long,
    val networkFingerprint: String,
)

enum class LanRecognitionStatus { CURRENT, NOT_CURRENT, DEVICE_UNAVAILABLE }

enum class LanRecognitionCategory {
    OBSERVED_SERVICE, DEVICE_REPORTED, WEAK_NAME_CLUE, INSUFFICIENT_OR_CONFLICTING,
}

/** Codes describe observations, not authenticated hardware or persistent device identity. */
enum class LanRecognitionCode {
    SERVICE_ADVERTISED,
    PLATFORM_REPORTED, BOARD_REPORTED, FRIENDLY_NAME_REPORTED, VERSION_REPORTED,
    MANUFACTURER_REPORTED, MODEL_NAME_REPORTED, MODEL_NUMBER_REPORTED,
    MODEL_DESCRIPTION_REPORTED, DEVICE_TYPE_REPORTED,
    ESP_NAME_CLUE, PLATFORM_DECLARATIONS_CONFLICT, NO_USABLE_RECOGNITION_EVIDENCE,
}

enum class LanRecognitionDimension {
    SERVICE, PLATFORM, BOARD, NAME, FIRMWARE_VERSION, MANUFACTURER,
    MODEL_NAME, MODEL_NUMBER, MODEL_DESCRIPTION, PROTOCOL_DEVICE_TYPE, AVAILABILITY,
}

enum class LanRecognitionSource { SYSTEM_REVERSE_DNS, MDNS, UPNP, SCAN_CONTEXT }

enum class LanRecognitionField {
    SERVICE, HOSTNAME, INSTANCE_NAME, PLATFORM, BOARD, FRIENDLY_NAME, VERSION,
    MANUFACTURER, MODEL_NAME, MODEL_NUMBER, MODEL_DESCRIPTION, DEVICE_TYPE, SCAN,
}

enum class LanReportedPlatform { ESP32, ESP8266, OTHER }

sealed interface LanRecognitionArguments {
    data object None : LanRecognitionArguments
    data class Text(val value: String) : LanRecognitionArguments
    data class Service(val type: String, val name: String?, val port: Int?) : LanRecognitionArguments
    data class Platform(val value: String, val platform: LanReportedPlatform) : LanRecognitionArguments
    data class NameClue(val name: String, val platform: LanReportedPlatform) : LanRecognitionArguments
    data class Conflict(val values: List<String>) : LanRecognitionArguments
}

/**
 * Opaque reference to permitted fields, scoped to the current network, generation and target.
 * It is not a persistent device key and must not be passed to the identity matcher.
 * No raw TXT map, MAC, URL, network fingerprint or saved-profile data is retained here.
 */
data class LanRecognitionEvidence(
    val source: LanRecognitionSource,
    val field: LanRecognitionField,
    val observationKey: String,
    val observedAt: Long?,
)

data class LanRecognitionHint(
    val code: LanRecognitionCode,
    val category: LanRecognitionCategory,
    val dimension: LanRecognitionDimension,
    val arguments: LanRecognitionArguments,
    val evidence: List<LanRecognitionEvidence>,
) {
    val sources: Set<LanRecognitionSource> get() = evidence.map { it.source }.toSet()

    /** A multi-observation conflict has no invented single observation time. */
    val observedAt: Long? get() = evidence.map { it.observedAt }.distinct().singleOrNull()
}

enum class LanRecognitionLimitation {
    INVALID_VALUE_IGNORED, DUPLICATE_TXT_KEY, OBSERVATION_NOT_CURRENT, OUTPUT_LIMIT_REACHED,
}

data class LanRecognitionResult(
    val status: LanRecognitionStatus,
    val hints: List<LanRecognitionHint> = emptyList(),
    val limitations: Set<LanRecognitionLimitation> = emptySet(),
)
