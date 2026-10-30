package com.networktoolbox.core.network.rdap

import com.networktoolbox.core.network.tls.TlsTestFixtures
import java.io.*
import java.net.*
import java.util.concurrent.*
import javax.net.SocketFactory
import javax.net.ssl.*
import kotlinx.coroutines.*
import okhttp3.*
import org.junit.Test
import org.junit.Assert.*

/** Loopback servers + controlled address translation exercise the actual production HTTP/socket helpers. */
class RegistrationRouteTest {
    private val host="localhost.test"
    private fun address(s:String)=InetAddress.getByName(s)
    private class MappedSocket(private val port:Int):Socket() {
        var intended:InetSocketAddress?=null
        override fun connect(endpoint:SocketAddress,timeout:Int) {
            intended=endpoint as InetSocketAddress
            super.connect(InetSocketAddress("127.0.0.1",port),timeout)
        }
        override fun getInetAddress():InetAddress = intended?.address ?: super.getInetAddress()
        override fun getPort():Int=intended?.port ?: super.getPort()
        override fun getRemoteSocketAddress():SocketAddress=intended ?: super.getRemoteSocketAddress()
    }
    private class Factory(private val port:Int):SocketFactory() {
        val created=mutableListOf<MappedSocket>()
        override fun createSocket():Socket=MappedSocket(port).also {created+=it}
        override fun createSocket(h:String,p:Int):Socket=error("not used")
        override fun createSocket(h:String,p:Int,l:InetAddress,lp:Int):Socket=error("not used")
        override fun createSocket(h:InetAddress,p:Int):Socket=error("not used")
        override fun createSocket(h:InetAddress,p:Int,l:InetAddress,lp:Int):Socket=error("not used")
    }
    private class Server(val tls:Boolean,val proxy:Boolean=false,val proxyStatus:Int=200,val block:Boolean=false):AutoCloseable {
        private val listener=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        private val executor=Executors.newSingleThreadExecutor()
        private val peers=java.util.Collections.synchronizedList(mutableListOf<Socket>())
        val port get()=listener.localPort
        val accepted=CountDownLatch(1)
        var tunnel="";var request="";var sni:List<String> = emptyList()
        val job=executor.submit {
            runCatching {
                val raw=listener.accept();peers+=raw;raw.soTimeout=2500;accepted.countDown()
                if(block){raw.getInputStream().read();return@runCatching}
                if(proxy) {
                    tunnel=header(raw.getInputStream())
                    raw.getOutputStream().write("HTTP/1.1 $proxyStatus Fixture\r\n\r\n".toByteArray());raw.getOutputStream().flush()
                    if(proxyStatus!=200) return@runCatching
                }
                if(tls) {
                    val ssl=TlsTestFixtures.serverContext().socketFactory.createSocket(raw,"localhost.test",port,true) as SSLSocket
                    peers+=ssl;ssl.useClientMode=false;ssl.startHandshake()
                    sni=(ssl.session as ExtendedSSLSession).requestedServerNames.filterIsInstance<SNIHostName>().map {it.asciiName}
                    request=header(ssl.getInputStream())
                    ssl.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                    ssl.getOutputStream().flush();ssl.close()
                } else {
                    request=raw.getInputStream().bufferedReader().readLine()
                    raw.getOutputStream().write("NetRange: 8.8.8.0 - 8.8.8.255\r\nNetName: FIXTURE\r\n".toByteArray())
                    raw.getOutputStream().flush();raw.close()
                }
            }
        }
        override fun close(){listener.close();synchronized(peers){peers.forEach {runCatching {it.close()}}};executor.shutdownNow();executor.awaitTermination(3,TimeUnit.SECONDS)}
        companion object {
            fun header(input:InputStream):String {
                val out=StringBuilder()
                while(out.length<8192 && !out.endsWith("\r\n\r\n")) {val b=input.read();if(b<0) break;out.append(b.toChar())}
                return out.toString()
            }
        }
    }
    private fun base(trusted:Boolean=true):OkHttpClient {
        val builder=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        if(trusted) {val manager=TlsTestFixtures.trustedManager();val ssl=SSLContext.getInstance("TLS");ssl.init(null,arrayOf(manager),null)
            builder.sslSocketFactory(ssl.socketFactory,manager)}
        return builder.build()
    }
    private fun route(ip:String,proxy:Boolean=false)=if(proxy) RegistrationRoute.systemProxy(host,
        Proxy(Proxy.Type.HTTP,InetSocketAddress.createUnresolved("proxy.example.test",8080)),listOf(address(ip)))
        else RegistrationRoute(host,Proxy.NO_PROXY,listOf(address(ip)))
    @Test fun mappedHttpsPreservesHostSniTlsAndActuallyExchangesHttp() {
        for(ip in listOf("198.18.0.1","8.8.8.8")) Server(tls=true).use {server->
            val sockets=Factory(server.port);var evidence=RdapTransportEvidence()
            val client=registrationHttpClient(base(),sockets,route(ip),3000,{},RdapObserver.NONE){evidence=it(evidence)}
            client.newCall(Request.Builder().url("https://$host/domain/example.com").build()).execute().use {assertEquals(200,it.code);assertEquals("{}",it.body.string())}
            server.job.get(3,TimeUnit.SECONDS)
            assertEquals(listOf(host),server.sni);assertTrue(server.request.contains("Host: $host\r\n"))
            assertEquals(ip,sockets.created.single().intended?.address?.hostAddress)
            assertTrue(evidence.tlsCompleted);assertEquals(1,evidence.httpResponses);client.connectionPool.evictAll()
        }
    }
    @Test fun mappedHttpsRejectsWrongHostnameAndUntrustedCertificate() {
        for(wrongName in listOf(true,false)) Server(tls=true).use {server->
            val name=if(wrongName) "wrong.example.com" else host
            val r=RegistrationRoute(name,Proxy.NO_PROXY,listOf(address("198.18.0.1")))
            val client=registrationHttpClient(base(trusted=wrongName),Factory(server.port),r,3000,{},RdapObserver.NONE){ }
            try {client.newCall(Request.Builder().url("https://$name/domain/example.com").build()).execute().use {};fail("TLS must reject")}
            catch(_:SSLException) { }
            assertEquals("",server.request);client.connectionPool.evictAll()
        }
    }
    @Test fun systemProxyTunnelRetainsOriginalHttpsHostAndTlsValidation() {
        Server(tls=true,proxy=true).use {server->
            val client=registrationHttpClient(base(),Factory(server.port),route("127.0.0.1",true),3000,{},RdapObserver.NONE){ }
            client.newCall(Request.Builder().url("https://$host/domain/example.com").build()).execute().use {assertEquals(200,it.code);it.body.string()}
            server.job.get(3,TimeUnit.SECONDS)
            assertTrue(server.tunnel.startsWith("CONNECT $host:443 HTTP/1.1"));assertEquals(listOf(host),server.sni)
            assertTrue(server.request.contains("Host: $host\r\n"));client.connectionPool.evictAll()
        }
    }
    @Test fun mappedWhoisReallyConnects43AndSendsNumericQuery()=runBlocking {
        Server(tls=false).use {server->
            val socket=MappedSocket(server.port);val r=RegistrationRoute.direct("whois.iana.org",listOf(address("198.18.0.1")),true)
            val text=whoisSocketTransaction(socket,r,"8.8.8.8",1024,{},{ }).decodeToString()
            assertTrue(text.contains("NetRange"));assertEquals("8.8.8.8",server.request)
            assertEquals(43,socket.intended?.port);assertEquals("198.18.0.1",socket.intended?.address?.hostAddress);assertTrue(socket.isClosed)
        }
    }
    @Test fun whoisSystemProxyUsesConnect43AndDoesNotSendQueryOnRefusal()=runBlocking {
        for(status in listOf(200,403,407)) Server(tls=false,proxy=true,proxyStatus=status).use {server->
            val socket=MappedSocket(server.port)
            val r=RegistrationRoute.systemProxy("whois.iana.org",Proxy(Proxy.Type.HTTP,InetSocketAddress.createUnresolved("proxy.test",8080)),listOf(address("127.0.0.1")))
            try {whoisSocketTransaction(socket,r,"8.8.8.8",1024,{},{ });assertEquals(200,status)}
            catch(e:RdapFailure) {assertTrue(status!=200);assertEquals(RdapStatus.ACCESS_RESTRICTED,e.status)}
            server.job.get(3,TimeUnit.SECONDS);assertTrue(server.tunnel.startsWith("CONNECT whois.iana.org:43 HTTP/1.1"))
            assertEquals(if(status==200) "8.8.8.8" else "",server.request);assertTrue(socket.isClosed)
        }
    }
    @Test fun cancelMappedSocketReadClosesUnderlyingConnection()=runBlocking {
        Server(tls=false,block=true).use {server->
            val socket=MappedSocket(server.port)
            val job=launch {whoisSocketTransaction(socket,RegistrationRoute.direct("whois.iana.org",listOf(address("198.18.0.1")),true),"8.8.8.8",1024,{},{})}
            withContext(Dispatchers.IO){assertTrue(server.accepted.await(2,TimeUnit.SECONDS))}
            withTimeout(2000){job.cancelAndJoin()};assertTrue(socket.isClosed)
        }
    }
    @Test fun networkLeaseCheckRejectsHttpsBeforeWritingApplicationRequest() {
        Server(tls=true).use {server->
            val client=registrationHttpClient(base(),Factory(server.port),route("198.18.0.1"),3000,
                {throw RdapFailure(RdapStatus.NETWORK_CHANGED)},RdapObserver.NONE){ }
            try {client.newCall(Request.Builder().url("https://$host/domain/example.com").build()).execute().use {};fail("network change")}
            catch(e:RdapFailure) {assertEquals(RdapStatus.NETWORK_CHANGED,e.status)}
            assertEquals("",server.request)
        }
    }
    @Test fun onlyTrustedServicesAdmitMappingWhileUserInputsStillRejectIt() {
        assertTrue(RegistrationRoute.direct("rdap.arin.net",listOf(address("198.18.0.1")),true).mapped);assertFalse(RdapAddressPolicy.public(address("198.18.0.1")))
        for(pair in listOf("198.18.0.1" to false,"10.0.0.1" to true,"127.0.0.1" to true)) {
            try {RegistrationRoute.direct("rdap.arin.net",listOf(address(pair.first)),pair.second);fail("invalid route")}
            catch(e:RdapFailure) {assertEquals(RdapStatus.UNSAFE_ENDPOINT,e.status)}
        }
    }
}
