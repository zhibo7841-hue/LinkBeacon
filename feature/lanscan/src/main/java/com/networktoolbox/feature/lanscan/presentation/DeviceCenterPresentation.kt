package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.feature.lanscan.domain.model.identity
import com.networktoolbox.core.common.ipv4.IPv4Address
import com.networktoolbox.core.common.wol.IPv4BroadcastResolution
import com.networktoolbox.core.common.wol.IPv4BroadcastResolver
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import java.util.Locale

/**
 * Small presentation values for the top-level Devices destination.
 *
 * The Device Center deliberately reuses [LanScannerPresentation] and the
 * existing identity aggregator. It does not create a second device model or
 * infer device metadata that the scanner did not observe.
 */
data class DeviceCenterNetworkSummary(
    val networkLabel: UiText,
    val networkName: String?,
    val subnet: String?,
    val localAddress: String?,
    val gateway: String?,
    val wifiSignalLevel: Int?,
)

data class LanDeviceCardPresentation(
    val displayName: UiText,
    val ipAddress: String,
    val identitySummary: String? = null,
    val evidence: UiText? = null,
    val role: UiText? = null,
    val macAddress: String? = null,
    val isFavorite: Boolean = false,
    val quickWake: QuickWakePresentation? = null,
)

/** A compact, accessible action shown only for an eligible saved profile. */
data class QuickWakePresentation(
    val contentDescription: UiText,
)

data class DeviceCenterDeviceItem(
    val device: LanDevice? = null,
    val favorite: FavoriteDevice? = null,
    val observedThisScan: Boolean,
    val isFavorite: Boolean,
    val detailKey: String,
    val card: LanDeviceCardPresentation,
)

enum class DeviceCenterFilter {
    ALL,
    DISCOVERED,
    NOT_DISCOVERED,
    FAVORITES,
}

data class DeviceCenterSearchState(
    val isSearchActive: Boolean = false,
    val query: String = "",
    val filter: DeviceCenterFilter = DeviceCenterFilter.ALL,
)

data class DeviceDetailPresentation(
    val detailKey: String,
    val displayName: UiText,
    val ipAddress: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val hostname: String?,
    val mdnsNames: List<String>,
    val upnpNames: List<String>,
    val role: UiText?,
    /** User-facing label only; opaque scope fingerprints are never displayed. */
    val networkScope: UiText?,
    val observedThisScan: Boolean,
    val lastSeenAt: Long?,
    val customName: String? = null,
    val isFavorite: Boolean,
    val canToggleFavorite: Boolean,
    val wakeOnLan: WakeOnLanDetailPresentation = WakeOnLanDetailPresentation.notConfigured(),
) {
    /** Derived labels keep the star, status text, and accessibility semantics aligned. */
    val favoriteStatusLabel: UiText
        get() = if (isFavorite) UiText(R.string.lan_favorite) else UiText(R.string.lan_not_favorite)

    val favoriteToggleContentDescription: UiText
        get() = if (isFavorite) UiText(R.string.lan_unfavorite) else UiText(R.string.lan_add_favorite)

    /** A validated IPv4 target for the existing Ping/TCP tool entry points. */
    val networkToolTarget: String?
        get() = FavoriteIdentityMatcher.normalizeIpv4(ipAddress)
}

enum class WakeOnLanAvailability {
    NOT_CONFIGURED,
    AVAILABLE,
    NO_ACTIVE_NETWORK,
    UNSUPPORTED_NETWORK,
    NO_IPV4,
    SCOPE_MISMATCH,
    BROADCAST_UNAVAILABLE,
}

data class WakeOnLanDetailPresentation(
    val config: WakeOnLanConfig?,
    val availability: WakeOnLanAvailability,
) {
    val canSend: Boolean
        get() = config != null && availability == WakeOnLanAvailability.AVAILABLE

    /** Configuration is an editable local profile value, not an immediate send. */
    val canConfigure: Boolean = true

    companion object {
        fun notConfigured(): WakeOnLanDetailPresentation = WakeOnLanDetailPresentation(
            config = null,
            availability = WakeOnLanAvailability.NOT_CONFIGURED,
        )
    }
}

