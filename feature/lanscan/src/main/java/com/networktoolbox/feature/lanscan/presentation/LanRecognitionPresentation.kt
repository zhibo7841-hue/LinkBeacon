package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.recognition.*

/** Transient projection only; the complete typed hint and evidence remain available to detail. */
data class LanRecognitionRow(val hint: LanRecognitionHint, val text: UiText)

data class LanRecognitionDevicePresentation(
    val result: LanRecognitionResult,
    val rows: List<LanRecognitionRow>,
    val summary: UiText?,
    val limitations: List<UiText>,
)

/** Scope check prevents a separately collected UI snapshot from decorating another scan. */
class LanRecognitionSnapshot(
    val sessionId: Long,
    val networkFingerprint: String,
    val devices: Map<String, LanRecognitionDevicePresentation>,
    private val capturedSession: LanScanSession,
) {
    fun forSession(session: LanScanSession): Map<String, LanRecognitionDevicePresentation> =
        if (session.status == LanScanStatus.COMPLETED) validated(session) else emptyMap()

    fun forState(state: LanScannerUiState): Map<String, LanRecognitionDevicePresentation> =
        state.recognitionSession()?.let(::validated).orEmpty()

    private fun validated(session: LanScanSession): Map<String, LanRecognitionDevicePresentation> {
        // Separately collected progress may lead the projection by one emission.
        // Counters/time do not change recognition evidence; keep every other
        // header field and each host observation guarded exactly as before.
        val comparable = if (session.status == LanScanStatus.SCANNING) session.copy(
            scannedHosts = capturedSession.scannedHosts, finishedAt = capturedSession.finishedAt,
        ) else session
        if (session.networkChanged ||
            session.sessionId != sessionId || session.networkFingerprint != networkFingerprint ||
            comparable.copy(discoveredDevices = capturedSession.discoveredDevices) != capturedSession
        ) return emptyMap()
        // UI state and recognition are collected separately. Even within one generation,
        // never attach a prior platform/conflict report to changed or ambiguous observations.
        val captured = capturedSession.discoveredDevices.associateBy { it.ipAddress }
        return session.discoveredDevices.groupBy { it.ipAddress }.mapNotNull { (ip, current) ->
            devices[ip]?.takeIf { current.size == 1 && current.single() == captured[ip] }?.let { ip to it }
        }.toMap()
    }
}

/** VM-owned, single-collector cache; no process-global or saved-profile recognition state. */
internal class LanRecognitionProjector(
    private val map: (LanScanSession, String, LanRecognitionContext) -> LanRecognitionResult = { session, ip, context ->
        if (session.status == LanScanStatus.SCANNING) LanRecognitionMapper.mapScanning(session, ip, context)
        else LanRecognitionMapper.map(session, ip, context)
    },
) {
    private data class Cached(val device: LanDevice, val presentation: LanRecognitionDevicePresentation?)
    private var header: LanScanSession? = null
    private var context: LanRecognitionContext? = null
    private var cache = emptyMap<String, Cached>()

    fun snapshot(state: LanScannerUiState, current: LanRecognitionContext?): LanRecognitionSnapshot? {
        val session = state.recognitionSession()
        if (session == null || current == null) {
            header = null
            context = null
            cache = emptyMap()
            return null
        }
        val nextHeader = session.copy(discoveredDevices = emptyList(),
            scannedHosts = 0, finishedAt = session.startedAt)
        val previous = if (header == nextHeader && context == current) cache else emptyMap()
        val next = session.discoveredDevices.groupBy { it.ipAddress }.mapNotNull { (ip, candidates) ->
            val device = candidates.singleOrNull() ?: return@mapNotNull null
            val entry = previous[ip]?.takeIf { it.device == device }
                ?: Cached(device, LanRecognitionPresentation.present(map(session, ip, current)))
            ip to entry
        }.toMap()
        header = nextHeader
        context = current
        cache = next
        return LanRecognitionSnapshot(current.sessionId, current.networkFingerprint,
            next.mapNotNull { (ip, entry) -> entry.presentation?.let { ip to it } }.toMap(), session)
    }
}

/** Resource binding, not a second recognizer. Only the reviewed mapper interprets observations. */
object LanRecognitionPresentation {
    fun snapshot(state: LanScannerUiState, current: LanRecognitionContext?): LanRecognitionSnapshot? =
        LanRecognitionProjector().snapshot(state, current)

