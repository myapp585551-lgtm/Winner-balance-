package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class OfflineOperationException(
    message: String = "Internet connection required."
) : IllegalStateException(message)

interface ConnectivityChecker {
    val isOnlineFlow: Flow<Boolean>
    fun isCurrentlyOnline(): Boolean
}

class AndroidConnectivityObserver(context: Context) : ConnectivityChecker {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return hasUsableInternetCapability(capabilities)
    }

    override val isOnlineFlow: Flow<Boolean> = callbackFlow {
        trySend(isCurrentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(hasUsableInternetCapability(networkCapabilities) || isCurrentlyOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: SecurityException) {
            trySend(false)
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    private fun hasUsableInternetCapability(capabilities: NetworkCapabilities): Boolean {
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        ) {
            return false
        }
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}

/**
 * Controllable connectivity checker for deterministic local unit and Robolectric tests.
 */
class ControllableConnectivityChecker(initialOnline: Boolean = true) : ConnectivityChecker {
    private val state = MutableStateFlow(initialOnline)
    override val isOnlineFlow: Flow<Boolean> = state

    override fun isCurrentlyOnline(): Boolean = state.value

    fun setOnline(online: Boolean) {
        state.value = online
    }
}
