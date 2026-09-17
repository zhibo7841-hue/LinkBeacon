package com.networktoolbox.core.common.diagnostic

data class DiagnosticFinding(
    val code: DiagnosticFindingCode,
    val title: String,
    val description: String,
    val severity: DiagnosticSeverity,
    val evidenceLevel: DiagnosticEvidenceLevel,
    val confidence: DiagnosticConfidence,
    val evidenceObservationIds: List<String> = emptyList(),
    val evidenceCheckCodes: List<DiagnosticCheckCode> = emptyList(),
    val possibleCauses: List<String> = emptyList(),
    val recommendedActionCodes: List<DiagnosticRecommendationCode> = emptyList(),
    val messages: Map<String, DiagnosticText> = emptyMap(),
) {
    init {
        requireBoundedText(title, "finding title", 256)
        requireBoundedText(description, "finding description", 1_024)
        requireBoundedList(evidenceObservationIds.size, "finding observation evidence", 32)
        requireBoundedList(evidenceCheckCodes.size, "finding check evidence", 16)
        requireBoundedList(possibleCauses.size, "finding possible causes", 8)
        requireBoundedList(recommendedActionCodes.size, "finding recommendations", 8)
        evidenceObservationIds.forEach { id ->
            requireBoundedText(id, "evidence observation id", 128)
        }
        possibleCauses.forEach { cause ->
            requireBoundedText(cause, "possible cause", 512)
        }
    }
}
