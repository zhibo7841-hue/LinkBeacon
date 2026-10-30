package com.networktoolbox.recognitionqa

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in only. All network targets come from the private packaged allowlist. */
class RecognitionQaDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<RecognitionQaActivity>()
    private fun authorize() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowTask169") == "true")
        assumeTrue(!compose.activity.controller.offlineOnly)
        assertEquals("com.networktoolbox.task168", compose.activity.packageName)
    }
    private fun waitPhase(phase: String, timeout: Long = 35000) = compose.waitUntil(timeout) {
        compose.activity.controller.state.value.phase == phase
    }
    private fun snapshot(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(compose.activity.filesDir, "task169/ui-$label.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    @Test fun fullRoundListDetailsAndEvidenceBoundaries() {
        authorize()
        compose.onNodeWithTag("qa-start").performClick(); waitPhase("COMPLETED")
        val controller = compose.activity.controller; val state = controller.state.value
        assertTrue(state.devices.size in 1..4); assertTrue(state.peak <= 2)
        assertEquals(state.evidence.size, state.evidence.map { it.targetId to it.source }.distinct().size)
        assertTrue(state.evidence.all { it.requests <= 1 })
        assertEquals(state.devices.map { it.ipAddress }.toSet(), state.categories.keys)
        compose.onNodeWithTag("qa-phase").assertTextEquals("COMPLETED")
        snapshot("list")
        state.devices.forEach { d ->
            val id = controller.targetId(d.ipAddress)
            compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("device-$id"))
            compose.onNodeWithTag("name-$id").assertExists()
            val name = controller.name(d, state) ?: d.ipAddress
            assertTrue(compose.onNodeWithTag("name-$id").fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .any { it.text == name })
            val category = state.categories.getValue(d.ipAddress)
            val chinese = compose.activity.resources.configuration.locales[0].language == "zh"
            val label = QaCategoryPresentation.label(category, chinese = chinese)
            if (label == null) compose.onNodeWithTag("category-$id").assertDoesNotExist()
            else compose.onNodeWithTag("category-$id").assertTextEquals(label)
            if (category.certainty == QaCategoryCertainty.POSSIBLE) {
                assertTrue(label.startsWith(if (chinese) "可能是" else "Possible "))
            }
            compose.onNodeWithTag("details-$id").performClick()
            compose.onNodeWithTag("category-detail-$id").assertTextEquals(QaCategoryPresentation.detail(category, chinese))
            compose.onAllNodesWithText("Current-session observations; unauthenticated. No OS, model or durable identity inferred.")
                .onFirst().assertExists()
            snapshot("detail-$id")
            state.evidence.filter { it.targetId == id }.forEach { e ->
                compose.onNodeWithTag("qa-list").performScrollToNode(hasText("${e.source}: ${e.status}"))
                compose.onAllNodesWithText("${e.source}: ${e.status}").onFirst().assertExists()
                e.names.forEach { n ->
                    val text = n.value + if (e.source == QaSource.NBSTAT) "" else " · TTL ${n.ttl}s"
                    compose.onNodeWithTag("qa-list").performScrollToNode(hasText(text))
                    compose.onAllNodesWithText(text).onFirst().assertExists()
                }
                e.software?.let { software ->
                    val text = "Server software claim: $software"
                    compose.onNodeWithTag("qa-list").performScrollToNode(hasText(text))
                    compose.onNodeWithText(text).assertExists()
                }
            }
            compose.onNodeWithTag("qa-list").performScrollToNode(hasTestTag("details-$id"))
            compose.onNodeWithTag("details-$id").performClick()
        }
    }
    @Test fun stopDuringRecognitionThenRescan() {
        authorize()
        compose.onNodeWithTag("qa-start").performClick(); waitPhase("QA_RECOGNITION")
        compose.onNodeWithTag("qa-stop").performClick(); waitPhase("STOPPED")
        val stopped = compose.activity.controller.state.value
        assertTrue(stopped.categories.isEmpty())
        Thread.sleep(1200)
        assertEquals(stopped, compose.activity.controller.state.value)
        compose.onNodeWithTag("qa-start").performClick(); waitPhase("COMPLETED")
        assertTrue(compose.activity.controller.state.value.round > stopped.round)
        assertTrue(compose.activity.controller.state.value.peak <= 2)
        snapshot("rescan")
    }
    @Test fun actualWifiLossStopsOwnerAndRestoresWifi() {
        authorize()
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowWifiToggle") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val manager = compose.activity.getSystemService(ConnectivityManager::class.java)
        fun wifi() = manager.activeNetwork?.let { manager.getNetworkCapabilities(it)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } == true
        assertTrue(wifi())
        compose.onNodeWithTag("qa-start").performClick(); waitPhase("QA_RECOGNITION")
        try {
            automation.executeShellCommand("svc wifi disable").close()
            waitPhase("NETWORK_CHANGED", 10000)
            val stopped = compose.activity.controller.state.value
            assertTrue(stopped.categories.isEmpty())
            Thread.sleep(1200); assertEquals(stopped, compose.activity.controller.state.value)
            snapshot("network-changed")
        } finally { automation.executeShellCommand("svc wifi enable").close() }
        compose.waitUntil(20000) { wifi() }
    }
}
