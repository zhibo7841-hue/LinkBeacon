package com.networktoolbox.core.network.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.networktoolbox.core.network.tcp.TcpConnectOutcome
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTcpConnectorInstrumentedTest {
    @Test
    fun localOpenAndClosedPortsUseRealAndroidSockets() = runBlocking {
        ServerSocket(0).use { server ->
            val accept = async(Dispatchers.IO) {
                server.accept().use { }
            }
            val connector = AndroidTcpConnector(ioDispatcher = Dispatchers.IO)

            val open = connector.createAttempt("127.0.0.1", server.localPort, 1_000).use {
                it.awaitResult()
            }

            assertEquals(TcpConnectOutcome.CONNECTED, open.outcome)
            accept.await()
        }

        val closedPort = ServerSocket(0).use { it.localPort }
        val connector = AndroidTcpConnector(ioDispatcher = Dispatchers.IO)
        val closed = connector.createAttempt("127.0.0.1", closedPort, 1_000).use {
            it.awaitResult()
        }

        assertEquals(TcpConnectOutcome.REFUSED, closed.outcome)
    }
}
