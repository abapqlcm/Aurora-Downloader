package com.aurora.downloader.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Downloads stall permanently when Wi-Fi drops mid-transfer and the engine
 * never learns the transport came back. This is the single connectivity
 * signal the engine needs: a hot flow of the current online state plus
 * unmetered/charging hints the scheduler gates downloads on.
 */
object NetworkMonitor {

    data class State(
        val online: Boolean,
        val unmetered: Boolean
    ) {
        companion object {
            val UNKNOWN = State(online = false, unmetered = false)
        }
    }

    private fun cm(context: Context): ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * The current state, then a new emission on every change. Completes when
     * the collector cancels — the callback is unregistered automatically.
     */
    fun state(context: Context): Flow<State> = callbackFlow {
        val cm = cm(context)
        if (cm == null) {
            trySend(State.UNKNOWN)
            awaitClose()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshot(cm))
            }

            override fun onLost(network: Network) {
                trySend(snapshot(cm))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // Wi-Fi -> mobile changes meteredness without onLost firing.
                trySend(snapshot(cm))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
        .onStart { emit(snapshot(cm(context)!!)) }
        .distinctUntilChanged()

    /** Synchronous probe, for the scheduler's tick. */
    fun current(context: Context): State = cm(context)?.let { snapshot(it) }
        ?: State.UNKNOWN

    private fun snapshot(cm: ConnectivityManager): State {
        val active = cm.activeNetwork ?: return State(false, false)
        val caps = cm.getNetworkCapabilities(active) ?: return State(false, false)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return State(online, unmetered)
    }
}
