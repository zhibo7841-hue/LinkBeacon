package com.networktoolbox.core.common.favorites

import com.networktoolbox.core.common.ipv4.IPv4Address
import java.util.Locale

/**
 * Conservative identity matching for saved LAN devices.
 *
 * A stronger identity never falls back to a weaker one. This intentionally
 * prefers missing a match over merging two different devices.
 */
object FavoriteIdentityMatcher {
    fun normalizeMac(value: String?): String? {
        val compact = value
            ?.trim()
            ?.replace(":", "")
            ?.replace("-", "")
            ?.replace(".", "")
            ?.takeIf {
                it.length == 12 && it.all { character ->
                    character in '0'..'9' || character.lowercaseChar() in 'a'..'f'
                }
            }
            ?.uppercase(Locale.ROOT)
            ?: return null

        if (compact == "000000000000" || compact == "FFFFFFFFFFFF" || compact == ANDROID_PLACEHOLDER) {
            return null
        }
        return compact.chunked(2).joinToString(":")
    }

    fun normalizeProtocol(value: String?): String? = value
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.ROOT)

    fun normalizeIpv4(value: String?): String? = value
        ?.let(IPv4Address::parse)
        ?.toDottedDecimal()

    fun identityFor(candidate: FavoriteDeviceCandidate): FavoriteDeviceIdentity {
        normalizeMac(candidate.macAddress)?.let { return FavoriteDeviceIdentity(FavoriteIdentityType.MAC, it) }
        normalizeProtocol(candidate.protocolIdentity)?.let {
            return FavoriteDeviceIdentity(FavoriteIdentityType.PROTOCOL, it)
        }
        return FavoriteDeviceIdentity(
            FavoriteIdentityType.NETWORK_IP,
            normalizeIpv4(candidate.ipv4Address)
                ?: error("Favorite candidate must contain a valid IPv4 address."),
        )
    }

    /**
     * Evaluates all current-scope profiles together so ambiguity and evidence
     * conflicts cannot be hidden by repository ordering.
     */
    fun match(
        savedProfiles: List<SavedDeviceProfile>,
        candidate: FavoriteDeviceCandidate,
    ): DeviceIdentityMatchResult {
        val scopedProfiles = savedProfiles.filter { it.networkScope == candidate.networkScope }
        if (scopedProfiles.isEmpty()) return DeviceIdentityMatchResult.NoMatch()

        val candidateMac = normalizeMac(candidate.macAddress)
        val candidateProtocol = normalizeProtocol(candidate.protocolIdentity)
        val candidateIpv4 = normalizeIpv4(candidate.ipv4Address)

        val macMatches = candidateMac?.let { mac ->
            scopedProfiles.filter { savedMac(it) == mac }
        }.orEmpty()
        val protocolMatches = candidateProtocol?.let { protocol ->
            scopedProfiles.filter { savedProtocol(it) == protocol }
        }.orEmpty()
        val strongMatches = (macMatches + protocolMatches).distinct()

        if (macMatches.size > 1 || protocolMatches.size > 1) {
            return conflict(
                reason = DeviceIdentityMatchReason.AMBIGUOUS_STRONG_MATCH,
                profiles = strongMatches,
            )
        }
        if (strongMatches.size > 1) {
            return conflict(
                reason = DeviceIdentityMatchReason.CONFLICT_MULTIPLE_STRONG_EVIDENCE,
                profiles = strongMatches,
            )
        }
        if (strongMatches.size == 1) {
            val profile = strongMatches.single()
            val conflicts = strongConflicts(profile, candidateMac, candidateProtocol)
            if (conflicts.isNotEmpty()) {
                return DeviceIdentityMatchResult.Conflict(
                    reason = conflicts.first(),
                    profileIds = setOf(profile.id),
                    conflictingEvidence = conflicts,
                )
            }
            return DeviceIdentityMatchResult.StrongMatch(
                profile = profile,
                reason = if (profile in macMatches) {
                    DeviceIdentityMatchReason.MATCH_MAC
                } else {
                    DeviceIdentityMatchReason.MATCH_UPNP_UDN
                },
            )
        }

        val sameAddressProfiles = scopedProfiles.filter { profile ->
            val savedIpv4 = normalizeIpv4(profile.lastKnownIpv4)
                ?: profile.identityValue.takeIf { profile.identityType == FavoriteIdentityType.NETWORK_IP }
                    ?.let(::normalizeIpv4)
            savedIpv4 == candidateIpv4
        }
        if (sameAddressProfiles.isEmpty()) return DeviceIdentityMatchResult.NoMatch()

        val conflictingProfiles = sameAddressProfiles.mapNotNull { profile ->
            strongConflicts(profile, candidateMac, candidateProtocol)
                .takeIf { it.isNotEmpty() }
                ?.let { profile to it }
        }
        if (conflictingProfiles.isNotEmpty()) {
            val evidence = conflictingProfiles.flatMap { it.second }.toSet()
            return DeviceIdentityMatchResult.Conflict(
                reason = evidence.first(),
                profileIds = conflictingProfiles.map { it.first.id }.toSet(),
                conflictingEvidence = evidence,
            )
        }
        if (sameAddressProfiles.size > 1) {
            return conflict(
                reason = DeviceIdentityMatchReason.AMBIGUOUS_WEAK_MATCH,
                profiles = sameAddressProfiles,
            )
        }
        return DeviceIdentityMatchResult.WeakCompatibilityMatch(sameAddressProfiles.single())
    }

    /** Compatibility helper. New production code should inspect [match]. */
    fun matches(saved: FavoriteDevice, candidate: FavoriteDeviceCandidate): Boolean =
        match(listOf(saved), candidate).associatedProfileOrNull() != null

    fun canonicalIdentity(identity: FavoriteDeviceIdentity): FavoriteDeviceIdentity = when (identity.type) {
        FavoriteIdentityType.MAC -> FavoriteDeviceIdentity(
            FavoriteIdentityType.MAC,
            normalizeMac(identity.value) ?: identity.value.trim().uppercase(Locale.ROOT),
        )

        FavoriteIdentityType.PROTOCOL -> FavoriteDeviceIdentity(
            FavoriteIdentityType.PROTOCOL,
            normalizeProtocol(identity.value) ?: identity.value.trim().lowercase(Locale.ROOT),
        )

        FavoriteIdentityType.NETWORK_IP -> FavoriteDeviceIdentity(
            FavoriteIdentityType.NETWORK_IP,
            normalizeIpv4(identity.value) ?: identity.value.trim(),
        )
    }

    private const val ANDROID_PLACEHOLDER = "020000000000"

    private fun savedMac(profile: SavedDeviceProfile): String? =
        normalizeMac(profile.macAddress)
            ?: normalizeMac(profile.identityValue.takeIf { profile.identityType == FavoriteIdentityType.MAC })

    private fun savedProtocol(profile: SavedDeviceProfile): String? =
        normalizeProtocol(profile.protocolIdentity)
            ?: normalizeProtocol(
                profile.identityValue.takeIf { profile.identityType == FavoriteIdentityType.PROTOCOL },
            )

    private fun strongConflicts(
        profile: SavedDeviceProfile,
        candidateMac: String?,
        candidateProtocol: String?,
    ): Set<DeviceIdentityMatchReason> = buildSet {
        val storedMac = savedMac(profile)
        if (candidateMac != null && storedMac != null && candidateMac != storedMac) {
            add(DeviceIdentityMatchReason.CONFLICT_MAC)
        }
        val storedProtocol = savedProtocol(profile)
        if (
            candidateProtocol != null &&
            storedProtocol != null &&
            candidateProtocol != storedProtocol
        ) {
            add(DeviceIdentityMatchReason.CONFLICT_UPNP_UDN)
        }
    }

    private fun conflict(
        reason: DeviceIdentityMatchReason,
        profiles: List<SavedDeviceProfile>,
    ): DeviceIdentityMatchResult.Conflict = DeviceIdentityMatchResult.Conflict(
        reason = reason,
        profileIds = profiles.map(SavedDeviceProfile::id).toSet(),
        conflictingEvidence = setOf(reason),
    )
}
