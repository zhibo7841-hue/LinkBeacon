package com.networktoolbox.core.common.favorites

/** Machine-readable evidence explaining a saved-device identity decision. */
enum class DeviceIdentityMatchReason {
    MATCH_MAC,
    MATCH_UPNP_UDN,
    MATCH_SCOPED_IPV4_WEAK,
    CONFLICT_MAC,
    CONFLICT_UPNP_UDN,
    CONFLICT_MULTIPLE_STRONG_EVIDENCE,
    AMBIGUOUS_STRONG_MATCH,
    AMBIGUOUS_WEAK_MATCH,
    NO_MATCH,
}

sealed interface DeviceIdentityMatchResult {
    val reason: DeviceIdentityMatchReason

    data class StrongMatch(
        val profile: SavedDeviceProfile,
        override val reason: DeviceIdentityMatchReason,
    ) : DeviceIdentityMatchResult

    data class WeakCompatibilityMatch(
        val profile: SavedDeviceProfile,
        override val reason: DeviceIdentityMatchReason =
            DeviceIdentityMatchReason.MATCH_SCOPED_IPV4_WEAK,
    ) : DeviceIdentityMatchResult

    data class Conflict(
        override val reason: DeviceIdentityMatchReason,
        val profileIds: Set<Long>,
        val conflictingEvidence: Set<DeviceIdentityMatchReason>,
    ) : DeviceIdentityMatchResult

    data class NoMatch(
        override val reason: DeviceIdentityMatchReason = DeviceIdentityMatchReason.NO_MATCH,
    ) : DeviceIdentityMatchResult
}

fun DeviceIdentityMatchResult.associatedProfileOrNull(): SavedDeviceProfile? = when (this) {
    is DeviceIdentityMatchResult.StrongMatch -> profile
    is DeviceIdentityMatchResult.WeakCompatibilityMatch -> profile
    is DeviceIdentityMatchResult.Conflict,
    is DeviceIdentityMatchResult.NoMatch,
    -> null
}
