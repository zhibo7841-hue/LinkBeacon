package com.networktoolbox.core.common.diagnostic

data class DiagnosticCheck(
    val code: DiagnosticCheckCode,
    val stage: DiagnosticStage,
    val status: DiagnosticCheckStatus,
    val severity: DiagnosticSeverity,
    val summary: String,
    val target: DiagnosticTarget? = null,
    val method: String? = null,
    val observedAt: Long? = null,
    val networkFingerprint: NetworkFingerprint? = null,
    val evidenceObservationIds: List<String> = emptyList(),
    val summaryMessage: DiagnosticText? = null,
) {
    init {
        requireBoundedText(summary, "check summary", 512)
        method?.let { requireBoundedText(it, "check method", 128) }
        requireBoundedList(evidenceObservationIds.size, "check evidence", 32)
        evidenceObservationIds.forEach { id ->
            requireBoundedText(id, "evidence observation id", 128)
        }
    }
}
