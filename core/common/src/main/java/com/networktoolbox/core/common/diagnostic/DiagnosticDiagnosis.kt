package com.networktoolbox.core.common.diagnostic

data class DiagnosticDiagnosis(
    val status: DiagnosticDiagnosisStatus,
    val title: String,
    val explanation: String,
    val primaryFindingCode: DiagnosticFindingCode? = null,
    val confidence: DiagnosticConfidence,
    val possibleCauses: List<String> = emptyList(),
    val messages: Map<String, DiagnosticText> = emptyMap(),
) {
    init {
        requireBoundedText(title, "diagnosis title", 256)
        requireBoundedText(explanation, "diagnosis explanation", 1_024)
        requireBoundedList(possibleCauses.size, "diagnosis possible causes", 8)
        possibleCauses.forEach { cause ->
            requireBoundedText(cause, "possible cause", 512)
        }
    }
}
