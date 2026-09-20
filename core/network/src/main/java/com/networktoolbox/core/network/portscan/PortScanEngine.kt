package com.networktoolbox.core.network.portscan

interface PortScanEngine {
    suspend fun scan(
        request: PortScanRequest,
        onUpdate: (PortScanUpdate) -> Unit = {},
    ): PortScanSessionResult
}

interface PortScanClock {
    fun currentTimeMillis(): Long

    fun nanoTime(): Long
}

object SystemPortScanClock : PortScanClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun nanoTime(): Long = System.nanoTime()
}
