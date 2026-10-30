package com.networktoolbox.core.network.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/** Architectural regression: callbacks must use payloads, never synchronous platform snapshots. */
class NetworkLeaseCallbackContractTest {
    @Test fun `callback adapter does not synchronously query network properties`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "core/network/src/main").exists() }
        val source = File(root, "core/network/src/main/java/com/networktoolbox/core/network/data/AndroidNetworkLeaseProvider.kt").readText()
        val callbacks = source.substringAfter("private val callback =").substringBefore("@Synchronized fun register")
        listOf("reader.", "revalidate()", "manager.get", "manager.activeNetwork").forEach {
            assertFalse("Synchronous lookup in callback: $it", callbacks.contains(it))
        }
    }
}
