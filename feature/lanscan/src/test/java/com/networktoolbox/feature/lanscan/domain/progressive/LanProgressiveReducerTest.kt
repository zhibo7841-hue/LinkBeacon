package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource as Source
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import org.junit.Assert.*
import org.junit.Test

class LanProgressiveReducerTest {
    @Test fun `discovery admits pending host then accepts a sourced name`() {
        val f = FixtureSession()
        f.admit()
        assertEquals(LanNameStatus.PENDING, f.task().status)
        assertTrue(f.task().observations.isEmpty())
        f.start()
        f.settle()
        assertEquals("workstation.local", f.task().observations.single().value)
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
        assertEquals(Source.REVERSE_DNS, f.task().job.source)
        assertNull(f.task().observations.single().observedAt)
    }

    @Test fun `name before new progress is preserved with discovery time owned by discovery`() {
        val f = FixtureSession()
        f.frame(discovery())
        f.start()
        f.settle()
        val original = f.task()
        f.frame(discovery(latency = 25), discovery(OTHER), scanned = 3)
        assertEquals(original, f.task())
        assertEquals(25L, f.state.hosts.getValue(IP).discovery.latencyMs)
        assertEquals(1_400L, f.state.hosts.getValue(IP).discovery.lastSeen)
        assertEquals(2, f.state.hosts.size)
    }

    @Test fun `coalesced full frame discovers every host not merely the last addition`() {
        val f = FixtureSession()
        val first = LanProgressiveEvent.DiscoveryFrame(f.token, 1, 1, listOf(discovery()))
        val latest = LanProgressiveEvent.DiscoveryFrame(f.token, 3, 3,
            listOf(discovery(), discovery(OTHER), discovery("192.168.1.42")))
        val coalesced = LanNameAdmission.coalesce(first, latest)
        assertTrue(f.send(coalesced).accepted)
        assertEquals(f.state.allowedAddresses, f.state.hosts.keys)
        assertEquals(3, LanNameAdmission.readyCandidates(f.state).size)
    }

    @Test fun `critical name and source events survive between coalesced progress frames`() {
        val f = FixtureSession()
        f.frame(discovery())
        f.start()
        val later = LanProgressiveEvent.DiscoveryFrame(f.token, 3, 3, listOf(discovery(), discovery(OTHER)))
        val slot = LanNameAdmission.coalesce(
            LanProgressiveEvent.DiscoveryFrame(f.token, 2, 2, listOf(discovery())), later,
        )
        f.settle()
        f.send(LanProgressiveEvent.SourceSettled(f.token, Source.REVERSE_DNS, LanSourceStatus.SETTLED))
        f.send(slot)
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
        assertEquals(LanNameStatus.DEFERRED, f.task(OTHER).status)
        assertEquals(LanSourceStatus.SETTLED, f.state.sources[Source.REVERSE_DNS])
    }

    @Test fun `same generation role latency and discovery updates do not invalidate admitted job`() {
        val f = FixtureSession()
        f.admit()
        val job = f.start()
        val before = f.state.hosts.getValue(IP)
        f.send(LanProgressiveEvent.HostAdmitted(f.token, ++f.sequence,
            discovery(latency = 30).copy(isGateway = true, lastSeen = 1_600)))
        assertEquals(job.admissionRevision, f.state.hosts.getValue(IP).admissionRevision)
        assertTrue(f.state.hosts.getValue(IP).hostRevision > before.hostRevision)
        assertTrue(f.settle(job).accepted)
        assertEquals(1_600L, f.state.hosts.getValue(IP).discovery.lastSeen)
    }

    @Test fun `other host can arrive out of sequence without being lost`() {
        val f = FixtureSession()
        f.send(LanProgressiveEvent.HostAdmitted(f.token, 3, discovery()))
        assertTrue(f.send(LanProgressiveEvent.HostAdmitted(f.token, 2, discovery(OTHER))).accepted)
        assertEquals(setOf(IP, OTHER), f.state.hosts.keys)
    }

