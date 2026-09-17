package com.networktoolbox.core.common.diagnostic

data class DiagnosticRecommendation(
    val code: DiagnosticRecommendationCode,
    val priority: DiagnosticRecommendationPriority,
    val title: String,
    val action: String,
    val reason: String,
    val relatedFindingCodes: List<DiagnosticFindingCode> = emptyList(),
    val verificationHint: String? = null,
    val messages: Map<String, DiagnosticText> = emptyMap(),
) {
    init {
        requireBoundedText(title, "recommendation title", 256)
        requireBoundedText(action, "recommendation action", 512)
        requireBoundedText(reason, "recommendation reason", 512)
        verificationHint?.let { requireBoundedText(it, "verification hint", 512) }
        requireBoundedList(relatedFindingCodes.size, "related findings", 8)
    }
}
