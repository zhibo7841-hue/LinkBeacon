package com.networktoolbox.recognitionqa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.feature.lanscan.domain.recognition.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only anonymous UI and already captured evidence. No scan, Wi-Fi toggle or transport invocation. */
class QaCategoryOfflineDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<RecognitionQaActivity>()
    private fun authorize() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowTask171") == "true")
        assertTrue(compose.activity.controller.offlineOnly)
        assertFalse(compose.activity.controller.wsdEnabled)
        assertEquals("com.networktoolbox.task168",compose.activity.packageName)
    }
    @Test fun anonymousListUsesUnifiedCategoriesAndSourceDetails() {
        authorize()
        val chinese=compose.activity.resources.configuration.locales[0].language=="zh"
        val controller=compose.activity.controller
        val state=controller.state.value
        assertEquals(11,state.devices.size)
        state.devices.forEach { d ->
            val id=controller.targetId(d.ipAddress)
            compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("device-$id"))
            compose.onNodeWithTag("category-$id").assertTextEquals(QaCategoryPresentation.label(state.categories.getValue(d.ipAddress),chinese=chinese))
        }
        compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("details-F01"))
        compose.onNodeWithTag("details-F01").performClick()
        compose.onNodeWithTag("category-detail-F01").assertTextEquals(QaCategoryPresentation.detail(state.categories.getValue(state.devices.first().ipAddress),chinese))
        compose.onNodeWithTag("qa-list").performScrollToNode(hasText(QaCategoryReason.WSD_COMPUTER.detail))
        compose.onNodeWithText(QaCategoryReason.WSD_COMPUTER.detail).assertExists()
    }
    @Test fun progressiveFixturesStopAndReplayNeverStartIoOrRearmBudget() {
        authorize()
        val controller=compose.activity.controller
        val prefs=compose.activity.getSharedPreferences("task170-wsd-once",0)
        val budgetBefore=prefs.all.toMap()
        val tasksBefore=QaIo.executor.taskCount
        fun first()=controller.state.value.categories.values.first().certainty
        compose.runOnIdle {controller.showOfflineFixtures(0)};assertEquals(QaCategoryCertainty.UNKNOWN,first())
        compose.runOnIdle {controller.showOfflineFixtures(1)};assertEquals(QaCategoryCertainty.POSSIBLE,first())
        compose.runOnIdle {controller.showOfflineFixtures(2)};assertEquals(QaCategoryCertainty.CONFIRMED,first())
        compose.onNodeWithTag("qa-stop").performClick()
        assertTrue(controller.state.value.categories.isEmpty())
        compose.onNodeWithTag("category-F01").assertTextEquals(if(compose.activity.resources.configuration.locales[0].language=="zh")"类型未知" else "Unknown type")
        compose.onNodeWithTag("qa-start").performClick()
        assertEquals(QaCategoryCertainty.CONFIRMED,first())
        compose.runOnIdle {controller.stop("NETWORK_CHANGED")}
        assertTrue(controller.state.value.categories.isEmpty())
        assertEquals(tasksBefore,QaIo.executor.taskCount)
        assertEquals(0,QaIo.executor.activeCount);assertTrue(QaIo.executor.queue.isEmpty())
        assertEquals(budgetBefore,prefs.all)
        assertFalse(controller.isRunning())
    }
    @Test fun capturedTask168Through170EvidenceReplaysWithoutNetwork() {
        authorize()
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val files=assets.list("").orEmpty().filter{it.matches(Regex("task(168|169|170)-api(31|36)\\.json"))}.sorted()
        assertEquals(6,files.size)
        val tasksBefore=QaIo.executor.taskCount
        val uniqueConfirmed=mutableSetOf<String>();var observations=0
        for(file in files) {
            val report=JSONObject(assets.open(file).bufferedReader().use{it.readText()})
            val records=report.getJSONArray("evidence")
            val evidence=(0 until records.length()).map { i ->
                val e=records.getJSONObject(i);val names=e.optJSONArray("names")
                val proofs=e.optJSONArray("wsdProofs")
                QaEvidence(e.getString("target"),QaSource.valueOf(e.getString("source")),e.getString("status"),
                    names=if(names==null)emptyList() else (0 until names.length()).map {n ->
                        val value=names.getJSONObject(n);PtrName(value.getString("value"),value.getLong("ttl"))},
                    software=if(e.isNull("software"))null else e.getString("software"),
                    responses=e.optInt("responses"),
                    wsdProofs=if(proofs==null)emptySet() else (0 until proofs.length()).map{WsdProof.valueOf(proofs.getString(it))}.toSet())
            }
            val devices=report.getJSONArray("devices")
            for(i in 0 until devices.length()) {
                val device=devices.getJSONObject(i);val id=device.getString("target")
                val observed=evidence.filter{it.targetId==id}
                val result=QaCategoryMapper.map(QaCategoryFixtures.current,observed)
                val expected=if(id=="T09") {
                    if(file.startsWith("task170"))QaCategoryCertainty.CONFIRMED else QaCategoryCertainty.POSSIBLE
                } else QaCategoryCertainty.UNKNOWN
                assertEquals("$file $id",expected,result.certainty)
                if(result.certainty==QaCategoryCertainty.CONFIRMED) uniqueConfirmed+=id
                if(id=="T06") assertNotNull("SSH service retained",QaProjection.service(observed))
                if(id=="T09") assertTrue("Existing name retained",QaProjection.name(null,null,observed).equals(device.getString("finalName"),true))
                observations++
            }
        }
        assertEquals(setOf("T09"),uniqueConfirmed)
        assertEquals(tasksBefore,QaIo.executor.taskCount)
        val summary=JSONObject().put("captureFiles",files.size).put("observationsReplayed",observations)
            .put("uniqueExistingConfirmedTargets",1).put("newRecognizedDevices",0).put("newNetworkRequests",0)
        File(compose.activity.filesDir,"task171/offline-summary.json").also{it.parentFile?.mkdirs()}.writeText(summary.toString(2))
    }
}