    @Test fun `older complete frame cannot regress facts or counts`() {
        val f = FixtureSession()
        f.send(LanProgressiveEvent.DiscoveryFrame(f.token, 4, 3, listOf(discovery(latency = 40))))
        val before = f.state
        assertFalse(f.send(LanProgressiveEvent.DiscoveryFrame(f.token, 2, 1, listOf(discovery()))).accepted)
        assertEquals(before, f.state)
    }

    @Test fun `newer frame lower scanned count does not regress progress`() {
        val f = FixtureSession()
        f.frame(discovery(), scanned = 3)
        f.frame(discovery(latency = 22), scanned = 2)
        assertEquals(3, f.state.scannedHosts)
        assertEquals(22L, f.state.hosts.getValue(IP).discovery.latencyMs)
    }

    @Test fun `incomplete cumulative or duplicate or outside range frames are rejected atomically`() {
        val f = FixtureSession()
        f.frame(discovery(), discovery(OTHER))
        val before = f.state
        for (values in listOf(listOf(discovery()), listOf(discovery(), discovery()),
            listOf(discovery("192.168.1.200")) )) {
            assertFalse(f.send(LanProgressiveEvent.DiscoveryFrame(f.token, 20, 3, values)).accepted)
            assertEquals(before, f.state)
        }
    }

    @Test fun `a name cannot create a host or a new online evidence method`() {
        val f = FixtureSession()
        val ghost = LanNameJob(f.token, IP, Source.REVERSE_DNS, 1)
        assertFalse(f.send(LanProgressiveEvent.NameScheduled(ghost)).accepted)
        assertFalse(f.observe(ghost).accepted)
        assertFalse(f.settle(ghost).accepted)
        assertTrue(f.state.hosts.isEmpty())
        f.admit()
        f.start()
        val raw = f.state.hosts.getValue(IP).discovery
        f.settle()
        assertEquals(raw, f.state.hosts.getValue(IP).discovery)
        assertEquals(listOf(LanDiscoveryMethod.REACHABILITY), raw.methods)
    }

    @Test fun `wrong generation lease fingerprint and policy reject results independently`() {
        val f = FixtureSession()
        f.admit()
        val job = f.start()
        val before = f.state
        for (token in listOf(f.token.copy(generation = 8), f.token.copy(networkLeaseId = "other"),
            f.token.copy(fingerprint = "other"), f.token.copy(policyRevision = 2))) {
            assertFalse(f.settle(job.copy(token = token)).accepted)
            assertFalse(f.send(LanProgressiveEvent.Stop(token)).accepted)
            assertFalse(f.send(LanProgressiveEvent.DiscoveryFrame(token, 10, 1, listOf(discovery()))).accepted)
            assertEquals(before, f.state)
        }
    }

    @Test fun `wrong host source or admission revision cannot reuse a job`() {
        val f = FixtureSession()
        f.admit()
        f.admit(OTHER)
        val job = f.start()
        for (wrong in listOf(job.copy(ipAddress = OTHER), job.copy(source = Source.MDNS),
            job.copy(admissionRevision = job.admissionRevision + 1))) {
            assertFalse(f.settle(wrong).accepted)
        }
        assertTrue(f.task(OTHER).observations.isEmpty())
    }

    @Test fun `same IP across network leases and generations remains isolated`() {
        val a = FixtureSession()
        val b = FixtureSession(TOKEN.copy(generation = 8, networkLeaseId = "fixture-lease-B"))
        a.admit(); b.admit()
        val old = a.start()
        val fresh = b.start()
        assertFalse(b.observe(old, "old-network-name").accepted)
        assertTrue(b.observe(fresh, "new-network-name").accepted)
        assertEquals(listOf("new-network-name"), b.task().observations.map { it.value })
        assertTrue(a.task().observations.isEmpty())
    }

