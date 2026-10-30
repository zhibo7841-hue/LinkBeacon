package com.networktoolbox.recognitionqa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.text.DateFormat
import java.util.Date

/** Separate launcher for the authorized experiment, with no route to unconstrained production scans. */
class RecognitionQaActivity : ComponentActivity() {
    lateinit var controller: QaController
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = QaController(application) as T
        })[QaController::class.java]
        setContent { MaterialTheme {
            val state by controller.state.collectAsState()
            val chinese = LocalConfiguration.current.locales[0].language == "zh"
            Surface(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.safeDrawingPadding().padding(horizontal = 16.dp).testTag("qa-list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("LAN Recognition QA · Task ${if (controller.offlineOnly) "171" else if (controller.wsdEnabled) "170" else "169"}", style = MaterialTheme.typography.titleLarge)
                        Text(if (controller.offlineOnly) "Offline synthetic fixtures · no network requests" else if (controller.wsdEnabled) "T05 · T06 · T09 · WSD one-shot" else "T03 · T05 · T06 · T09 · remote only", style = MaterialTheme.typography.bodySmall)
                        Text(state.phase, Modifier.testTag("qa-phase"))
                        if (state.progress.isNotEmpty()) Text(state.progress)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = controller::start,
                                enabled = state.phase !in setOf("CAPTURE_NETWORK", "C0_DISCOVERY", "QA_RECOGNITION", "WSD_QA"),
                                modifier = Modifier.testTag("qa-start")) { Text(if (controller.offlineOnly) "Replay fixtures" else "Start / Rescan") }
                            TextButton(onClick = { controller.stop() }, modifier = Modifier.testTag("qa-stop")) { Text("Stop") }
                        }
                    }
                    items(state.devices, key = { it.ipAddress }) { device ->
                        val id = controller.targetId(device.ipAddress)
                        val evidence = state.evidence.filter { it.targetId == id }
                        val category = state.categories[device.ipAddress] ?: QaCategoryResult()
                        var details by remember(state.round, id) { mutableStateOf(false) }
                        OutlinedCard(Modifier.fillMaxWidth().testTag("device-$id")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(controller.name(device, state) ?: device.ipAddress,
                                    Modifier.testTag("name-$id"), style = MaterialTheme.typography.titleMedium)
                                if (controller.name(device, state) != null) Text(device.ipAddress)
                                Text(QaCategoryPresentation.label(category, chinese = chinese), Modifier.testTag("category-$id"))
                                QaProjection.service(evidence)?.let { Text(it, Modifier.testTag("service-$id")) }
                                TextButton(onClick = { details = !details }, Modifier.testTag("details-$id")) {
                                    Text(if (details) "Hide details · $id" else "Details · $id")
                                }
                                if (details) {
                                    Text(QaCategoryPresentation.detail(category, chinese), Modifier.testTag("category-detail-$id"))
                                    category.reasons.forEach { Text(it.detail, style = MaterialTheme.typography.bodySmall) }
                                    state.baselineDetails[device.ipAddress].orEmpty().forEach { Text(it) }
                                    evidence.forEach { e ->
                                        Text("${e.source}: ${e.status}", style = MaterialTheme.typography.labelLarge)
                                        e.names.forEach { n -> Text(n.value + if (e.source in setOf(QaSource.NBSTAT, QaSource.WSD)) "" else " · TTL ${n.ttl}s") }
                                        e.wsdProofs.forEach { Text("WSD / device self-report: ${it.name}", style = MaterialTheme.typography.bodySmall) }
                                        e.software?.let { Text("Server software claim: $it") }
                                        Text(if (e.source == QaSource.WSD)
                                            "Shared discovery · ${e.requests} metadata request · ${DateFormat.getTimeInstance().format(Date(e.observedAt))}"
                                            else "${e.requests} request · ${e.elapsedMs} ms · ${DateFormat.getTimeInstance().format(Date(e.observedAt))}",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Current-session observations; unauthenticated. No OS, model or durable identity inferred.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } }
    }
    override fun onStop() {
        if (::controller.isInitialized && controller.isRunning()) controller.stop("PAGE_LEFT")
        super.onStop()
    }
}
