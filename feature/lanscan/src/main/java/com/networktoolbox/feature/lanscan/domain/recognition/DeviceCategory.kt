package com.networktoolbox.feature.lanscan.domain.recognition

import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.feature.lanscan.domain.recognition.*

/** Session-scoped interpretation; never a persisted detectedDeviceType or identity. */
enum class DeviceCategory {
    COMPUTER, SERVER, MOBILE, NETWORK_INFRASTRUCTURE, STORAGE, PRINTER, MEDIA, CAMERA, SMART_HOME, OTHER, UNKNOWN,
}
enum class CategoryCertainty { CONFIRMED, POSSIBLE, UNKNOWN }
enum class CategoryReason(val detail: String) {
    NBSTAT_WORKSTATION("NBSTAT workstation-name observation; computer clue only"),
    UPNP_GATEWAY("UPnP explicitly reports InternetGatewayDevice; logical gateway category only"),
    WSD_COMPUTER("WSD / device self-report: Computer; not authenticated hardware"),
    NAME_CONFLICT("Conflicting workstation names; no NBSTAT name/category candidate selected"),
    CONFLICT("Conflicting evidence; no automatic category selected"),
    INSUFFICIENT("No sufficient category evidence; services and names alone do not establish hardware type"),
    NOT_CURRENT("Evidence is not from the current eligible session"),
}
data class CategoryResult(
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val certainty: CategoryCertainty = CategoryCertainty.UNKNOWN,
    val reasons: List<CategoryReason> = listOf(CategoryReason.INSUFFICIENT),
)

/**
 * Consumes the existing mapper's checked output, not raw TXT, names, inventory,
 * open ports or saved detected types. Confirmed means explicit self-report, not
 * authenticated physical hardware. There is deliberately no server/OS heuristic.
 */
object CategoryMapper {
    fun map(baseline: LanRecognitionResult, evidence: CategoryEvidence): CategoryResult {
        if (baseline.status != LanRecognitionStatus.CURRENT) return CategoryResult(
            reasons = listOf(CategoryReason.NOT_CURRENT))
        val candidates = mutableListOf<CategoryResult>()
        val names = evidence.names.map { it.trim().lowercase(java.util.Locale.ROOT) }.filter(String::isNotEmpty).distinct()
        val nameConflict = names.size > 1 || evidence.nameConflict
        if (names.size == 1 && !nameConflict) candidates += CategoryResult(DeviceCategory.COMPUTER,
            CategoryCertainty.POSSIBLE, listOf(CategoryReason.NBSTAT_WORKSTATION))
        if (evidence.computerSelfReport) {
            candidates += CategoryResult(DeviceCategory.COMPUTER, CategoryCertainty.CONFIRMED,
                listOf(CategoryReason.WSD_COMPUTER))
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
        }) candidates += CategoryResult(DeviceCategory.NETWORK_INFRASTRUCTURE,
            CategoryCertainty.CONFIRMED, listOf(CategoryReason.UPNP_GATEWAY))

        val conflict = evidence.categoryConflict ||
            baseline.hints.any { it.code == LanRecognitionCode.PLATFORM_DECLARATIONS_CONFLICT } ||
            candidates.map { it.category }.distinct().size > 1
        val reasons = candidates.flatMap { it.reasons }.distinct() +
            if (nameConflict) listOf(CategoryReason.NAME_CONFLICT) else emptyList()
        if (conflict) return CategoryResult(reasons = reasons + CategoryReason.CONFLICT)
        return candidates.firstOrNull { it.certainty == CategoryCertainty.CONFIRMED }
            ?.let { it.copy(reasons = reasons) }
            ?: candidates.firstOrNull()?.let {
            it.copy(reasons = reasons)
        } ?: if (nameConflict) CategoryResult(reasons = reasons) else CategoryResult()
    }

}

data class CategoryEvidence(val names: List<String> = emptyList(), val nameConflict: Boolean = false,
    val computerSelfReport: Boolean = false, val categoryConflict: Boolean = false)

/** Manual display wins without mutating the automatic evidence or counting it as an auto result. */
object CategoryPresentation {
    fun label(result: CategoryResult, userType: DeviceType? = null, chinese: Boolean = false,
        userLabel: (DeviceType) -> String = { it.name }): String {
        if (userType != null) return userLabel(userType)
        if (result.certainty == CategoryCertainty.UNKNOWN || result.category == DeviceCategory.UNKNOWN)
            return if (chinese) "类型未知" else "Unknown type"
        val label = when (result.category) {
            DeviceCategory.COMPUTER -> if (chinese) "计算机" else "Computer"
            DeviceCategory.SERVER -> if (chinese) "服务器" else "Server"
            DeviceCategory.MOBILE -> if (chinese) "手机或平板" else "Phone or tablet"
            DeviceCategory.NETWORK_INFRASTRUCTURE -> if (chinese) "网络设备" else "Network infrastructure"
            DeviceCategory.STORAGE -> if (chinese) "存储设备" else "Storage device"
            DeviceCategory.PRINTER -> if (chinese) "打印机" else "Printer"
            DeviceCategory.MEDIA -> if (chinese) "媒体设备" else "Media device"
            DeviceCategory.CAMERA -> if (chinese) "摄像机" else "Camera"
            DeviceCategory.SMART_HOME -> if (chinese) "智能家居设备" else "Smart home device"
            DeviceCategory.OTHER -> if (chinese) "其他设备" else "Other device"
            DeviceCategory.UNKNOWN -> error("Handled above")
        }
        return when (result.certainty) {
            CategoryCertainty.CONFIRMED -> label
            CategoryCertainty.POSSIBLE -> if (chinese) "可能是$label" else "Possible ${label.lowercase(java.util.Locale.ROOT)}"
            CategoryCertainty.UNKNOWN -> error("Handled above")
        }
    }
    fun detail(result: CategoryResult, chinese: Boolean = false): String = when (result.certainty) {
        CategoryCertainty.CONFIRMED -> if (chinese) "类别明确：设备自报，不代表身份或安全认证" else "Category: explicit self-report (not authenticated)"
        CategoryCertainty.POSSIBLE -> if (chinese) "可能类别：现有线索不足以确认" else "Category: possible; insufficient to confirm"
        CategoryCertainty.UNKNOWN -> if (chinese) "类型未知" else "Category: unknown"
    }
}