    @Test fun `two valid sources and distinct same source candidates preserve provenance`() {
        val f = FixtureSession()
        f.admit()
        val ptr = f.start()
        f.observe(ptr, "ptr-alias")
        f.observe(ptr, "another-alias")
        f.enableShared()
        val mdns = f.start(source = Source.MDNS)
        f.observe(mdns, "mDNS device name")
        val upnp = f.start(source = Source.UPNP)
        f.observe(upnp, "UPnP device name")
        assertEquals(3, f.state.hosts.getValue(IP).names.size)
        assertEquals(setOf("ptr-alias", "another-alias"), f.task().observations.map { it.value }.toSet())
        assertEquals("mDNS device name", f.task(source = Source.MDNS).observations.single().value)
        assertTrue(f.settle(ptr).accepted) // Other sources did not invalidate its admission.
    }

    @Test fun `no data from one source cannot erase another source name`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.settle(); f.enableShared()
        val mdns = f.start(source = Source.MDNS)
        assertTrue(f.settle(mdns, LanNameStatus.NO_DATA).accepted)
        assertEquals("workstation.local", f.task().observations.single().value)
        assertEquals(LanNameStatus.NO_DATA, f.task(source = Source.MDNS).status)
    }

    @Test fun `duplicate observation and duplicate or contradictory final terminal are idempotent`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.observe()
        val beforeDuplicate = f.state
        assertFalse(f.observe().accepted)
        assertEquals(beforeDuplicate, f.state)
        assertTrue(f.settle().accepted)
        val final = f.state
        assertFalse(f.settle().accepted)
        assertFalse(f.settle(outcome = LanNameStatus.NO_DATA).accepted)
        assertFalse(f.settle(outcome = LanNameStatus.TIMED_OUT).accepted)
        assertFalse(f.observe(value = "late contradictory name").accepted)
        assertEquals(final, f.state)
    }

    @Test fun `complete terminal before incremental event loses neither names nor final status`() {
        val f = FixtureSession()
        f.admit(); val job = f.start()
        val payload = listOf(name("alias-a"), name("alias-b"))
        assertTrue(f.send(LanProgressiveEvent.NameSettled(job, LanNameStatus.RESOLVED, payload)).accepted)
        assertFalse(f.observe(job, "alias-a").accepted)
        assertEquals(payload, f.task().observations)
    }

    @Test fun `pending and deferred were never executed and cannot be no data`() {
        val f = FixtureSession()
        f.admit()
        assertFalse(f.settle(outcome = LanNameStatus.NO_DATA).accepted)
        assertTrue(f.settle(outcome = LanNameStatus.DEFERRED).accepted)
        assertNull(f.task().startedAt)
        assertEquals(0, f.state.physicalInFlight)
        assertFalse(f.settle().accepted)
    }

    @Test fun `visible timeout can be replaced by valid late success without releasing resource`() {
        val f = FixtureSession()
        f.admit(); val job = f.start()
        f.clock += LanNameContractCandidates.VISIBLE_DEADLINE_MS
        assertEquals(2_500L, f.clock)
        assertTrue(f.settle(outcome = LanNameStatus.TIMED_OUT).accepted)
        assertEquals(1, f.state.physicalInFlight)
        assertEquals(1, f.budget.active.size)
        val late = name().copy(completedAt = f.clock + 50)
        assertTrue(f.send(LanProgressiveEvent.NameSettled(job, LanNameStatus.RESOLVED, listOf(late))).accepted)
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
        assertEquals(1, f.state.physicalInFlight)
    }

    @Test fun `no data needs an actual start and resolved needs a valid payload`() {
        val f = FixtureSession()
        f.admit(); f.start()
        assertFalse(f.send(LanProgressiveEvent.NameSettled(f.job(), LanNameStatus.RESOLVED)).accepted)
        assertTrue(f.settle(outcome = LanNameStatus.NO_DATA).accepted)
        assertTrue(f.task().observations.isEmpty())
        assertEquals(1, f.state.physicalInFlight)
    }

    @Test fun `source unknown keeps unexecuted deferred and started unknown not no data`() {
        val f = FixtureSession()
        f.admit(); f.admit(OTHER); f.start()
        assertTrue(f.send(LanProgressiveEvent.SourceSettled(f.token, Source.REVERSE_DNS, LanSourceStatus.UNKNOWN)).accepted)
        assertEquals(LanNameStatus.UNKNOWN, f.task().status)
        assertEquals(LanNameStatus.DEFERRED, f.task(OTHER).status)
        assertEquals(1, f.state.physicalInFlight)
        assertFalse(f.settle().accepted)
    }

    @Test fun `source finalization duplicated or delivered before source start is rejected`() {
        val f = FixtureSession()
        assertFalse(f.send(LanProgressiveEvent.SourceSettled(f.token, Source.MDNS, LanSourceStatus.SETTLED)).accepted)
        val event = LanProgressiveEvent.SourceSettled(f.token, Source.REVERSE_DNS, LanSourceStatus.SETTLED)
        assertTrue(f.send(event).accepted)
        val final = f.state
        assertFalse(f.send(event).accepted)
        assertEquals(final, f.state)
    }

    @Test fun `bounded candidate conflicts retain deterministic prefix and explicit limitation`() {
        fun result(reverse: Boolean): LanNameTask {
            val f = FixtureSession()
            f.admit(); val job = f.start()
            val batches = listOf(listOf(name("f"), name("e"), name("d")),
                listOf(name("c"), name("b"), name("a")))
            (if (reverse) batches.reversed() else batches).forEach {
                assertTrue(f.send(LanProgressiveEvent.NameObserved(job, it)).accepted)
            }
            return f.task()
        }
        val a = result(false); val b = result(true)
        assertEquals(a.observations, b.observations)
        assertEquals(listOf("a", "b", "c", "d"), a.observations.map { it.value })
        assertTrue(a.outputLimited)
    }

    @Test fun `invalid field time unsafe text or oversized event fails without corrupting earlier evidence`() {
        val f = FixtureSession()
        f.admit(); val job = f.start(); f.observe(value = "safe")
        val before = f.state
        val invalid = listOf(name("bad\u202Etext"), name("x".repeat(256)), name("").copy(value = " "),
            name().copy(field = LanNameField.FRIENDLY_NAME), name().copy(observedAt = 1_050),
            name().copy(completedAt = 999))
        invalid.forEach {
            assertFalse(f.send(LanProgressiveEvent.NameObserved(job, listOf(it))).accepted)
            assertEquals(before, f.state)
        }
        assertFalse(f.send(LanProgressiveEvent.NameObserved(job, List(5) { name("$it") })).accepted)
    }

    @Test fun `raw names in a discovery device cannot enter the recognition store`() {
        val device = com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device()
            .copy(hostName = "not-discovery-owned", macAddress = "02:11:22:33:44:55")
        val f = FixtureSession()
        f.frame(LanDiscoveryObservation.from(device))
        assertTrue(f.task().observations.isEmpty())
        assertFalse(f.state.hosts.toString().contains("not-discovery-owned"))
        assertEquals(device.macAddress, f.state.hosts.getValue(IP).discovery.macAddress)
    }

    @Test fun `repeated candidate with different completion times cannot crowd out distinct names`() {
        val f = FixtureSession()
        f.admit(); val job = f.start()
        (0..5).forEach {
            f.send(LanProgressiveEvent.NameObserved(job, listOf(name("alias").copy(completedAt = 1_100L + it))))
        }
        f.observe(job, "other-alias")
        assertEquals(2, f.task().observations.size)
        assertEquals(1_100L, f.task().observations.first().completedAt)
        assertFalse(f.task().outputLimited)
    }
}
