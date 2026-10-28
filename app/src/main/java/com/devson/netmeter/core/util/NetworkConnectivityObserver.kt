package com.devson.netmeter.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionType(val displayName: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Mobile Data"),
    ETHERNET("Ethernet"),
    VPN("VPN"),
    OTHER("Other"),
    NONE("Offline")
}

data class CurrentNetworkState(
    val isConnected: Boolean = false,
    val type: ConnectionType = ConnectionType.NONE,
    val isMetered: Boolean = false,
    val isValidated: Boolean = false,
    val description: String = "Offline"
)

interface ConnectivityObserver {
    val networkState: Flow<CurrentNetworkState>
    fun getCurrentState(): CurrentNetworkState
}

class AndroidConnectivityObserver(
    private val context: Context
) : ConnectivityObserver {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val networkState: Flow<CurrentNetworkState> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(CurrentNetworkState(isConnected = false, type = ConnectionType.NONE, description = "Network service unavailable"))
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentState())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentState())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(mapCapabilitiesToState(networkCapabilities))
            }
        }

        // Emit initial snapshot immediately
        trySend(getCurrentState())

        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Gracefully handle device security or system restrictions
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    override fun getCurrentState(): CurrentNetworkState {
        val cm = connectivityManager ?: return CurrentNetworkState(
            isConnected = false,
            type = ConnectionType.NONE,
            description = "Network service unavailable"
        )

        val activeNetwork = cm.activeNetwork ?: return CurrentNetworkState(
            isConnected = false,
            type = ConnectionType.NONE,
            description = "Offline / Disconnected"
        )

        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return CurrentNetworkState(
            isConnected = false,
            type = ConnectionType.NONE,
            description = "No network capabilities"
        )

        return mapCapabilitiesToState(capabilities)
    }

    private fun mapCapabilitiesToState(capabilities: NetworkCapabilities): CurrentNetworkState {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val (type, desc) = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                ConnectionType.WIFI to if (isValidated) "Connected via Wi-Fi" else "Wi-Fi (Limited Connectivity)"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val meteredText = if (isMetered) "Metered" else "Unmetered"
                ConnectionType.CELLULAR to if (isValidated) "Connected via Mobile ($meteredText)" else "Mobile Data (Limited Connectivity)"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                ConnectionType.ETHERNET to "Connected via Ethernet"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                ConnectionType.VPN to "Connected via VPN"
            }
            hasInternet -> {
                ConnectionType.OTHER to "Connected to Network"
            }
            else -> {
                ConnectionType.NONE to "Offline"
            }
        }

        return CurrentNetworkState(
            isConnected = hasInternet,
            type = type,
            isMetered = isMetered,
            isValidated = isValidated,
            description = desc
        )
    }
}
