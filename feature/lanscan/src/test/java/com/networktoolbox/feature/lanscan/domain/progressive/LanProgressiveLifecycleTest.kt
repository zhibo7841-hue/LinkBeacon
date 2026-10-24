package com.networktoolbox.feature.lanscan.domain.progressive

import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource as Source
import org.junit.Assert.*
import org.junit.Test

class LanProgressiveLifecycleTest {
    @Test fun `stop retains a frozen observation but rejects every new result and request`() {
        val f = FixtureSession()
        f.admit(); val job = f.start(); f.observe()
        assertTrue(f.send(LanProgressiveEvent.Stop(f.token)).accepted)
        val stopped = f.state
        assertEquals(LanAddressPhase.CANCELLED, stopped.addressPhase)
        assertEquals(1, stopped.hosts.size)
        assertEquals("workstation.local", f.task().observations.single().value)
        assertFalse(f.observe(job, "late").accepted)
        assertFalse(f.settle(job).accepted)
        assertFalse(f.send(LanProgressiveEvent.NameScheduled(job)).accepted)
        assertFalse(f.send(LanProgressiveEvent.HostAdmitted(f.token, 10, discovery(OTHER))).accepted)
        assertFalse(f.frame(discovery(), discovery(OTHER)).accepted)
        assertFalse(f.acceptAddress().accepted)
        assertTrue(LanNameAdmission.readyCandidates(f.state).isEmpty())
        assertEquals(stopped, f.state)
    }

    @Test fun `repeat stop is idempotent but physical cleanup still runs exactly once`() {
        val f = FixtureSession()
        f.admit(); f.start()
        val permit = f.task().permit!!
        f.send(LanProgressiveEvent.Stop(f.token))
        assertFalse(f.send(LanProgressiveEvent.Stop(f.token)).accepted)
        assertEquals(1, f.state.physicalInFlight)
        assertEquals(1, f.budget.active.size)
        val cleanup = f.send(LanProgressiveEvent.PhysicalNameFinished(permit))
        assertEquals(listOf(LanProgressiveEffect.ReleaseNamePermit(permit)), cleanup.effects)
        assertEquals(0, f.state.physicalInFlight)
        assertEquals(0, f.budget.active.size)
        assertFalse(f.send(LanProgressiveEvent.PhysicalNameFinished(permit)).accepted)
    }

    @Test fun `wrong permit cannot falsely free a running call`() {
        val f = FixtureSession()
        f.admit(); f.start()
        val permit = f.task().permit!!
        assertFalse(f.send(LanProgressiveEvent.PhysicalNameFinished(permit.copy(id = 99))).accepted)
        assertEquals(1, f.state.physicalInFlight)
        assertEquals(1, f.budget.active.size)
    }

    @Test fun `timeout then stop rejects late success but not real return`() {
        val f = FixtureSession()
        f.admit(); f.start()
        val permit = f.task().permit!!
        f.settle(outcome = LanNameStatus.TIMED_OUT)
        f.send(LanProgressiveEvent.Stop(f.token))
        assertFalse(f.settle().accepted)
        assertEquals(LanNameStatus.TIMED_OUT, f.task().status)
        assertEquals(1, f.state.physicalInFlight)
        assertTrue(f.send(LanProgressiveEvent.PhysicalNameFinished(permit)).accepted)
    }

    @Test fun `network invalidation closes results and does not launch on another network`() {
        val f = FixtureSession()
        f.admit(); val job = f.start()
        assertTrue(f.send(LanProgressiveEvent.NetworkInvalidated(f.token)).accepted)
        assertEquals(LanAddressPhase.NETWORK_INVALIDATED, f.state.addressPhase)
        assertFalse(f.observe(job).accepted)
        assertFalse(f.send(LanProgressiveEvent.NetworkInvalidated(f.token)).accepted)
        assertTrue(LanNameAdmission.readyCandidates(f.state).isEmpty())
        assertEquals(1, f.state.physicalInFlight)
    }

