package com.networktoolbox.core.common.diagnostic

/**
 * Optional presentation metadata captured when a diagnostic branch is selected.
 * The code is stable across builds, unlike Android resource IDs. Fallback is
 * original snapshot text, not text to parse or re-analyze. Unknown codes survive
 * round trips and remain readable. No locale is part of diagnostic identity.
 */
data class DiagnosticText(
    val code: String,
    val fallbackText: String,
    val arguments: List<DiagnosticTextArgument> = emptyList(),
) {
    init {
        require(code.length <= 128)
        require(fallbackText.length <= 2_048)
        require(arguments.size <= 16)
    }

    companion object {
        fun legacy(text: String) = DiagnosticText("", text)
    }
}

/** Values retain their type; no extraction from natural-language text. */
sealed interface DiagnosticTextArgument {
    data class Text(val value: String) : DiagnosticTextArgument {
        init { require(value.length <= 2_048) }
    }
    data class Integer(val value: Long) : DiagnosticTextArgument
    data class Decimal(val value: Double) : DiagnosticTextArgument {
        init { require(value.isFinite()) }
    }
}
