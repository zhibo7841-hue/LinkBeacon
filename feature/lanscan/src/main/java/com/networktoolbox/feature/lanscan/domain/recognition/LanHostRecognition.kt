package com.networktoolbox.feature.lanscan.domain.recognition

/** Transient protocol facts; deliberately excluded from identity aggregation and History. */
data class LanHostRecognition(
    val generation: Long,
    val networkFingerprint: String,
    val nbstatName: String? = null,
    val nbstatConflict: Boolean = false,
    val sshSoftware: String? = null,
    val observedAt: Long,
)
