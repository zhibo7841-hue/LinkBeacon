package com.networktoolbox.core.network.rdap

import java.io.*
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WhoisSocketTransactionTest {
    private class TestSocket(val block:Boolean=false,val peer:InetAddress=InetAddress.getByName("8.8.8.8")):Socket() {
        val closed=AtomicBoolean();val reading=CountDownLatch(1);val release=CountDownLatch(1)
        val bytes=ByteArrayOutputStream();var connects=0
        override fun connect(endpoint:SocketAddress,timeout:Int) {assertEquals(43,(endpoint as InetSocketAddress).port);assertEquals(3000,timeout);connects++}
        override fun getInetAddress()=peer
        override fun getOutputStream():OutputStream=bytes
        override fun getInputStream():InputStream=if(!block) ByteArrayInputStream("fixture".toByteArray()) else object:InputStream() {
            override fun read():Int {reading.countDown();check(release.await(3,TimeUnit.SECONDS));throw SocketException("closed fixture")}
        }
        override fun close(){closed.set(true);release.countDown()}
    }
    private val domain=RdapDomain("example.com","example.com")
    private val pinned=listOf(InetAddress.getByName("8.8.8.8"))
    @Test fun arinDetailedQueryIsWrittenOnceAndOtherRirsRejectItsFlags()=runBlocking {
        val socket=TestSocket()
        whoisSocketTransaction(socket,RegistrationRoute.direct("whois.arin.net",pinned,true),"+ n 173.249.200.19",1024,{},{})
        assertEquals("+ n 173.249.200.19\r\n",socket.bytes.toString("US-ASCII"));assertEquals(1,socket.connects)
        val rejected=TestSocket()
        try {whoisSocketTransaction(rejected,RegistrationRoute.direct("whois.ripe.net",pinned,true),"+ n 173.249.200.19",1024,{},{})
            fail("ARIN flags leaked") } catch(_:IllegalArgumentException) {}
        assertEquals(0,rejected.connects);assertTrue(rejected.closed.get())
    }
    @Test fun singleLabelDirectoryRequestIsAllowedOnlyAtIana()=runBlocking {
        val socket=TestSocket()
        whoisSocketTransaction(socket,RegistrationRoute.direct(WhoisIpData.IANA,pinned,true),"asia",1024,{},{})
        assertEquals("asia\r\n",socket.bytes.toString("US-ASCII"))
        assertFalse(WhoisServers.validWireQuery("whois.nic.asia","asia"))
    }
    @Test fun sendsOnlyAlabelCrLfAndClosesAfterEof()=runBlocking {
        val s=TestSocket();assertEquals("fixture",whoisSocketTransaction(s,pinned,domain,{},{ }).decodeToString())
        assertEquals("example.com\r\n",s.bytes.toString("US-ASCII"));assertTrue(s.closed.get());assertEquals(1,s.connects)
    }
    @Test fun stopInterruptsAnActiveReadAndJoinsWorker()=runBlocking {
        val s=TestSocket(true);val job=launch {whoisSocketTransaction(s,pinned,domain,{},{})}
        withContext(Dispatchers.IO) {assertTrue(s.reading.await(2,TimeUnit.SECONDS))}
        withTimeout(2000) {job.cancelAndJoin()};assertTrue(s.closed.get());assertTrue(job.isCompleted)
    }
    @Test fun deadlineClosesSocketRatherThanLeavingBlockedWorker()=runBlocking {
        val s=TestSocket(true)
        try {withTimeout(100) {whoisSocketTransaction(s,pinned,domain,{},{})};fail("timeout")}
        catch(_:TimeoutCancellationException) {assertTrue(s.closed.get())}
    }
    @Test fun networkChangeBeforeWriteNeverSendsQueryAndAlwaysCloses()=runBlocking {
        val s=TestSocket();var checks=0
        try {whoisSocketTransaction(s,pinned,domain,{if(++checks==2) throw RdapFailure(RdapStatus.NETWORK_CHANGED)},{})
            fail("network changed")}
        catch(e:RdapFailure) {assertEquals(RdapStatus.NETWORK_CHANGED,e.status)}
        assertTrue(s.closed.get());assertEquals(0,s.bytes.size())
    }
    @Test fun unexpectedSocketPeerIsRejectedBeforeWrite()=runBlocking {
        val s=TestSocket(peer=InetAddress.getByName("10.0.0.1"))
        try {whoisSocketTransaction(s,pinned,domain,{},{ });fail("unsafe peer")}
        catch(e:RdapFailure) {assertEquals(RdapStatus.UNSAFE_ENDPOINT,e.status)}
        assertTrue(s.closed.get());assertEquals(0,s.bytes.size())
    }
}
