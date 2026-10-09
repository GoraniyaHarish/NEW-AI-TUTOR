package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Real hardware connectivity
    private val _isDeviceOnline = MutableStateFlow(checkInitialConnectivity())

    // Allows testing offline behavior on demand while the device remains connected.
    private val _isOfflineSimulated = MutableStateFlow(false)
    val isOfflineSimulated: StateFlow<Boolean> = _isOfflineSimulated.asStateFlow()

    // Combined effective status
    private val _isOnline = MutableStateFlow(computeEffectiveOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isDeviceOnline.value = true
                        updateEffectiveOnline()
                    }

                    override fun onLost(network: Network) {
                        _isDeviceOnline.value = false
                        updateEffectiveOnline()
                    }
                }
            )
        } catch (_: Exception) {
            _isDeviceOnline.value = true
            updateEffectiveOnline()
        }
    }

    fun toggleOfflineSimulation() {
        _isOfflineSimulated.value = !_isOfflineSimulated.value
        updateEffectiveOnline()
    }

    fun setOfflineSimulation(simulatedOffline: Boolean) {
        _isOfflineSimulated.value = simulatedOffline
        updateEffectiveOnline()
    }

    private fun updateEffectiveOnline() {
        _isOnline.value = computeEffectiveOnline()
    }

    private fun computeEffectiveOnline(): Boolean {
        if (_isOfflineSimulated.value) return false
        return _isDeviceOnline.value
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
