package com.networktoolbox.recognitionqa

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.feature.lanscan.domain.recognition.*

/** QA-only, session-scoped interpretation; never a SavedDevice.detectedDeviceType. */
enum class QaDeviceCategory {
    COMPUTER, SERVER, MOBILE, NETWORK_INFRASTRUCTURE, STORAGE, PRINTER, MEDIA, CAMERA, SMART_HOME, OTHER, UNKNOWN,
}
enum class QaCategoryCertainty { CONFIRMED, POSSIBLE, UNKNOWN }
enum class QaCategoryReason(val detail: String) {
    NBSTAT_WORKSTATION("NBSTAT workstation-name observation; computer clue only"),
    UPNP_GATEWAY("UPnP explicitly reports InternetGatewayDevice; logical gateway category only"),
    WSD_COMPUTER("WSD / device self-report: Computer; not authenticated hardware"),
    NAME_CONFLICT("Conflicting workstation names; no NBSTAT name/category candidate selected"),
    CONFLICT("Conflicting evidence; no automatic category selected"),
    INSUFFICIENT("No sufficient category evidence; services and names alone do not establish hardware type"),
    NOT_CURRENT("Evidence is not from the current eligible session"),
}
data class QaCategoryResult(
    val category: QaDeviceCategory = QaDeviceCategory.UNKNOWN,
    val certainty: QaCategoryCertainty = QaCategoryCertainty.UNKNOWN,
    val reasons: List<QaCategoryReason> = listOf(QaCategoryReason.INSUFFICIENT),
)

/**
 * Consumes the existing mapper's checked output, not raw TXT, names, inventory,
 * open ports or saved detected types. Confirmed means explicit self-report, not
 * authenticated physical hardware. There is deliberately no server/OS heuristic.
 */
object QaCategoryMapper {
    fun map(baseline: LanRecognitionResult, evidence: List<QaEvidence>): QaCategoryResult {
        if (baseline.status != LanRecognitionStatus.CURRENT) return QaCategoryResult(
            reasons = listOf(QaCategoryReason.NOT_CURRENT))
        val candidates = mutableListOf<QaCategoryResult>()
        val nbstat = evidence.filter { it.source == QaSource.NBSTAT && it.status == "NAME" }
        val names = nbstat.flatMap { it.names }.map { it.value.trim().lowercase(java.util.Locale.ROOT) }
            .filter(String::isNotEmpty).distinct()
        val nameConflict = names.size > 1 || evidence.any { it.source == QaSource.NBSTAT && it.status == "CONFLICT" }
        if (names.size == 1 && !nameConflict) candidates += QaCategoryResult(QaDeviceCategory.COMPUTER,
            QaCategoryCertainty.POSSIBLE, listOf(QaCategoryReason.NBSTAT_WORKSTATION))
        if (evidence.any { acceptedWsdProofs(it).isNotEmpty() }) {
            candidates += QaCategoryResult(QaDeviceCategory.COMPUTER, QaCategoryCertainty.CONFIRMED,
                listOf(QaCategoryReason.WSD_COMPUTER))
        }

        // Reuse only typed, current, explicit UPnP deviceType evidence. A service
        // such as MediaServer/WANIPConnection, model text or a port is not this declaration.
        if (baseline.hints.any { hint ->
            hint.code == LanRecognitionCode.DEVICE_TYPE_REPORTED &&
                hint.category == LanRecognitionCategory.DEVICE_REPORTED &&
                hint.dimension == LanRecognitionDimension.PROTOCOL_DEVICE_TYPE &&
                hint.evidence.isNotEmpty() && hint.evidence.all {
                    it.source == LanRecognitionSource.UPNP && it.field == LanRecognitionField.DEVICE_TYPE
                } && (hint.arguments as? LanRecognitionArguments.Text)?.value in setOf(
                    "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
                    "urn:schemas-upnp-org:device:InternetGatewayDevice:2")
        }) candidates += QaCategoryResult(QaDeviceCategory.NETWORK_INFRASTRUCTURE,
            QaCategoryCertainty.CONFIRMED, listOf(QaCategoryReason.UPNP_GATEWAY))

        val conflict = evidence.any { it.source == QaSource.WSD && it.status == "CATEGORY_CONFLICT" } ||
            baseline.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT } ||
            candidates.map { it.category }.distinct().size > 1
        val reasons = candidates.flatMap { it.reasons }.distinct() +
            if (nameConflict) listOf(QaCategoryReason.NAME_CONFLICT) else emptyList()
        if (conflict) return QaCategoryResult(reasons = reasons + QaCategoryReason.CONFLICT)
        return candidates.firstOrNull { it.certainty == QaCategoryCertainty.CONFIRMED }
            ?.let { it.copy(reasons = reasons) }
            ?: candidates.firstOrNull()?.let {
            it.copy(reasons = reasons)
        } ?: if (nameConflict) QaCategoryResult(reasons = reasons) else QaCategoryResult()
    }

    /** Failed metadata cannot create metadata proof; a valid Probe claim survives that failure. */
    private fun acceptedWsdProofs(e: QaEvidence): Set<WsdProof> {
        if (e.source != QaSource.WSD) return emptySet()
        return when (e.status) {
            "METADATA_ACCEPTED" -> e.wsdProofs
            "COMPUTER_SELF_REPORT", "XADDR_REJECTED", "INVALID_METADATA", "METADATA_TIMEOUT", "METADATA_REJECTED" ->
                e.wsdProofs.intersect(setOf(WsdProof.PROBE_COMPUTER_TYPE))
            else -> emptySet()
        }
    }
}