    fun present(result: LanRecognitionResult): LanRecognitionDevicePresentation? {
        if (result.status != LanRecognitionStatus.CURRENT) return null
        val rows = result.hints.filter { it.code != LanRecognitionCode.NO_USABLE_RECOGNITION_EVIDENCE }
            .map { LanRecognitionRow(it, text(it)) }
        if (rows.isEmpty() && result.limitations.isEmpty()) return null
        val summary = rows.firstOrNull { it.hint.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT }
            ?: rows.firstOrNull { it.hint.code == LanRecognitionCode.PLATFORM_REPORTED }
            ?: rows.firstOrNull { it.hint.category == LanRecognitionCategory.DEVICE_REPORTED && it.hint.sources != setOf(LanRecognitionSource.NBSTAT) }
            ?: rows.firstOrNull { it.hint.category == LanRecognitionCategory.OBSERVED_SERVICE }
        val summaryText = summary?.let { row -> when (val args = row.hint.arguments) {
            is LanRecognitionArguments.Service -> UiText(R.string.lan_recognition_service_short, args.type,
                source(row.hint.evidence.first()))
            is LanRecognitionArguments.Conflict -> UiText(R.string.lan_recognition_conflict_short)
            else -> row.text
        } }
        return LanRecognitionDevicePresentation(result, rows, summaryText,
            result.limitations.map { limitation -> UiText(when (limitation) {
                LanRecognitionLimitation.OUTPUT_LIMIT_REACHED -> R.string.lan_recognition_truncated
                LanRecognitionLimitation.DUPLICATE_TXT_KEY -> R.string.lan_recognition_duplicate_txt
                LanRecognitionLimitation.INVALID_VALUE_IGNORED -> R.string.lan_recognition_invalid
                LanRecognitionLimitation.OBSERVATION_NOT_CURRENT -> R.string.lan_recognition_stale
            }) })
    }

    fun category(result: CategoryResult): UiText? = when {
        result.certainty == CategoryCertainty.POSSIBLE && result.category == DeviceCategory.COMPUTER -> UiText(R.string.lan_category_possible_computer)
        result.certainty == CategoryCertainty.CONFIRMED && result.category == DeviceCategory.NETWORK_INFRASTRUCTURE -> UiText(R.string.lan_category_network_reported)
        else -> null
    }

    fun source(evidence: LanRecognitionEvidence): UiText = when (evidence.source) {
        LanRecognitionSource.SYSTEM_REVERSE_DNS -> UiText(R.string.lan_recognition_ptr)
        LanRecognitionSource.MDNS -> UiText(if (evidence.field in setOf(LanRecognitionField.PLATFORM,
            LanRecognitionField.BOARD, LanRecognitionField.FRIENDLY_NAME, LanRecognitionField.VERSION))
            R.string.lan_recognition_mdns_txt else R.string.lan_recognition_mdns)
        LanRecognitionSource.UPNP -> UiText(R.string.lan_recognition_upnp)
        LanRecognitionSource.NBSTAT -> UiText(R.string.lan_recognition_nbstat)
        LanRecognitionSource.SSH -> UiText(R.string.lan_recognition_ssh)
        LanRecognitionSource.SCAN_CONTEXT -> UiText(R.string.lan_recognition_scan)
    }

    private fun text(hint: LanRecognitionHint): UiText {
        val source = hint.evidence.firstOrNull()?.let(::source) ?: UiText(R.string.lan_recognition_scan)
        return when (val arguments = hint.arguments) {
            is LanRecognitionArguments.Platform -> UiText(R.string.lan_recognition_platform, arguments.value, source)
            is LanRecognitionArguments.Conflict -> UiText(R.string.lan_recognition_conflict, arguments.values.joinToString(" / "))
            is LanRecognitionArguments.NameClue -> UiText(R.string.lan_recognition_name_clue,
                arguments.platform.name, arguments.name, source)
            is LanRecognitionArguments.Service -> if (hint.sources == setOf(LanRecognitionSource.UPNP)) {
                UiText(R.string.lan_recognition_service_short, arguments.type, source)
            } else UiText(R.string.lan_recognition_service, arguments.type,
                arguments.name ?: UiText(R.string.lan_recognition_not_recorded),
                arguments.port?.toString() ?: UiText(R.string.lan_recognition_not_recorded), source)
            is LanRecognitionArguments.Text -> UiText(R.string.lan_recognition_reported_field,
                UiText(when (hint.code) {
                    LanRecognitionCode.BOARD_REPORTED -> R.string.lan_recognition_board
                    LanRecognitionCode.FRIENDLY_NAME_REPORTED -> R.string.lan_recognition_name
                    LanRecognitionCode.VERSION_REPORTED -> R.string.lan_recognition_version
                    LanRecognitionCode.MANUFACTURER_REPORTED -> R.string.lan_recognition_manufacturer
                    LanRecognitionCode.MODEL_NAME_REPORTED -> R.string.lan_recognition_model_name
                    LanRecognitionCode.MODEL_NUMBER_REPORTED -> R.string.lan_recognition_model_number
                    LanRecognitionCode.MODEL_DESCRIPTION_REPORTED -> R.string.lan_recognition_model_description
                    LanRecognitionCode.DEVICE_TYPE_REPORTED -> R.string.lan_recognition_protocol_type
                    else -> R.string.lan_recognition_name
                }), arguments.value, source)
            LanRecognitionArguments.None -> UiText(R.string.lan_recognition_not_recorded)
        }
    }
}

/** A detached read-only view with its actual SCANNING status, never a persisted result. */
internal fun LanScannerUiState.recognitionSession(): LanScanSession? = when (this) {
    is LanScannerUiState.Completed -> session
    is LanScannerUiState.Scanning -> LanScanSession(
        status = LanScanStatus.SCANNING, initialNetworkContext = networkContext, range = range,
        scannedHosts = update.scannedHosts, totalHosts = update.totalHosts,
        discoveredDevices = update.discoveredDevices, startedAt = startedAt,
        finishedAt = startedAt + (update.elapsedMs ?: 0), sessionId = sessionId,
    )
    else -> null
}