object DeviceCenterPresentation {
    /**
     * Applies the Device Center's local-only query and filter without changing
     * the ordering supplied by [deviceList].
     */
    fun filterDeviceItems(
        items: List<DeviceCenterDeviceItem>,
        query: String,
        filter: DeviceCenterFilter,
    ): List<DeviceCenterDeviceItem> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return items.filter { item ->
            matchesFilter(item, filter) && (
                normalizedQuery.isEmpty() ||
                    searchableValues(item).any { value ->
                        value.lowercase(Locale.ROOT).contains(normalizedQuery)
                    }
                )
        }
    }

    fun wakeOnLan(
        config: WakeOnLanConfig?,
        savedNetworkScope: String?,
        context: NetworkContext,
    ): WakeOnLanDetailPresentation {
        if (context.activeNetworkAvailable == false) {
            return WakeOnLanDetailPresentation(config, WakeOnLanAvailability.NO_ACTIVE_NETWORK)
        }
        if (context.connectionType != ConnectionType.WIFI &&
            context.connectionType != ConnectionType.ETHERNET
        ) {
            return WakeOnLanDetailPresentation(config, WakeOnLanAvailability.UNSUPPORTED_NETWORK)
        }
        val currentScope = LanNetworkScope.from(context)
        if (config != null && (currentScope == null || currentScope != savedNetworkScope)) {
            return WakeOnLanDetailPresentation(config, WakeOnLanAvailability.SCOPE_MISMATCH)
        }
        if (context.ipv4Address.isNullOrBlank()) {
            return WakeOnLanDetailPresentation(config, WakeOnLanAvailability.NO_IPV4)
        }
        if (IPv4BroadcastResolver.resolve(context.ipv4Address, context.ipv4PrefixLength) !is
            IPv4BroadcastResolution.Available
        ) {
            return WakeOnLanDetailPresentation(config, WakeOnLanAvailability.BROADCAST_UNAVAILABLE)
        }
        return WakeOnLanDetailPresentation(
            config = config,
            availability = if (config == null) {
                WakeOnLanAvailability.NOT_CONFIGURED
            } else {
                WakeOnLanAvailability.AVAILABLE
            },
        )
    }

    fun networkSummary(
        context: NetworkContext,
        range: LanScanRange? = null,
    ): DeviceCenterNetworkSummary = DeviceCenterNetworkSummary(
        networkLabel = context.connectionType.displayName(),
        networkName = context.wifiName
            ?.trim()
            ?.takeIf { context.connectionType == ConnectionType.WIFI && it.isRealWifiName() },
        subnet = range?.displayLabel,
        localAddress = context.ipv4Address?.trim()?.takeIf(String::isNotBlank),
        gateway = context.gateway
            ?.trim()
            ?.takeIf { it.isNotBlank() && context.connectionType.isLocalNetwork() },
        wifiSignalLevel = context.wifiSignalLevel
            ?.takeIf { context.connectionType == ConnectionType.WIFI },
    )

    /** Resolves custom override before the detected identity and neutral fallback. */
    fun deviceDisplayName(device: LanDevice): UiText = deviceDisplayName(device, null)

    fun deviceDisplayName(device: LanDevice, favorite: FavoriteDevice?): UiText =
        LanScannerPresentation.displayName(
            customName = favorite?.customName,
            detectedName = LanScannerPresentation.devicePrimaryText(device)
                .takeUnless { it == device.ipAddress },
        )

    fun deviceAddress(device: LanDevice): String = device.ipAddress

    fun deviceIdentitySummary(device: LanDevice): String? =
        LanScannerPresentation.deviceIdentitySummary(device)

    fun deviceRole(device: LanDevice): UiText? = LanScannerPresentation.deviceRole(device)

    /**
     * The evidence line stays grounded in the scanner's confirmed evidence.
     * Ordinary devices never receive a synthetic "在线" badge.
     */
    fun deviceEvidence(device: LanDevice): UiText? =
        LanScannerPresentation.deviceSecondaryText(device)

    /**
     * Merges current scan observations with saved favorites without changing
     * the scanner's ordering or turning an absent observation into offline.
     */
    fun deviceList(
        devices: List<LanDevice>,
        favorites: List<FavoriteDevice>,
        context: NetworkContext,
        includeUnseenFavorites: Boolean = true,
        unseenEvidence: UiText = UiText(R.string.lan_not_found),
    ): List<DeviceCenterDeviceItem> {
        val scope = LanNetworkScope.from(context)
        val scopedFavorites = favorites.filter { it.networkScope == scope }
        val observedItems = devices.mapNotNull { device ->
            val candidate = LanFavoriteIdentity.candidate(device, context)
            val favorite = candidate?.let { current ->
                scopedFavorites.firstOrNull { saved ->
                    FavoriteIdentityMatcher.matches(saved, current)
                }
            }
            val detailKey = favorite
                ?.takeIf { it.isFavorite }
                ?.let(LanDeviceDetailRouteKey::forFavorite)
                ?: LanDeviceDetailRouteKey.forObserved(scope, device.ipAddress)
            DeviceCenterDeviceItem(
                device = device,
                favorite = favorite,
                observedThisScan = true,
                isFavorite = favorite?.isFavorite == true,
                detailKey = detailKey,
                card = card(device, favorite),
            )
        }
        val observedFavoriteKeys = observedItems.mapNotNull { it.favorite?.let(::favoriteKey) }.toSet()
        val unseenItems = if (includeUnseenFavorites) {
            scopedFavorites
                .filter { it.isFavorite || it.customName != null || it.wolConfig != null }
                .filterNot { favorite -> favoriteKey(favorite) in observedFavoriteKeys }
                .map { favorite ->
                    DeviceCenterDeviceItem(
                        favorite = favorite,
                        observedThisScan = false,
                        isFavorite = favorite.isFavorite,
                        detailKey = LanDeviceDetailRouteKey.forFavorite(favorite),
                        card = card(favorite, context, unseenEvidence),
                    )
                }
        } else {
            emptyList()
        }

        return (observedItems + unseenItems).sortedWith(
            compareBy<DeviceCenterDeviceItem>(
                { item -> itemGroup(item) },
                { item -> ipv4SortValue(item.card.ipAddress) },
                { item -> item.card.displayName.raw.orEmpty() },
            ),
        )
    }

    /**
     * Saved profiles are deliberately presented separately before the first
     * scan of a scope. The neutral text describes the session state and never
     * implies that the device is offline.
     */
    fun savedProfilesBeforeScan(
        favorites: List<FavoriteDevice>,
        context: NetworkContext,
        unseenEvidence: UiText = UiText(R.string.lan_not_scanned),
    ): List<DeviceCenterDeviceItem> = deviceList(
        devices = emptyList(),
        favorites = favorites,
        context = context,
        includeUnseenFavorites = true,
        unseenEvidence = unseenEvidence,
    )

    /**
     * Returns only the current-scope profiles that have not matched one of
     * the supplied observations. Device Center uses this to keep its saved
     * group separate from the current scan group.
     */
    fun savedProfilesNotObserved(
        devices: List<LanDevice>,
        favorites: List<FavoriteDevice>,
        context: NetworkContext,
        unseenEvidence: UiText,
    ): List<DeviceCenterDeviceItem> = deviceList(
        devices = devices,
        favorites = favorites,
        context = context,
        includeUnseenFavorites = true,
        unseenEvidence = unseenEvidence,
    ).filterNot(DeviceCenterDeviceItem::observedThisScan)

    fun detail(
        device: LanDevice,
        favorite: FavoriteDevice?,
        context: NetworkContext,
        observedThisScan: Boolean = true,
        detailKey: String = LanDeviceDetailRouteKey.forObserved(
            LanNetworkScope.from(context),
            device.ipAddress,
        ),
        wakeOnLanContext: NetworkContext? = null,
    ): DeviceDetailPresentation {
        val identity = device.identity
        val role = deviceRole(device)
            ?: favorite?.let(::favoriteRole)
        val mdnsNames = device.mdnsObservations
            .flatMap { observation -> listOf(observation.serviceName, observation.hostname.orEmpty()) }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOfNotNull(favorite?.lastKnownMdnsName) }
        val upnpNames = device.upnpObservations
            .mapNotNull { it.friendlyName?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .ifEmpty { listOfNotNull(favorite?.lastKnownUpnpName) }
        return DeviceDetailPresentation(
            detailKey = detailKey,
            displayName = deviceDisplayName(device, favorite),
            ipAddress = device.ipAddress,
            macAddress = FavoriteIdentityMatcher.normalizeMac(device.macAddress)
                ?: favorite?.macAddress,
            vendor = identity.manufacturer?.value ?: favorite?.vendor,
            model = identity.modelName?.value
                ?: identity.modelDescription?.value
                ?: identity.modelNumber?.value
                ?: favorite?.model,
            hostname = identity.hostname?.value ?: favorite?.lastKnownHostname,
            mdnsNames = mdnsNames,
            upnpNames = upnpNames,
            role = role,
            networkScope = LanNetworkScope.from(context)?.let { UiText(R.string.lan_current_lan) },
            observedThisScan = observedThisScan,
            lastSeenAt = device.lastSeen,
            customName = favorite?.customName,
            isFavorite = favorite?.isFavorite == true,
            canToggleFavorite = LanNetworkScope.from(context) != null,
            wakeOnLan = wakeOnLan(
                config = favorite?.wolConfig,
                savedNetworkScope = favorite?.networkScope,
                context = wakeOnLanContext ?: context,
            ),
        )
    }

    fun detail(
        favorite: FavoriteDevice,
        context: NetworkContext,
        detailKey: String = LanDeviceDetailRouteKey.forFavorite(favorite),
        wakeOnLanContext: NetworkContext? = null,
    ): DeviceDetailPresentation = DeviceDetailPresentation(
        detailKey = detailKey,
        displayName = LanScannerPresentation.displayName(
            customName = favorite.customName,
            detectedName = favorite.lastKnownDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != favorite.lastKnownIpv4 },
        ),
        ipAddress = favorite.lastKnownIpv4,
        macAddress = FavoriteIdentityMatcher.normalizeMac(favorite.macAddress),
        vendor = favorite.vendor,
        model = favorite.model,
        hostname = favorite.lastKnownHostname,
        mdnsNames = listOfNotNull(favorite.lastKnownMdnsName),
        upnpNames = listOfNotNull(favorite.lastKnownUpnpName),
        role = favoriteRole(favorite),
        networkScope = LanNetworkScope.from(context)?.let { UiText(R.string.lan_current_lan) },
        observedThisScan = false,
        lastSeenAt = favorite.lastSeenAt,
        customName = favorite.customName,
        isFavorite = favorite.isFavorite,
        canToggleFavorite = LanNetworkScope.from(context) != null,
        wakeOnLan = wakeOnLan(
            config = favorite.wolConfig,
            savedNetworkScope = favorite.networkScope,
            context = wakeOnLanContext ?: context,
        ),
    )

    private fun card(device: LanDevice, favorite: FavoriteDevice?): LanDeviceCardPresentation =
        LanDeviceCardPresentation(
            displayName = deviceDisplayName(device, favorite),
            ipAddress = device.ipAddress,
            identitySummary = deviceIdentitySummary(device),
            evidence = deviceEvidence(device),
            role = deviceRole(device),
            macAddress = device.macAddress,
            isFavorite = favorite?.isFavorite == true,
        )

    private fun card(
        favorite: FavoriteDevice,
        context: NetworkContext,
        evidence: UiText = UiText(R.string.lan_not_found),
    ): LanDeviceCardPresentation {
        val displayName = LanScannerPresentation.displayName(
            customName = favorite.customName,
            detectedName = favorite.lastKnownDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != favorite.lastKnownIpv4 },
        )
        return LanDeviceCardPresentation(
            displayName = displayName,
            ipAddress = favorite.lastKnownIpv4.orEmpty(),
            identitySummary = listOfNotNull(favorite.vendor, favorite.model)
                .joinToString(" · ")
                .takeIf(String::isNotBlank),
            evidence = evidence,
            role = favoriteRole(favorite),
            macAddress = favorite.macAddress,
            isFavorite = favorite.isFavorite,
            quickWake = favorite.wolConfig
                ?.takeIf {
                    wakeOnLan(
                        config = it,
                        savedNetworkScope = favorite.networkScope,
                        context = context,
                    ).canSend
                }
                ?.let { QuickWakePresentation(contentDescription = UiText(R.string.lan_wake_name, displayName)) },
        )
    }

    private fun itemGroup(item: DeviceCenterDeviceItem): Int = when {
        item.observedThisScan && item.isFavorite -> 0
        item.observedThisScan && (item.device?.isGateway == true || item.device?.isLocalDevice == true) -> 1
        item.observedThisScan -> 2
        else -> 3
    }

    private fun favoriteKey(favorite: FavoriteDevice): String = listOf(
        favorite.networkScope,
        favorite.identityType.name,
        favorite.identityValue,
    ).joinToString("\u0000")

    private fun favoriteRole(favorite: FavoriteDevice): UiText? =
        LanScannerPresentation.role(favorite.isLocalDevice, favorite.isGateway)

    private fun matchesFilter(
        item: DeviceCenterDeviceItem,
        filter: DeviceCenterFilter,
    ): Boolean = when (filter) {
        DeviceCenterFilter.ALL -> true
        DeviceCenterFilter.DISCOVERED -> item.observedThisScan
        DeviceCenterFilter.NOT_DISCOVERED -> !item.observedThisScan
        DeviceCenterFilter.FAVORITES -> item.isFavorite
    }

    private fun searchableValues(item: DeviceCenterDeviceItem): List<String> = buildList {
        fun addValue(value: String?) {
            value?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        }

        addValue(item.card.displayName.raw)
        addValue(item.card.ipAddress)
        item.device?.let { device ->
            val identity = device.identity
            addValue(device.ipAddress)
            addValue(device.hostName)
            addValue(identity.displayName.value)
            addValue(identity.hostname?.value)
            addValue(identity.manufacturer?.value)
            addValue(identity.modelName?.value)
            addValue(identity.modelNumber?.value)
            addValue(identity.modelDescription?.value)
        }
        item.favorite?.let { favorite ->
            addValue(favorite.customName)
            addValue(favorite.lastKnownIpv4)
            addValue(favorite.lastKnownDisplayName)
            addValue(favorite.lastKnownHostname)
            addValue(favorite.lastKnownMdnsName)
            addValue(favorite.lastKnownUpnpName)
            addValue(favorite.vendor)
            addValue(favorite.model)
        }
    }

    private fun ipv4SortValue(value: String): Long = IPv4Address.parse(value)?.value ?: Long.MAX_VALUE

    private fun ConnectionType.isLocalNetwork(): Boolean = this == ConnectionType.WIFI ||
        this == ConnectionType.ETHERNET

    private fun ConnectionType.displayName(): UiText = when (this) {
        ConnectionType.WIFI -> UiText("Wi-Fi")
        ConnectionType.ETHERNET -> UiText(R.string.lan_ethernet)
        ConnectionType.CELLULAR -> UiText(R.string.lan_mobile)
        ConnectionType.VPN -> UiText("VPN")
        ConnectionType.BLUETOOTH -> UiText(R.string.lan_bluetooth)
        ConnectionType.UNKNOWN -> UiText(R.string.lan_unknown_network)
    }

    private fun String.isRealWifiName(): Boolean = lowercase() !in setOf(
        "<unknown ssid>",
        "unknown ssid",
        "unknown",
    )
}
