package com.networktoolbox

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.common.favorites.*
import com.networktoolbox.core.common.wol.*
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.model.*
import com.networktoolbox.feature.lanscan.presentation.*
import com.networktoolbox.feature.lanscan.ui.DeviceDetailScreen
import com.networktoolbox.feature.lanscan.R as LanR
import dagger.hilt.android.testing.*
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Two separate instrumentation invocations, separated by force-stop: real Room, fake network only. */
@HiltAndroidTest
class Wol188DeviceTest {
    @get:Rule(order=0) val hilt=HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Inject lateinit var repository: SavedDeviceRepository
    @Inject lateinit var fixture: RecreationFixture
    private lateinit var vm: LanScannerViewModel
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("task188-evidence",0)
    private var showing by mutableStateOf(true)
    private fun observed()=LanDevice(ipAddress="192.168.50.188",isLocalDevice=false,isGateway=false,
        macAddress="02:00:00:00:01:88", discoveryMethods=emptyList(),discoveryEvidence=emptyList(),lastSeen=42)
    private fun profiles()=runBlocking { repository.observeProfiles().first() }
    private fun profile()=profiles().single { it.id==prefs.getLong("id",-1) }
    private fun route()=LanDeviceDetailRouteKey.forFavorite(profile())
    private fun str(id:Int)=compose.activity.getString(id)
    @Before fun setup() {
        hilt.inject()
        check(context.packageName=="com.networktoolbox.wifiqa")
        check(InstrumentationRegistry.getArguments().getString("task188Room")=="true")
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[LanScannerViewModel::class.java]}
    }
    private fun render() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val saved by vm.savedProfiles.collectAsState()
                val network by fixture.network.collectAsState()
                NetworkToolboxTheme {
                    if(showing) saved.firstOrNull {it.id==prefs.getLong("id",-1)}?.let {p ->
                        DeviceDetailScreen(DeviceCenterPresentation.detail(p,network),
                            {showing=false},{}, onSaveWakeOnLan={mac,port-> vm.saveWakeOnLanByRouteKey(
                                LanDeviceDetailRouteKey.forFavorite(p),mac,port)},
                            onSendWakeOnLan={vm.sendWakeOnLanByRouteKey(LanDeviceDetailRouteKey.forFavorite(p))})
                    }
                }
            }
        }
        compose.waitUntil(5000) {vm.savedProfiles.value.any {it.id==prefs.getLong("id",-1)}}
        compose.waitForIdle()
    }
    private fun edit() {
        val label=str(if(profile().wolConfig==null) LanR.string.wol_configure else LanR.string.wol_edit)
        compose.onAllNodesWithText(label).onLast().performScrollTo().performClick()
    }
    private fun saveMac(value:String,port:String?=null) {
        edit()
        compose.onNodeWithTag("wol_mac_input").performTextReplacement(value)
        if(port!=null) compose.onNodeWithTag("wol_port_input").performTextReplacement(port)
        compose.onNodeWithTag("wol_save").assertIsEnabled().performClick()
        compose.waitUntil(5000) {profile().wolConfig?.macAddress?.toString()==MacAddress.parse(value)?.toString()}
        compose.waitForIdle()
    }
    private fun assertCleared() {
        assertNull(profile().wolConfig)
        // Independent SQLite read verifies BOTH columns, not just a presentation object.
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("task188-wol.db").path,
            null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {db ->
            db.rawQuery("SELECT wol_mac_address, wol_udp_port FROM favorite_devices WHERE id = ?",
                arrayOf(profile().id.toString())).use { c ->
                assertTrue(c.moveToFirst());assertTrue(c.isNull(0));assertTrue(c.isNull(1))
            }
        }
        compose.onNodeWithText(str(LanR.string.wol_not_configured)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(LanR.string.wol_send)).assertDoesNotExist()
        compose.runOnUiThread {vm.sendWakeOnLanByRouteKey(route())}
        compose.waitForIdle()
        assertEquals(0,fixture.wakeSends)
    }
    private fun evidence(name:String) {
        val dir=File(context.getExternalFilesDir(null),"task188").apply{mkdirs()}
        File(dir,"$name.txt").writeText("pid=${android.os.Process.myPid()}\nprofile=${profile()}\n"+
            "scanStarts=${fixture.scanStarts};wakeSends=${fixture.wakeSends}\n")
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun phaseA_saveClearReenterRecreate() {
        // Reruns reuse only this explicit synthetic fixture; never delete existing data.
        val existing=profiles().firstOrNull {it.customName=="Task188 fixture"}
        val id=existing?.id ?: runBlocking {repository.save(LanFavoriteIdentity.createSavedProfile(observed(),fixture.network.value,10)!!
            .copy(customName="Task188 fixture",userDeviceType=DeviceType.SERVER,notes="Keep fixture note",
                isFavorite=true,firstSeenAt=7,lastSeenAt=9))}
        prefs.edit().putLong("id",id).putInt("phaseAPid",android.os.Process.myPid()).commit()
        prefs.edit().putLong("firstSeen",profile().firstSeenAt!!).putLong("lastSeen",profile().lastSeenAt!!).commit()
        render()
        saveMac("02:11:22:33:44:55","7")
        assertEquals(7,profile().wolConfig!!.udpPort)
        val before=profile()
        evidence("wol-valid")
        saveMac("")
        assertCleared()
        assertEquals(before.copy(wolConfig=null,updatedAt=profile().updatedAt),profile())
        evidence("wol-empty-cleared")
        compose.onNodeWithContentDescription(str(com.networktoolbox.core.designsystem.R.string.common_back))
            .performScrollTo().performClick()
        compose.runOnIdle {assertFalse(showing)}
        compose.runOnUiThread{showing=true};compose.waitForIdle()
        assertCleared();evidence("wol-reentered")
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[LanScannerViewModel::class.java]}
        render();assertCleared();evidence("wol-recreated")
        saveMac("02:11:22:33:44:66","7")
        edit()
        compose.onNodeWithTag("wol_port_input").performTextReplacement("invalid")
        compose.onNodeWithTag("wol_mac_input").performTextReplacement("   ")
        compose.onNodeWithTag("wol_save").assertIsEnabled().performClick()
        compose.waitUntil(5000){profile().wolConfig==null}
        assertCleared();evidence("wol-whitespace-cleared")
        assertEquals(before.copy(wolConfig=null,updatedAt=profile().updatedAt),profile())
        assertEquals(0,fixture.scanStarts)
    }
    @Test fun phaseB_processRestartRescanReconfigure() {
        assertNotEquals(prefs.getInt("phaseAPid",-1),android.os.Process.myPid())
        assertEquals(prefs.getLong("firstSeen",-1),profile().firstSeenAt)
        assertEquals(prefs.getLong("lastSeen",-1),profile().lastSeenAt)
        val before=profile()
        render();assertCleared();evidence("wol-process-restarted")
        edit()
        compose.onNodeWithTag("wol_mac_input").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("wol_port_input").assertTextContains("9")
        compose.onNodeWithTag("wol_mac_input").performTextReplacement("invalid")
        compose.onNodeWithTag("wol_save").assertIsNotEnabled()
        compose.onNodeWithTag("wol_mac_input").performTextClearance()
        compose.onNodeWithTag("wol_save").performClick()
        fixture.scanDevices=listOf(observed());fixture.finishScan.complete(Unit)
        compose.runOnUiThread {vm.startScan()}
        compose.waitUntil(10000){vm.uiState.value is LanScannerUiState.Completed}
        assertCleared()
        assertEquals(before.id,profile().id);assertEquals(before.macAddress,profile().macAddress)
        assertEquals(before.identityValue,profile().identityValue)
        assertEquals(before.customName,profile().customName);assertEquals(before.notes,profile().notes)
        assertEquals(before.userDeviceType,profile().userDeviceType);assertEquals(before.isFavorite,profile().isFavorite)
        assertEquals(before.firstSeenAt,profile().firstSeenAt)
        evidence("wol-after-fixture-rescan")
        saveMac("02:11:22:33:44:77")
        assertEquals(WakeOnLanConfig.DEFAULT_UDP_PORT,profile().wolConfig!!.udpPort)
        evidence("wol-new-config-default-port")
        assertEquals(1,fixture.scanStarts);assertEquals(0,fixture.wakeSends)
    }
    @After fun isolation() {
        assertEquals(0,fixture.pingStarts);assertEquals(0,fixture.diagnosticStarts)
        assertEquals(0,fixture.tlsStarts);assertEquals(0,fixture.websiteStarts);assertEquals(0,fixture.wakeSends)
    }
}
