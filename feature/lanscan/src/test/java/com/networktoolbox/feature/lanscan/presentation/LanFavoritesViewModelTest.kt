package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.designsystem.UiText
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.DeviceIdentityMatchResult
import com.networktoolbox.core.common.favorites.DeviceType
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.wol.MacAddress
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import com.networktoolbox.core.common.wol.WakeOnLanFailureReason
import com.networktoolbox.core.common.wol.WakeOnLanResult
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.NoOpSendWakeOnLan
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.SendWakeOnLan
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanFavoritesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `weak compatibility match keeps presentation continuity without updating last seen`() = runTest {
        val context = context()
        val observed = device("10.0.1.20")
        val profile = LanFavoriteIdentity.createSavedProfile(observed, context, now = 1L)!!
            .copy(id = 1L, lastSeenAt = 1L)
        val repository = FakeFavoriteDeviceRepository(listOf(profile))
        val viewModel = viewModel(context, observed, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(0, repository.updateLastObservedCalls)
        assertEquals(1L, repository.observeProfiles().first().single().lastSeenAt)
        assertTrue(viewModel.detailRouteKey(observed).startsWith("favorite:"))
    }

    @Test
    fun `strong mac match updates last seen without changing user fields`() = runTest {
        val context = context()
        val observed = device("10.0.1.99", macAddress = "AA:BB:CC:DD:EE:FF")
        val original = LanFavoriteIdentity.createSavedProfile(
            device = device("10.0.1.20", macAddress = "AA:BB:CC:DD:EE:FF"),
            context = context,
            now = 1L,
        )!!.copy(id = 1L, customName = "Server", lastSeenAt = 1L)
        val repository = FakeFavoriteDeviceRepository(listOf(original))
        val viewModel = viewModel(context, observed, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val updated = repository.observeProfiles().first().single()
        assertEquals(1, repository.updateLastObservedCalls)
        assertEquals(42L, updated.lastSeenAt)
        assertEquals("10.0.1.99", updated.lastKnownIpv4)
        assertEquals("Server", updated.customName)
    }

    @Test
    fun `new managed profile records first seen from current observation`() {
        val observed = device("10.0.1.20")

        val profile = LanFavoriteIdentity.createSavedProfile(observed, context(), now = 100L)!!

        assertEquals(42L, profile.firstSeenAt)
        assertEquals(100L, profile.createdAt)
    }

    @Test
    fun `observed device can be favorited and resolved through detail route`() = runTest {
        val context = context()
        val device = device("10.0.1.20")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)
        val initialDetail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)
        assertNotNull(initialDetail)
        assertTrue(initialDetail!!.isFavorite.not())
        assertPresentationEquals("未收藏", initialDetail.favoriteStatusLabel)
        assertPresentationEquals("收藏设备", initialDetail.favoriteToggleContentDescription)

        viewModel.toggleFavoriteByRouteKey(route)
        advanceUntilIdle()

        val favorite = viewModel.favoriteDevices.value.single()
        val detail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)

        assertPresentationEquals("10.0.1.20", favorite.lastKnownIpv4)
        assertTrue(favorite.isLocalDevice.not())
        assertNotNull(detail)
        assertTrue(detail!!.isFavorite)
        assertTrue(detail.observedThisScan)
        assertPresentationEquals("已收藏", detail.favoriteStatusLabel)
        assertPresentationEquals("取消收藏", detail.favoriteToggleContentDescription)
    }

    @Test
    fun `toggling the same observed device removes its favorite`() = runTest {
        val context = context()
        val device = device("10.0.1.20")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        assertTrue(viewModel.favoriteDevices.value.isEmpty())
    }

    @Test
    fun `observed route supports repeated off on off on transitions`() = runTest {
        val context = context()
        val device = device("10.0.1.25")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)

        listOf(true, false, true, false).forEach { expectedFavorite ->
            viewModel.toggleFavoriteByRouteKey(route)
            advanceUntilIdle()

            val detail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)
            assertNotNull(detail)
            assertPresentationEquals(expectedFavorite, detail!!.isFavorite)
            assertPresentationEquals(
                if (expectedFavorite) "已收藏" else "未收藏",
                detail.favoriteStatusLabel,
            )
            assertPresentationEquals(
                if (expectedFavorite) "取消收藏" else "收藏设备",
                detail.favoriteToggleContentDescription,
            )
            assertPresentationEquals(if (expectedFavorite) 1 else 0, viewModel.favoriteDevices.value.size)
        }
    }

    @Test
    fun `favorite route supports repeated on off on off on transitions`() = runTest {
        val context = context()
        val device = device("10.0.1.26")
        val initialFavorite = com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
            .createFavorite(device = device, context = context, now = 1L)!!
            .copy(id = 41L)
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(initialFavorite))
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)
        assertTrue(route.startsWith("favorite:"))
        assertTrue(viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)!!.isFavorite)

        listOf(false, true, false, true).forEach { expectedFavorite ->
            viewModel.toggleFavoriteByRouteKey(route)
            advanceUntilIdle()

            val detail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)
            assertNotNull(detail)
            assertPresentationEquals(expectedFavorite, detail!!.isFavorite)
            assertPresentationEquals(
                if (expectedFavorite) "已收藏" else "未收藏",
                detail.favoriteStatusLabel,
            )
            assertPresentationEquals(
                if (expectedFavorite) "取消收藏" else "收藏设备",
                detail.favoriteToggleContentDescription,
            )
            assertPresentationEquals(if (expectedFavorite) 1 else 0, viewModel.favoriteDevices.value.size)
        }
    }

    @Test
    fun `open detail receives repository changes without route recreation`() = runTest {
        val context = context()
        val device = device("10.0.1.21")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)

        assertTrue(
            viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)!!.isFavorite.not(),
        )

        repository.save(
            com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity.createFavorite(
                device = device,
                context = context,
                now = 1L,
            )!!,
        )
        advanceUntilIdle()

        val addedDetail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)
        assertTrue(addedDetail!!.isFavorite)
        assertPresentationEquals("已收藏", addedDetail.favoriteStatusLabel)

        repository.delete(viewModel.favoriteDevices.value.single().id)
        advanceUntilIdle()

        val removedDetail = viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value)
        assertTrue(removedDetail!!.isFavorite.not())
        assertPresentationEquals("未收藏", removedDetail.favoriteStatusLabel)
    }

    @Test
    fun `favorite route falls back to observed device after removal`() = runTest {
        val context = context()
        val device = device("10.0.1.22")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        val favoriteRoute = viewModel.detailRouteKey(device)
        assertTrue(viewModel.resolveDeviceDetail(favoriteRoute, viewModel.favoriteDevices.value)!!.isFavorite)

        viewModel.toggleFavoriteByRouteKey(favoriteRoute)
        advanceUntilIdle()

        val detailAfterRemoval = viewModel.resolveDeviceDetail(
            favoriteRoute,
            viewModel.favoriteDevices.value,
        )
        assertNotNull(detailAfterRemoval)
        assertTrue(detailAfterRemoval!!.isFavorite.not())
        assertPresentationEquals("未收藏", detailAfterRemoval.favoriteStatusLabel)

        viewModel.toggleFavoriteByRouteKey(favoriteRoute)
        advanceUntilIdle()

        val detailAfterReAdd = viewModel.resolveDeviceDetail(
            favoriteRoute,
            viewModel.favoriteDevices.value,
        )
        assertNotNull(detailAfterReAdd)
        assertTrue(detailAfterReAdd!!.isFavorite)
        assertPresentationEquals("已收藏", detailAfterReAdd.favoriteStatusLabel)
        assertPresentationEquals(1, viewModel.favoriteDevices.value.size)
    }

    @Test
    fun `favorite route without observation remains conservative after removal`() = runTest {
        val context = context()
        val favoriteDevice = device("10.0.1.27")
        val favorite = com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
            .createFavorite(device = favoriteDevice, context = context, now = 1L)!!
            .copy(id = 42L)
        val observedOtherDevice = device("10.0.1.28")
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(favorite))
        val viewModel = viewModel(context, observedOtherDevice, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = LanDeviceDetailRouteKey.forFavorite(favorite)
        assertNotNull(viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value))

        viewModel.toggleFavoriteByRouteKey(route)
        advanceUntilIdle()

        assertTrue(viewModel.favoriteDevices.value.isEmpty())
        assertPresentationEquals(null, viewModel.resolveDeviceDetail(route, viewModel.favoriteDevices.value))
    }

    @Test
    fun `rapid toggles are serialized and settle deterministically`() = runTest {
        val context = context()
        val device = device("10.0.1.23")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        assertTrue(viewModel.favoriteDevices.value.isEmpty())
        assertPresentationEquals(null, viewModel.favoriteActionError.value)
    }

    @Test
    fun `favorite write failure keeps state and exposes a user error`() = runTest {
        val context = context()
        val device = device("10.0.1.24")
        val repository = FakeFavoriteDeviceRepository(failWrites = true)
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        assertTrue(viewModel.favoriteDevices.value.isEmpty())
        assertPresentationEquals("收藏失败，请重试。", viewModel.favoriteActionError.value)
    }

    @Test
    fun `custom name is persisted immediately without favoriting observed device`() = runTest {
        val context = context()
        val device = device("10.0.1.30")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)

        viewModel.setCustomNameByRouteKey(route, "客厅 NAS")
        advanceUntilIdle()

        val profile = viewModel.savedProfiles.value.single()
        assertPresentationEquals("客厅 NAS", profile.customName)
        assertTrue(profile.isFavorite.not())
        assertTrue(viewModel.favoriteDevices.value.isEmpty())
        val detail = viewModel.resolveDeviceDetail(route, viewModel.savedProfiles.value)
        assertNotNull(detail)
        assertPresentationEquals("客厅 NAS", detail!!.displayName)
        assertTrue(detail.isFavorite.not())
        assertPresentationEquals(null, viewModel.customNameActionError.value)
    }

    @Test
    fun `custom name and favorite can be changed and restored independently`() = runTest {
        val context = context()
        val device = device("10.0.1.31")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)

        viewModel.setCustomNameByRouteKey(route, "主路由")
        advanceUntilIdle()
        viewModel.toggleFavoriteByRouteKey(route)
        advanceUntilIdle()

        assertTrue(viewModel.savedProfiles.value.single().isFavorite)
        assertPresentationEquals("主路由", viewModel.savedProfiles.value.single().customName)

        viewModel.clearCustomNameByRouteKey(route)
        advanceUntilIdle()

        assertTrue(viewModel.savedProfiles.value.single().isFavorite)
        assertPresentationEquals(null, viewModel.savedProfiles.value.single().customName)
        assertPresentationEquals("未知设备", viewModel.resolveDeviceDetail(route, viewModel.savedProfiles.value)!!.displayName)
    }

    @Test
    fun `invalid custom name is rejected without creating a saved profile`() = runTest {
        val context = context()
        val device = device("10.0.1.32")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        viewModel.setCustomNameByRouteKey(viewModel.detailRouteKey(device), "   ")
        advanceUntilIdle()

        assertTrue(viewModel.savedProfiles.value.isEmpty())
        assertPresentationEquals("名称不能为空、不能包含控制字符，且最多 40 个字符。", viewModel.customNameActionError.value)
    }

    @Test
    fun `observation refresh changes detected metadata but not custom name`() = runTest {
        val context = context()
        val device = device("10.0.1.33")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)
        viewModel.setCustomNameByRouteKey(route, "实验设备")
        advanceUntilIdle()

        repository.updateLastObserved(
            id = viewModel.savedProfiles.value.single().id,
            observation = FavoriteDeviceObservation(
                lastKnownIpv4 = "10.0.1.34",
                lastKnownDisplayName = "new-device.local",
                lastKnownHostname = "new-device.local",
                lastKnownMdnsName = null,
                lastKnownUpnpName = null,
                macAddress = null,
                vendor = null,
                model = null,
                lastSeenAt = 100L,
                isGateway = false,
                isLocalDevice = false,
            ),
        )
        advanceUntilIdle()

        val profile = viewModel.savedProfiles.value.single()
        assertPresentationEquals("实验设备", profile.customName)
        assertPresentationEquals("new-device.local", profile.lastKnownDisplayName)
    }

    @Test
    fun `successful wake emits one shot event without changing saved profile`() = runTest {
        val context = context()
        val device = device("10.0.1.34")
        val config = WakeOnLanConfig(MacAddress.parse("00:11:22:33:44:55")!!)
        val profile = LanFavoriteIdentity.createFavorite(device, context, now = 1L)!!
            .copy(id = 7L, customName = "客厅主机", wolConfig = config)
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(profile))
        val viewModel = viewModel(
            context = context,
            device = device,
            repository = repository,
            sendWakeOnLan = SendWakeOnLan {
                WakeOnLanResult.Sent("10.0.1.255", config.udpPort)
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val before = viewModel.savedProfiles.value.single()
        val event = async { viewModel.deviceDetailEvents.first() }
        runCurrent()

        viewModel.sendWakeOnLanByRouteKey(viewModel.detailRouteKey(device))
        advanceUntilIdle()

        assertPresentationEquals(DeviceDetailEvent.WakePacketSent, event.await())
        assertPresentationEquals(before, viewModel.savedProfiles.value.single())
        assertTrue(viewModel.deviceDetailEvents.replayCache.isEmpty())
    }

    @Test
    fun `wake failure emits transient user feedback without persistent error state`() = runTest {
        val context = context()
        val device = device("10.0.1.35")
        val config = WakeOnLanConfig(MacAddress.parse("02:11:22:33:44:55")!!)
        val profile = LanFavoriteIdentity.createFavorite(device, context, now = 1L)!!
            .copy(id = 8L, wolConfig = config)
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(profile))
        val viewModel = viewModel(
            context = context,
            device = device,
            repository = repository,
            sendWakeOnLan = SendWakeOnLan {
                WakeOnLanResult.Failed(WakeOnLanFailureReason.SEND_FAILED)
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val event = async { viewModel.deviceDetailEvents.first() }
        runCurrent()

        viewModel.sendWakeOnLanByRouteKey(viewModel.detailRouteKey(device))
        advanceUntilIdle()

        assertPresentationEquals(
            DeviceDetailEvent.WakePacketFailed(UiText(com.networktoolbox.feature.lanscan.R.string.lan_error_send_failed)),
            event.await(),
        )
        assertPresentationEquals(profile, viewModel.savedProfiles.value.single())
    }

    @Test
    fun `repeated wake actions emit a new event for each send`() = runTest {
        val context = context()
        val device = device("10.0.1.36")
        val config = WakeOnLanConfig(MacAddress.parse("02:AA:BB:CC:DD:EE")!!)
        val profile = LanFavoriteIdentity.createFavorite(device, context, now = 1L)!!
            .copy(id = 9L, wolConfig = config)
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(profile))
        val viewModel = viewModel(
            context = context,
            device = device,
            repository = repository,
            sendWakeOnLan = SendWakeOnLan {
                WakeOnLanResult.Sent("10.0.1.255", config.udpPort)
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val route = viewModel.detailRouteKey(device)

        val firstEvent = async { viewModel.deviceDetailEvents.first() }
        runCurrent()
        viewModel.sendWakeOnLanByRouteKey(route)
        advanceUntilIdle()
        assertPresentationEquals(DeviceDetailEvent.WakePacketSent, firstEvent.await())

        val secondEvent = async { viewModel.deviceDetailEvents.first() }
        runCurrent()
        viewModel.sendWakeOnLanByRouteKey(route)
        advanceUntilIdle()
        assertPresentationEquals(DeviceDetailEvent.WakePacketSent, secondEvent.await())
        assertPresentationEquals(profile, viewModel.savedProfiles.value.single())
    }

    @Test
    fun `device center quick wake route reuses existing sender for unseen profile`() = runTest {
        val context = context()
        val observedDevice = device("10.0.1.34")
        val savedDevice = device("10.0.1.50")
        val config = WakeOnLanConfig(MacAddress.parse("02:AA:BB:CC:DD:EF")!!)
        val profile = LanFavoriteIdentity.createFavorite(savedDevice, context, now = 1L)!!
            .copy(id = 10L, customName = "VAIO", wolConfig = config)
        var sendCount = 0
        val repository = FakeFavoriteDeviceRepository(initialFavorites = listOf(profile))
        val viewModel = viewModel(
            context = context,
            device = observedDevice,
            repository = repository,
            sendWakeOnLan = SendWakeOnLan {
                sendCount += 1
                WakeOnLanResult.Sent("10.0.1.255", config.udpPort)
            },
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val before = viewModel.savedProfiles.value.single()
        val event = async { viewModel.deviceDetailEvents.first() }
        runCurrent()

        viewModel.sendWakeOnLanByRouteKey(LanDeviceDetailRouteKey.forFavorite(profile))
        advanceUntilIdle()

        assertPresentationEquals(1, sendCount)
        assertPresentationEquals(DeviceDetailEvent.WakePacketSent, event.await())
        assertPresentationEquals(before, viewModel.savedProfiles.value.single())
    }

    @Test
    fun `saving wake configuration emits an event and persists a wol only profile`() = runTest {
        val context = context()
        val device = device("10.0.1.37")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        val event = async { viewModel.deviceDetailEvents.first() }
        runCurrent()

        viewModel.saveWakeOnLanByRouteKey(
            routeKey = viewModel.detailRouteKey(device),
            rawMacAddress = "02-aa-bb-cc-dd-ee",
            rawPort = "9",
        )
        advanceUntilIdle()

        assertPresentationEquals(DeviceDetailEvent.WakeOnLanConfigurationSaved, event.await())
        val saved = viewModel.savedProfiles.value.single()
        assertTrue(saved.isFavorite.not())
        assertPresentationEquals(null, saved.customName)
        assertPresentationEquals(
            WakeOnLanConfig(MacAddress.parse("02:AA:BB:CC:DD:EE")!!),
            saved.wolConfig,
        )
    }

    private fun viewModel(
        context: NetworkContext,
        device: LanDevice,
        repository: SavedDeviceRepository,
        sendWakeOnLan: SendWakeOnLan = NoOpSendWakeOnLan,
    ) = LanScannerViewModel(
        observeReadiness = ObserveLanScanReadiness {
            flowOf(
                LanScanReadiness(
                    networkContext = context,
                    rangeResult = LanScanRangeCalculator().calculate(context),
                ),
            )
        },
        runScan = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                val range = (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range
                return LanScanSession(
                    status = LanScanStatus.COMPLETED,
                    initialNetworkContext = context,
                    range = range,
                    scannedHosts = range.hostCount,
                    totalHosts = range.hostCount,
                    discoveredDevices = listOf(device),
                    startedAt = 0L,
                    finishedAt = 1L,
                )
            }
        },
        reverseDnsEnricher = ReverseDnsEnricher { _, _ -> },
        mdnsEnricher = com.networktoolbox.feature.lanscan.domain.MdnsEnricher { _, _, _, _ -> },
        savedDeviceRepository = repository,
        sendWakeOnLan = sendWakeOnLan,
    )

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )

    private fun device(ip: String, macAddress: String? = null) = LanDevice(
        ipAddress = ip,
        macAddress = macAddress,
        isLocalDevice = false,
        isGateway = false,
        latencyMs = 14L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 42L,
    )
}

private class FakeFavoriteDeviceRepository(
    initialFavorites: List<FavoriteDevice> = emptyList(),
    private val failWrites: Boolean = false,
) : FavoriteDeviceRepository, SavedDeviceRepository {
    private val state = MutableStateFlow(initialFavorites)
    private var nextId = 1L
    var updateLastObservedCalls: Int = 0
        private set

    override fun observeFavorites(): Flow<List<FavoriteDevice>> = state

    override fun observeProfiles(): Flow<List<FavoriteDevice>> = state

    override suspend fun save(profile: FavoriteDevice): Long {
        if (failWrites) error("write failed")
        val existing = state.value.firstOrNull { saved ->
            saved.identityType == profile.identityType &&
                saved.identityValue == profile.identityValue &&
                saved.networkScope == profile.networkScope
        }
        if (existing != null) return existing.id
        val id = if (profile.id > 0L) profile.id else nextId++
        state.value = state.value + profile.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        if (failWrites) error("write failed")
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        if (failWrites) error("write failed")
        val existing = state.value.firstOrNull { it.id == id } ?: return
        if (!isFavorite && existing.customName == null) {
            delete(id)
        } else {
            state.value = state.value.map { profile ->
                if (profile.id == id) profile.copy(isFavorite = isFavorite) else profile
            }
        }
    }

    override suspend fun setCustomName(id: Long, customName: String?) {
        if (failWrites) error("write failed")
        val existing = state.value.firstOrNull { it.id == id } ?: return
        if (customName == null && !existing.isFavorite) {
            delete(id)
        } else {
            state.value = state.value.map { profile ->
                if (profile.id == id) profile.copy(customName = customName) else profile
            }
        }
    }

    override suspend fun setUserDeviceType(id: Long, deviceType: DeviceType?) {
        val existing = state.value.firstOrNull { it.id == id } ?: return
        if (deviceType == null && !existing.isFavorite && existing.customName == null && existing.wolConfig == null && existing.notes == null) {
            delete(id)
        } else {
            state.value = state.value.map { profile ->
                if (profile.id == id) profile.copy(userDeviceType = deviceType) else profile
            }
        }
    }

    override suspend fun setNotes(id: Long, notes: String?) {
        val existing = state.value.firstOrNull { it.id == id } ?: return
        if (notes == null && !existing.isFavorite && existing.customName == null && existing.wolConfig == null && existing.userDeviceType == null) {
            delete(id)
        } else {
            state.value = state.value.map { profile ->
                if (profile.id == id) profile.copy(notes = notes) else profile
            }
        }
    }

    override suspend fun setWakeOnLanConfig(id: Long, config: WakeOnLanConfig?) {
        if (failWrites) error("write failed")
        val existing = state.value.firstOrNull { it.id == id } ?: return
        if (config == null && !existing.isFavorite && existing.customName == null) {
            delete(id)
        } else {
            state.value = state.value.map { profile ->
                if (profile.id == id) profile.copy(wolConfig = config) else profile
            }
        }
    }

    override suspend fun add(favorite: FavoriteDevice): Long {
        return save(favorite.copy(isFavorite = true))
    }

    override suspend fun remove(id: Long) {
        delete(id)
    }

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) {
        updateLastObservedCalls += 1
        state.value = state.value.map { favorite ->
            if (favorite.id != id) favorite else favorite.copy(
                lastKnownIpv4 = observation.lastKnownIpv4,
                lastKnownDisplayName = observation.lastKnownDisplayName,
                lastKnownHostname = observation.lastKnownHostname,
                lastKnownMdnsName = observation.lastKnownMdnsName,
                lastKnownUpnpName = observation.lastKnownUpnpName,
                macAddress = observation.macAddress,
                vendor = observation.vendor,
                model = observation.model,
                lastSeenAt = observation.lastSeenAt,
                isGateway = observation.isGateway,
                isLocalDevice = observation.isLocalDevice,
            )
        }
    }

    override suspend fun findIdentityMatch(candidate: FavoriteDeviceCandidate): DeviceIdentityMatchResult =
        FavoriteIdentityMatcher.match(state.value, candidate)

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice? =
        when (val result = findIdentityMatch(candidate)) {
            is DeviceIdentityMatchResult.StrongMatch -> result.profile
            is DeviceIdentityMatchResult.WeakCompatibilityMatch -> result.profile
            is DeviceIdentityMatchResult.Conflict,
            is DeviceIdentityMatchResult.NoMatch,
            -> null
        }
}
