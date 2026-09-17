package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotDeserializer

/** Public read-only bridge; History and Home need no dependency on an analyzer. */
object DiagnosticHistoryLocalization {
    fun text(record: HistoryRecord, localization: ReportLocalizationContext): Pair<String, String?>? {
        val restored = AutomaticDiagnosticHistorySnapshotDeserializer.fromHistoryRecord(record) ?: return null
        val report = DiagnosticPresentationMapper.forHistory(restored, localization)
        val vocabulary = ReportVocabulary(localization)
        val metadata = DiagnosticPresentationMapper.stageSummariesForPresentation(report.checks, localization)
            .filter { it.stage in setOf(DiagnosticStage.GATEWAY, DiagnosticStage.INTERNET, DiagnosticStage.DNS) }
            .joinToString(" · ") { "${vocabulary.stage(it.stage)} ${vocabulary.check(it.status, it.severity)}" }
            .takeIf(String::isNotBlank)
        return report.summary to metadata
    }
}
