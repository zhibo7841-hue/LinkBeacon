package com.networktoolbox.core.network.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class AndroidNetworkRepository(context: Context) : NetworkRepository {
    private val contextReader = AndroidNetworkContextReader(context)
    private val connectivityManager = contextReader.connectivityManager

    override fun observeNetworkContext(): Flow<NetworkContext> {
        val manager = connectivityManager ?: return flowOf(NetworkContext.unknown())

        return callbackFlow {
            trySend(contextReader.readCurrentContext())

            // Existing location grant may expose SSID through the shared current-network
            // source. This does not request permission or start a nearby scan.
            val callback = object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
            ) {
                override fun onAvailable(network: Network) {
                    if (manager.activeNetwork == network) trySend(contextReader.readContext(network))
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (manager.activeNetwork == network) {
                        trySend(contextReader.readContext(network, networkCapabilities))
                    }
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    if (manager.activeNetwork == network) {
                        trySend(contextReader.readContext(network, linkProperties = linkProperties))
                    }
                }

                override fun onLost(network: Network) {
                    trySend(contextReader.readCurrentContext())
                }
            }

            try {
                manager.registerDefaultNetworkCallback(callback)
            } catch (_: SecurityException) {
                trySend(NetworkContext.unknown())
                close()
            } catch (_: RuntimeException) {
                trySend(NetworkContext.unknown())
                close()
            }

            awaitClose {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }.distinctUntilChanged()
    }

}
