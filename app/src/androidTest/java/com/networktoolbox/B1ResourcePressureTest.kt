package com.networktoolbox

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.lease.NetworkLease
import com.networktoolbox.core.network.model.*
import com.networktoolbox.feature.lanscan.data.AndroidLanHostEnricher
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.recognition.*
import java.net.Socket
import java.net.DatagramSocket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * No packets: production admission and private I/O scheduler, with unconnected sockets and
 * synthetic blocking/timeout functions. Reflection keeps test seams out of production.
 * Never drain an offered host into inspect(): that method would perform actual network I/O.
 */
@RunWith(AndroidJUnit4::class)
class B1ResourcePressureTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val network=NetworkContext(ConnectionType.WIFI,"192.0.2.1",null,null,emptyList(),false,null,null)
    private class Lease:NetworkLease {
        override val id="fixture-lease"
        override val policyRevision=1L
        @Volatile override var valid=true
        override fun revalidate()=valid
        override fun close() { valid=false }
    }
    private class HeldDispatcher:CoroutineDispatcher() {
        private val tasks=ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context:CoroutineContext,block:Runnable) { tasks.add(block) }
        fun drainCancelled() { while(true) (tasks.poll()?:break).run() }
    }
    private fun open(scope:CoroutineScope,current:AtomicBoolean=AtomicBoolean(true),lease:Lease=Lease()) =
        AndroidLanHostEnricher(context).open(network,1,lease,scope,{current.get()},{null},{_,_->fail("Unexpected protocol callback")})
    private fun executor():ThreadPoolExecutor {
        val c=Class.forName("com.networktoolbox.feature.lanscan.data.HostRecognitionIo")
        return c.getDeclaredMethod("getExecutor").apply { isAccessible=true }
            .invoke(c.getDeclaredField("INSTANCE").apply {isAccessible=true}.get(null)) as ThreadPoolExecutor
    }
    private fun owner(s:LanHostEnrichmentSession)=s.javaClass.getDeclaredField("owner")
        .apply {isAccessible=true}.get(s) as CoroutineScope
    private suspend fun io(s:LanHostEnrichmentSession,socket:AutoCloseable,block:(AutoCloseable)->String?):String? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            s.javaClass.declaredMethods.single { it.name=="io" }.apply {isAccessible=true}
                .invoke(s,socket,block,continuation)
        }
    private suspend fun settled(s:LanHostEnrichmentSession) {
        withTimeout(3000) { while(s.statistics.activeIo!=0 || s.statistics.resources!=0) delay(5) }
    }
    private fun device(n:Int)=LanDevice(ipAddress="192.0.2.$n",isLocalDevice=false,isGateway=false,
        discoveryMethods=emptyList(),discoveryEvidence=emptyList(),lastSeen=1)

    @Test fun boundedHostAdmissionAt10_64_128_254() {
        for(n in listOf(10,64,128,254)) {
            val dispatcher=HeldDispatcher();val scope=CoroutineScope(SupervisorJob()+dispatcher)
            val s=open(scope)
            try {
                (1..n).forEach { s.offer(device(it));s.offer(device(it)) }
                assertEquals(n,s.statistics.admitted)
                assertEquals((n-32).coerceAtLeast(0),s.statistics.deferred)
                assertEquals(0,s.statistics.nbstatRequests);assertEquals(0,s.statistics.sshConnections)
                Log.i("Task174","fixture admission n=$n deferred=${s.statistics.deferred} packetAttempts=0")
            } finally {
                s.close();scope.cancel();dispatcher.drainCancelled()
            }
            assertTrue(s.statistics.closed);assertEquals(0,s.statistics.resources)
        }
    }
    @Test fun physicalIoAndQueueStayBoundedWithSimulatedNoResponses():Unit=runBlocking {
        val pool=executor()
        val c0=com.networktoolbox.feature.lanscan.data.SystemReverseDnsExecutor.process.resources()
        assertEquals(0,c0.physicalInFlight)
        for(n in listOf(10,64,128,254)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val s=open(scope)
            val entered=CountDownLatch(2);val release=CountDownLatch(1)
            val sockets:List<AutoCloseable> = (1..n).map {
                if(it%2==0) Socket() else DatagramSocket(null as SocketAddress?)
            }
            val results=mutableListOf<Deferred<String?>>()
            try {
                fun submit(i:Int)=owner(s).async(start=CoroutineStart.UNDISPATCHED) {
                    io(s,sockets[i]) { entered.countDown();check(release.await(3,TimeUnit.SECONDS));throw SocketTimeoutException("fixture") }
                }
                results+=submit(0);results+=submit(1)
                assertTrue(entered.await(2,TimeUnit.SECONDS))
                for(i in 2 until n) results+=submit(i)
                assertTrue(pool.queue.size<=32);assertEquals(2,pool.maximumPoolSize)
                release.countDown()
                assertTrue(withTimeout(3000) { results.awaitAll() }.all {it==null})
                settled(s)
                assertTrue(sockets.all {when(it) {
                    is Socket -> it.isClosed
                    is DatagramSocket -> it.isClosed
                    else -> false
                } });assertEquals(2,s.statistics.peakIo)
                assertEquals(0,s.statistics.nbstatRequests);assertEquals(0,s.statistics.sshConnections)
                Log.i("Task174","fixture IO n=$n peak=${s.statistics.peakIo} active=0 resources=0; no-response/rejection completed")
            } finally { release.countDown();s.close();scope.cancel();sockets.forEach {it.close()} }
        }
        assertFalse(pool.isShutdown)
        assertEquals(0,com.networktoolbox.feature.lanscan.data.SystemReverseDnsExecutor.process.resources().physicalInFlight)
    }
    @Test fun stopNetworkInvalidationAndRescanDoNotReplaceBlockedWorkers():Unit=runBlocking {
        val pool=executor();val release=CountDownLatch(1);val entered=CountDownLatch(2)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val current=AtomicBoolean(true);val lease=Lease();val old=open(scope,current,lease)
        val sockets=listOf(Socket(),Socket())
        try {
            val oldJobs=sockets.map { socket -> owner(old).async(start=CoroutineStart.UNDISPATCHED) {
                io(old,socket) { entered.countDown();release.await();"late fixture value" }
            } }
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            lease.close();current.set(false);old.close();old.close()
            assertTrue(sockets.all {it.isClosed})
            assertTrue(oldJobs.all {it.isCancelled})
            val fresh=open(scope)
            val freshSocket=Socket();val freshRan=AtomicBoolean(false)
            try {
                val pending=owner(fresh).async(start=CoroutineStart.UNDISPATCHED) {
                    io(fresh,freshSocket) { freshRan.set(true);"new" }
                }
                assertFalse(freshRan.get()) // Old physical workers have not returned.
                assertEquals(2,pool.poolSize)
                fresh.close();assertTrue(pending.isCancelled);assertTrue(freshSocket.isClosed)
                release.countDown();settled(old);settled(fresh)
                assertFalse(freshRan.get());assertTrue(pool.queue.isEmpty())
                Log.i("Task174","fixture Stop/network/rescan PASS; no replacement workers or late result, sockets closed")
            } finally { fresh.close() }
        } finally { release.countDown();old.close();scope.cancel() }
    }
    @Test fun completionTailDeadlineCancelsBlockedFixtureIo():Unit=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val s=open(scope)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val socket=object:Socket() { override fun close() { super.close();release.countDown() } }
        try {
            val pending=owner(s).async(start=CoroutineStart.UNDISPATCHED) {
                io(s,socket) {entered.countDown();release.await();"late"}
            }
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            val start=android.os.SystemClock.elapsedRealtime();s.discoveryCompleted()
            withTimeout(3000) { while(!s.statistics.closed) delay(10) }
            settled(s)
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            assertTrue(elapsed>=1900);assertTrue(elapsed<3000)
            assertTrue(pending.isCancelled);assertTrue(socket.isClosed)
            Log.i("Task174","fixture completion cutoff ms=$elapsed; active=0 resources=0")
        } finally { s.close();scope.cancel() }
    }
}
