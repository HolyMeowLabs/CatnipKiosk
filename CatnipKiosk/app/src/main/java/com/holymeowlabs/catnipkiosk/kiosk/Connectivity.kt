package com.holymeowlabs.catnipkiosk.kiosk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/** Reports default-network availability on the main thread's caller via [onChange]. */
class Connectivity(context: Context, private val onChange: (available: Boolean) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange(true)
        override fun onLost(network: Network) = onChange(false)
    }

    fun start() = manager.registerDefaultNetworkCallback(callback)

    fun stop() = manager.unregisterNetworkCallback(callback)
}
