package com.networktoolbox

import com.networktoolbox.nbstat.*
import java.net.SocketTimeoutException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NbstatBoundaryTest {
    private val scope=NbstatScope("192.0.2.",mapOf("T05" to "192.0.2.5","T06" to "192.0.2.6","T09" to "192.0.2.9"))
    @Test fun strictIpScopeAndOptIn() {
        scope.authorize("true","T05","192.0.2.5",500,"192.0.2.",false)
        for(opt in listOf(null,"false")) assertThrows(IllegalArgumentException::class.java){scope.authorize(opt,"T05","192.0.2.5",500,"192.0.2.",false)}
        for(ip in listOf("192.0.2.1","192.0.2.6","198.51.100.5","host.example")) assertThrows(IllegalArgumentException::class.java){scope.authorize("true","T05",ip,500,"192.0.2.",false)}
        assertThrows(IllegalArgumentException::class.java){scope.authorize("true","T05","192.0.2.5",500,"198.51.100.",false)}
        assertThrows(IllegalArgumentException::class.java){scope.authorize("true","T05","192.0.2.5",500,"192.0.2.",true)}
        assertThrows(IllegalArgumentException::class.java){scope.authorize("true","T05","192.0.2.5",1500,"192.0.2.",false)}
    }
    @Test fun malformedAuthorization() { assertThrows(IllegalArgumentException::class.java){NbstatScope("192.0.2.",mapOf("T05" to "192.0.2.5"))} }
    private class Channel(val block:Boolean=false):NbstatChannel {
        val sent=AtomicInteger(); val closed=AtomicInteger(); val entered=CountDownLatch(1); val released=CountDownLatch(1)
        override fun send(bytes:ByteArray){sent.incrementAndGet()}
        override fun receive(windowMs:Int):NbstatDatagram { entered.countDown();if(block) check(released.await(3,TimeUnit.SECONDS)); throw SocketTimeoutException() }
        override fun close(){closed.incrementAndGet();released.countDown()}
    }
    @Test fun timeoutOneSendAndClose()=runBlocking { val ex=Executors.newSingleThreadExecutor();try{val c=Channel();val r=NbstatExchange(ex).query(c,"192.0.2.5",1,500,{true});assertEquals("TIMEOUT_NO_RESPONSE",r.status);assertEquals(1,c.sent.get());assertEquals(1,c.closed.get())}finally{ex.shutdownNow()} }
    @Test fun invalidLeaseNoPacket()=runBlocking { val ex=Executors.newSingleThreadExecutor();try{val c=Channel();val r=NbstatExchange(ex).query(c,"192.0.2.5",1,500,{false});assertEquals("POLICY_BLOCKED",r.status);assertEquals(0,c.sent.get());assertEquals(1,c.closed.get())}finally{ex.shutdownNow()} }
    @Test fun cancelClosesBlockedReceive()=runBlocking { val ex=Executors.newSingleThreadExecutor();try{val c=Channel(true);val job=async{NbstatExchange(ex).query(c,"192.0.2.5",1,1000,{true})};yield();assertTrue(c.entered.await(2,TimeUnit.SECONDS));job.cancelAndJoin();assertEquals(1,c.closed.get());assertEquals(1,c.sent.get())}finally{ex.shutdownNow();check(ex.awaitTermination(3,TimeUnit.SECONDS))} }
    @Test fun invalidatedDuringReceiveRejectsResult()=runBlocking { val ex=Executors.newSingleThreadExecutor();try{val c=Channel(true);val valid=java.util.concurrent.atomic.AtomicBoolean(true);val job=async{NbstatExchange(ex).query(c,"192.0.2.5",1,1000,{valid.get()})};yield();assertTrue(c.entered.await(2,TimeUnit.SECONDS));valid.set(false);c.released.countDown();assertEquals("NETWORK_INVALIDATED",job.await().status);assertEquals(1,c.closed.get())}finally{ex.shutdownNow()} }
}