/** Manual display wins without mutating the automatic evidence or counting it as an auto result. */
object QaCategoryPresentation {
    fun label(result: QaCategoryResult, userType: DeviceType? = null, chinese: Boolean = false,
        userLabel: (DeviceType) -> String = { it.name }): String {
        if (userType != null) return userLabel(userType)
        if (result.certainty == QaCategoryCertainty.UNKNOWN || result.category == QaDeviceCategory.UNKNOWN)
            return if (chinese) "类型未知" else "Unknown type"
        val label = when (result.category) {
            QaDeviceCategory.COMPUTER -> if (chinese) "计算机" else "Computer"
            QaDeviceCategory.SERVER -> if (chinese) "服务器" else "Server"
            QaDeviceCategory.MOBILE -> if (chinese) "手机或平板" else "Phone or tablet"
            QaDeviceCategory.NETWORK_INFRASTRUCTURE -> if (chinese) "网络设备" else "Network infrastructure"
            QaDeviceCategory.STORAGE -> if (chinese) "存储设备" else "Storage device"
            QaDeviceCategory.PRINTER -> if (chinese) "打印机" else "Printer"
            QaDeviceCategory.MEDIA -> if (chinese) "媒体设备" else "Media device"
            QaDeviceCategory.CAMERA -> if (chinese) "摄像机" else "Camera"
            QaDeviceCategory.SMART_HOME -> if (chinese) "智能家居设备" else "Smart home device"
            QaDeviceCategory.OTHER -> if (chinese) "其他设备" else "Other device"
            QaDeviceCategory.UNKNOWN -> error("Handled above")
        }
        return when (result.certainty) {
            QaCategoryCertainty.CONFIRMED -> label
            QaCategoryCertainty.POSSIBLE -> if (chinese) "可能是$label" else "Possible ${label.lowercase(java.util.Locale.ROOT)}"
            QaCategoryCertainty.UNKNOWN -> error("Handled above")
        }
    }
    fun detail(result: QaCategoryResult, chinese: Boolean = false): String = when (result.certainty) {
        QaCategoryCertainty.CONFIRMED -> if (chinese) "类别明确：设备自报，不代表身份或安全认证" else "Category: explicit self-report (not authenticated)"
        QaCategoryCertainty.POSSIBLE -> if (chinese) "可能类别：现有线索不足以确认" else "Category: possible; insufficient to confirm"
        QaCategoryCertainty.UNKNOWN -> if (chinese) "类型未知" else "Category: unknown"
    }
}
