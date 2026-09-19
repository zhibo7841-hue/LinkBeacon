package com.networktoolbox.core.common.favorites

/** Validation and normalization for local, plain-text managed-device notes. */
object DeviceNotes {
    const val MAX_CODE_POINTS = 500

    fun normalize(value: String?): String? {
        val normalized = value
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        require(normalized.codePointCount(0, normalized.length) <= MAX_CODE_POINTS) {
            "Device notes must contain at most $MAX_CODE_POINTS Unicode code points."
        }

        var offset = 0
        while (offset < normalized.length) {
            val codePoint = normalized.codePointAt(offset)
            require(
                !Character.isISOControl(codePoint) ||
                    codePoint == '\n'.code ||
                    codePoint == '\t'.code,
            ) { "Device notes must be plain text without control characters." }
            offset += Character.charCount(codePoint)
        }
        return normalized
    }
}