    @Test fun `address running completed and logical supplement settled are separate`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.settle(outcome = LanNameStatus.TIMED_OUT)
        assertEquals(LanAddressPhase.RUNNING, f.state.addressPhase)
        assertFalse(f.state.logicallySettled)
        f.enableShared()
        assertEquals(LanAddressPhase.COMPLETED, f.state.addressPhase)
        assertFalse(f.state.logicallySettled)
        Source.entries.forEach {
            f.send(LanProgressiveEvent.SourceSettled(f.token, it, LanSourceStatus.SETTLED))
        }
        assertTrue(f.state.logicallySettled)
        assertEquals(LanNameStatus.TIMED_OUT, f.task().status)
        assertEquals(1, f.state.physicalInFlight) // Logical completion is not physical quiescence.
    }

    @Test fun `address acceptance includes last undispatched host even if progress slot was never consumed`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.settle()
        val snapshot = f.completedSnapshot().copy(discoveredDevices = listOf(
            com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device(),
            com.networktoolbox.feature.lanscan.domain.recognition.LanRecognitionFixtures.device().copy(ipAddress = OTHER),
        ))
        assertTrue(f.send(LanProgressiveEvent.AddressAccepted(f.token, 10, snapshot)).accepted)
        assertEquals(setOf(IP, OTHER), f.state.hosts.keys)
        assertEquals(LanNameStatus.PENDING, f.task(OTHER).status)
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
        assertSame(snapshot, f.state.addressSnapshot)
        val final = f.state
        assertFalse(f.send(LanProgressiveEvent.AddressAccepted(f.token, 10, snapshot)).accepted)
        assertFalse(f.frame(discovery()).accepted)
        assertEquals(final, f.state)
    }

    @Test fun `invalid address terminal cannot issue baseline sync or pretend to save history`() {
        val f = FixtureSession()
        f.admit()
        val snapshot = f.completedSnapshot()
        for (bad in listOf(snapshot.copy(sessionId = 99), snapshot.copy(networkFingerprint = "other"),
            snapshot.copy(scannedHosts = 1), snapshot.copy(totalHosts = 9))) {
            val result = f.send(LanProgressiveEvent.AddressAccepted(f.token, 5, bad))
            assertFalse(result.accepted)
            assertTrue(result.effects.isEmpty())
            assertNull(f.state.addressSnapshot)
        }
    }

    @Test fun `shared services cannot start before original baseline profile gate`() {
        val f = FixtureSession()
        f.admit()
        val mdnsJob = f.job(source = Source.MDNS)
        assertFalse(f.send(LanProgressiveEvent.NameScheduled(mdnsJob)).accepted)
        assertFalse(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
        val accepted = f.acceptAddress()
        val sync = accepted.effects.single()
        assertTrue(sync is LanProgressiveEffect.SyncBaselineProfile)
        assertTrue(LanNameAdmission.mayExecute(f.state, sync))
        assertFalse(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
        val finished = f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, true))
        val launch = finished.effects.single()
        assertTrue(LanNameAdmission.mayExecute(f.state, launch))
        assertFalse(f.send(LanProgressiveEvent.NameScheduled(mdnsJob)).accepted)
        assertTrue(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
        assertFalse(LanNameAdmission.mayExecute(f.state, launch))
        assertTrue(f.send(LanProgressiveEvent.NameScheduled(mdnsJob)).accepted)
        assertFalse(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
    }

    @Test fun `stop during profile await prevents resumed sync from launching any shared work`() {
        val f = FixtureSession()
        f.admit()
        val sync = f.acceptAddress().effects.single()
        f.send(LanProgressiveEvent.Stop(f.token))
        assertFalse(LanNameAdmission.mayExecute(f.state, sync))
        val resume = f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, true))
        assertFalse(resume.accepted)
        assertTrue(resume.effects.isEmpty())
        assertEquals(LanBaselineSync.CANCELLED, f.state.baselineSync)
        assertEquals(LanAddressPhase.COMPLETED, f.state.addressPhase)
        assertNotNull(f.state.addressSnapshot) // Address-only History meaning is retained.
    }

    @Test fun `network lost during profile await prevents resumed or cached launch effects`() {
        val f = FixtureSession()
        f.admit(); f.acceptAddress()
        val cached = f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, true)).effects.single()
        f.send(LanProgressiveEvent.NetworkInvalidated(f.token))
        assertFalse(LanNameAdmission.mayExecute(f.state, cached))
        assertFalse(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
        assertFalse(f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, true)).accepted)
    }

    @Test fun `profile failure marks unknown source not no data and emits no launch`() {
        val f = FixtureSession()
        f.admit(); f.acceptAddress()
        val failed = f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, false))
        assertTrue(failed.effects.isEmpty())
        assertEquals(LanBaselineSync.FAILED, f.state.baselineSync)
        assertEquals(LanSourceStatus.UNKNOWN, f.state.sources[Source.MDNS])
        assertFalse(f.send(LanProgressiveEvent.BaselineProfileSyncFinished(f.token, true)).accepted)
        assertFalse(f.send(LanProgressiveEvent.SharedSourcesStarted(f.token)).accepted)
    }

    @Test fun `old profile completion cannot start sources in next generation`() {
        val old = FixtureSession()
        old.admit(); old.acceptAddress()
        val next = FixtureSession(TOKEN.copy(generation = 8))
        next.admit(); next.acceptAddress()
        assertFalse(next.send(LanProgressiveEvent.BaselineProfileSyncFinished(old.token, true)).accepted)
        assertEquals(LanBaselineSync.PENDING, next.state.baselineSync)
    }

    @Test fun `source and result from three simultaneous stopped sources are all ignored`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.enableShared()
        f.start(source = Source.MDNS); f.start(source = Source.UPNP)
        f.send(LanProgressiveEvent.Stop(f.token))
        val stopped = f.state
        Source.entries.forEach {
            assertFalse(f.observe(f.job(source = it)).accepted)
            assertFalse(f.send(LanProgressiveEvent.SourceSettled(f.token, it, LanSourceStatus.SETTLED)).accepted)
        }
        assertEquals(stopped, f.state)
    }

    @Test fun `254 pending hosts use at most 32 ready entries without dropping devices`() {
        val ips = (1..254).map { "192.168.1.$it" }.toSet()
        val f = FixtureSession(addresses = ips)
        f.frame(*ips.map(::discovery).toTypedArray(), scanned = 254)
        val ready = LanNameAdmission.readyCandidates(f.state)
        assertEquals(32, ready.size)
        ready.forEach { assertTrue(f.send(LanProgressiveEvent.NameScheduled(it)).accepted) }
        assertEquals(254, f.state.hosts.size)
        assertEquals(222, f.state.hosts.values.count { it.names.getValue(Source.REVERSE_DNS).status == LanNameStatus.PENDING })
        assertTrue(LanNameAdmission.readyCandidates(f.state).isEmpty())
        val waiting = f.state.hosts.values.first { it.names.getValue(Source.REVERSE_DNS).status == LanNameStatus.PENDING }
        assertFalse(f.send(LanProgressiveEvent.NameScheduled(waiting.names.getValue(Source.REVERSE_DNS).job)).accepted)
        assertEquals(0, f.budget.active.size)
    }

    @Test fun `ready queue slot is refilled in admission order after start`() {
        val ips = (1..40).map { "192.168.1.$it" }.toSet()
        val f = FixtureSession(addresses = ips)
        f.frame(*ips.map(::discovery).toTypedArray(), scanned = 40)
        val ready = LanNameAdmission.readyCandidates(f.state)
        ready.forEach { f.send(LanProgressiveEvent.NameScheduled(it)) }
        val permit = f.budget.tryAcquire(ready.first())!!
        assertTrue(f.send(LanProgressiveEvent.NameStarted(ready.first(), f.clock, permit)).accepted)
        assertEquals(listOf("192.168.1.33"), LanNameAdmission.readyCandidates(f.state).map { it.ipAddress })
    }

    @Test fun `four blocked old generation calls remain within shared process budget after stop and timeout`() {
        val ips = (1..5).map { "192.168.1.$it" }.toSet()
        val budget = FakeProcessBudget()
        val old = FixtureSession(addresses = ips, budget = budget)
        old.frame(*ips.map(::discovery).toTypedArray(), scanned = 5)
        ips.take(4).forEach {
            old.start(it)
            old.settle(old.job(it), LanNameStatus.TIMED_OUT)
        }
        val oldPermits = old.state.hosts.values.mapNotNull { it.names[Source.REVERSE_DNS]?.permit }
        old.send(LanProgressiveEvent.Stop(old.token))
        val next = FixtureSession(TOKEN.copy(generation = 8), budget = budget)
        next.admit()
        assertTrue(next.send(LanProgressiveEvent.NameScheduled(next.job())).accepted)
        assertNull(budget.tryAcquire(next.job()))
        assertEquals(4, budget.active.size)
        assertFalse(next.observe(old.job(ips.first()), "old-name").accepted)
        old.send(LanProgressiveEvent.PhysicalNameFinished(oldPermits.first()))
        val newPermit = budget.tryAcquire(next.job())!!
        assertTrue(next.send(LanProgressiveEvent.NameStarted(next.job(), next.clock, newPermit)).accepted)
        assertEquals(4, budget.active.size)
        assertEquals(4, budget.peak)
        assertEquals(3, old.state.physicalInFlight)
        assertEquals(1, next.state.physicalInFlight)
    }

    @Test fun `never launched permit is safely returned if stop wins admission race`() {
        val f = FixtureSession()
        f.admit()
        val job = f.job()
        f.send(LanProgressiveEvent.NameScheduled(job))
        val acquired = f.budget.tryAcquire(job)!!
        f.send(LanProgressiveEvent.Stop(f.token))
        assertFalse(f.send(LanProgressiveEvent.NameStarted(job, f.clock, acquired)).accepted)
        f.budget.release(acquired) // Future coordinator's mandatory rejected-admission cleanup.
        assertEquals(0, f.budget.active.size)
        assertNull(f.task().startedAt)
    }

    @Test fun `physical return before result delivery does not lose reliable final result`() {
        val f = FixtureSession()
        f.admit(); f.start()
        f.send(LanProgressiveEvent.PhysicalNameFinished(f.task().permit!!))
        assertEquals(0, f.state.physicalInFlight)
        assertTrue(f.settle().accepted)
        assertEquals(LanNameStatus.RESOLVED, f.task().status)
    }

    @Test fun `coalescing cannot mix tokens even when fingerprints are equal`() {
        val frame = LanProgressiveEvent.DiscoveryFrame(TOKEN, 1, 1, listOf(discovery()))
        assertThrows(IllegalArgumentException::class.java) {
            LanNameAdmission.coalesce(frame, frame.copy(token = TOKEN.copy(networkLeaseId = "B"), sequence = 2))
        }
        assertSame(frame, LanNameAdmission.coalesce(frame, frame.copy(sequence = 0)))
    }

    @Test fun `coalescing rejects accidental delta frames rather than silently losing an IP`() {
        val first = LanProgressiveEvent.DiscoveryFrame(TOKEN, 1, 1, listOf(discovery()))
        val delta = first.copy(sequence = 2, discoveries = listOf(discovery(OTHER)))
        assertThrows(IllegalArgumentException::class.java) { LanNameAdmission.coalesce(first, delta) }
    }

    @Test fun `a stopped snapshot can be explicitly invalidated without accepting new names`() {
        val f = FixtureSession()
        f.admit(); f.start(); f.observe()
        f.send(LanProgressiveEvent.Stop(f.token))
        assertTrue(f.send(LanProgressiveEvent.NetworkInvalidated(f.token)).accepted)
        assertEquals(LanAddressPhase.NETWORK_INVALIDATED, f.state.addressPhase)
        assertFalse(f.observe(value = "late").accepted)
        assertEquals(1, f.state.physicalInFlight)
    }
}
