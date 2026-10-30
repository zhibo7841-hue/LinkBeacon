package com.networktoolbox.recognitionqa

import com.networktoolbox.feature.lanscan.domain.recognition.*
typealias QaDeviceCategory = DeviceCategory
typealias QaCategoryCertainty = CategoryCertainty
typealias QaCategoryReason = CategoryReason
typealias QaCategoryResult = CategoryResult
typealias QaCategoryPresentation = CategoryPresentation
object QaCategoryMapper {
    fun map(baseline: LanRecognitionResult, evidence: List<QaEvidence>): QaCategoryResult =
        CategoryMapper.map(baseline, CategoryEvidence(
            names = evidence.filter { it.source == QaSource.NBSTAT && it.status == "NAME" }.flatMap { it.names }.map { it.value },
            nameConflict = evidence.any { it.source == QaSource.NBSTAT && it.status == "CONFLICT" },
            computerSelfReport = evidence.any { acceptedWsdProofs(it).isNotEmpty() },
            categoryConflict = evidence.any { it.source == QaSource.WSD && it.status == "CATEGORY_CONFLICT" },
        ))
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
