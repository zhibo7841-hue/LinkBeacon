package com.networktoolbox

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.networktoolbox.core.network.data.AndroidNetworkLeaseProvider
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import com.networktoolbox.nbstat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Explicit single-protocol opt-in. Private output must be redirected to build/task162 only. */
class NbstatDeviceExperimentTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private fun log(data:JSONObject) { ins.sendStatus(2,Bundle().apply {putString("stream","NBSTAT162 $data\n")}) }
    @Test fun nativeReceiveCancellationAndLeaseLossCloseSocketWithoutPackets()=runBlocking {
        require(ins.targetContext.packageName=="com.networktoolbox.wifiqa")
        for(cancel in listOf(true,false)) {
            val executor=Executors.newSingleThreadExecutor()
            val socket=DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(),0))
            val entered=java.util.concurrent.CountDownLatch(1)
            val valid=java.util.concurrent.atomic.AtomicBoolean(true)
            val transport=object:NbstatChannel {
                override fun send(bytes:ByteArray) { /* No packet: test only native receive cleanup. */ }
                override fun receive(windowMs:Int):NbstatDatagram {
                    socket.soTimeout=windowMs;entered.countDown()
                    val p=DatagramPacket(ByteArray(8),8);socket.receive(p)
                    error("Unexpected loopback input")
                }
                override fun close(){socket.close()}
            }
            try {
                val job=async{NbstatExchange(executor).query(transport,"192.0.2.1",1,1000,{valid.get()})}
                yield();assertTrue(entered.await(2,TimeUnit.SECONDS))
                if(cancel) job.cancelAndJoin() else {
                    valid.set(false);socket.close() // Same close action used by lease invalidation callback.
                    assertEquals("NETWORK_INVALIDATED",job.await().status)
                }
                assertTrue(socket.isClosed)
            } finally {socket.close();executor.shutdownNow();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS))}
        }
    }
    @Test fun authorizedThreeTargetExperiment()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Explicit NBSTAT opt-in required",args.getString("allowNbstat")=="true")
        val context=ins.targetContext
        val pin=runCatching{ins.context.assets.open("lan-audit-prefix.txt").bufferedReader().use{it.readText()}}.getOrNull()
        val plan=LanAuditAuthorization.validate(context.packageName,pin,
            listOf("allowRealLan","authorizedPrefix","auditRun").associateWith {args.getString(it)})
        val mapping=ins.context.assets.open("nbstat-targets.txt").bufferedReader().use{it.readLines()}
            .filter(String::isNotBlank).map{it.split('=')}.also{require(it.size==3 && it.all{x->x.size==2})}
            .associate{it[0] to it[1]}
        val scope=NbstatScope(plan.prefix,mapping)
        require(args.getString("targetIds")=="T05,T06,T09") {"Explicit approved target IDs required"}
        val cm=context.getSystemService(ConnectivityManager::class.java)
        val captured=checkNotNull(cm.activeNetwork){"No active network"}
        val expected=AndroidNetworkRepository(context).observeNetworkContext().first()
        val channel=AtomicReference<DatagramSocket?>()
        val lease=checkNotNull(AndroidNetworkLeaseProvider(context).acquire(expected){channel.get()?.close()}) {"LAN policy unavailable"}
        fun valid():Boolean = lease.revalidate() && cm.activeNetwork==captured &&
            cm.getNetworkCapabilities(captured)?.let { c ->
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }==true && cm.allNetworks.none{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
        val executor=Executors.newSingleThreadExecutor()
        val exchange=NbstatExchange(executor)
        val markers=File(context.filesDir,"nbstat162-sent").apply{mkdirs()}
        try {
            require(valid()) {"Captured network policy invalid"}
            val links=checkNotNull(cm.getLinkProperties(captured))
            require(links.linkAddresses.any {it.address is Inet4Address && it.prefixLength==24 && it.address.hostAddress?.startsWith(plan.prefix)==true}) {"Approved local /24 required"}
            log(JSONObject().put("kind","network").put("handle",captured.networkHandle).put("lease",lease.id).put("fd",File("/proc/self/fd").list()?.size))
            for(window in listOf(500,1000)) for(targetId in listOf("T05","T06","T09")) {
                ensureActive()
                val ip=checkNotNull(mapping[targetId]); plan.checkAddress(ip)
                scope.authorize(args.getString("allowNbstat"),targetId,ip,window,plan.prefix,expected.vpnActive!=false)
                require(valid()) {"Network invalidated; no further sends"}
                val address=InetAddress.getByAddress(ip.split('.').map{it.toInt().toByte()}.toByteArray())
                require(links.routes.any{it.matches(address) && !it.hasGateway() && it.type==android.net.RouteInfo.RTN_UNICAST}) {"Target not on captured direct LAN route"}
                val marker=File(markers,"$targetId-$window")
                if(marker.exists()) { log(JSONObject().put("kind","already_attempted").put("targetId",targetId).put("window",window)); continue }
                val socket=DatagramSocket(null)
                channel.set(socket)
                val transport=object:NbstatChannel {
                    override fun send(bytes:ByteArray) {
                        check(valid())
                        captured.bindSocket(socket) // No process-wide bind, alternate network, VPN bypass or DNS lookup.
                        socket.bind(InetSocketAddress(0))
                        check(valid())
                        socket.send(DatagramPacket(bytes,bytes.size,address,137))
                    }
                    override fun receive(windowMs:Int):NbstatDatagram {
                        socket.soTimeout=windowMs
                        val data=ByteArray(NbstatProtocol.MAX_PACKET+1)
                        val packet=DatagramPacket(data,data.size);socket.receive(packet)
                        return NbstatDatagram(data.copyOf(packet.length),packet.address.hostAddress.orEmpty(),packet.port)
                    }
                    override fun close(){socket.close()}
                }
                val outcome=exchange.query(transport,ip,SecureRandom().nextInt(65536),window,::valid,
                    beforeSend={check(marker.createNewFile()){ "Already attempted" }},
                    raw={kind,bytes->log(JSONObject().put("kind",kind).put("targetId",targetId).put("window",window)
                        .put("base64",java.util.Base64.getEncoder().encodeToString(bytes)))})
                channel.compareAndSet(socket,null)
                assertTrue(socket.isClosed)
                log(JSONObject().put("kind","outcome").put("targetId",targetId).put("window",window)
                    .put("sent",outcome.sent).put("received",outcome.received).put("elapsedMs",outcome.elapsedMs)
                    .put("status",outcome.status).put("parsed",outcome.parsed?.toString()).put("socketClosed",socket.isClosed)
                    .put("fd",File("/proc/self/fd").list()?.size))
                check(valid()){ "Network invalidated; remaining targets cancelled" }
            }
        } finally {
            channel.getAndSet(null)?.close();lease.close();executor.shutdownNow()
            assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS))
            log(JSONObject().put("kind","closed").put("leaseValid",lease.valid).put("workerTerminated",executor.isTerminated)
                .put("fd",File("/proc/self/fd").list()?.size))
        }
    }
}
