package com.aiutil.videodownloader.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 전역 환경 설정 및 네트워크 상태 감시.
 *
 * 현재는 Wi-Fi 전용 다운로드 옵션만 보관한다.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    @Volatile
    private var onMeteredHandler: (() -> Unit)? = null
    private var networkRegistered = false

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
        if (enabled && !isUnmeteredConnected()) {
            onMeteredHandler?.invoke()
        }
    }

    /** Wi-Fi 또는 비과금 유선/무선 네트워크에 연결되어 있는지 확인한다. */
    fun isUnmeteredConnected(): Boolean {
        val cm = connectivity ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return isUnmetered(caps)
    }

    private fun isUnmetered(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Wi-Fi 전용 모드일 때 모바일 데이터로 전환되거나 연결이 끊기면 콜백을 호출한다. */
    fun observeNetwork(onMeteredNetworkDetected: () -> Unit) {
        onMeteredHandler = onMeteredNetworkDetected
        if (networkRegistered) return
        val cm = connectivity ?: return

        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (_wifiOnly.value && !isUnmetered(networkCapabilities)) {
                        onMeteredHandler?.invoke()
                    }
                }

                override fun onLost(network: Network) {
                    if (_wifiOnly.value && !isUnmeteredConnected()) {
                        onMeteredHandler?.invoke()
                    }
                }
            })
            networkRegistered = true
        }
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
    }
}
