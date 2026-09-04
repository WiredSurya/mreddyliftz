package com.mreddy.liftz.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone currently has usable internet.
 *
 * Deliberately reports VALIDATED connectivity rather than "a network exists": a captive-portal
 * wifi that has not been logged into, or a connection with no route out, both look connected to a
 * naive check and would make an offline banner flicker misleadingly.
 *
 * Needs only ACCESS_NETWORK_STATE, which is a normal (non-runtime) permission and does NOT grant
 * the app the ability to make network calls. INTERNET is still not requested anywhere.
 */
class Connectivity(private val context: Context) {

    fun isOnline(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            trySend(true)   // can't tell; assume fine rather than nagging
            awaitClose { }
            return@callbackFlow
        }

        fun current(): Boolean {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(current())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) { trySend(current()) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
