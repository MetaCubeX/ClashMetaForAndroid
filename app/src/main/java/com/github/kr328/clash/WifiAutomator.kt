package com.github.kr328.clash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService

class WifiAutomator(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val serviceStore = ServiceStore(context)
    private var lastConnectedSsid: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateCurrentSsid(network)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d("WifiAutomator: Network Lost")

            // Check if the lost network was the target WiFi
            if (serviceStore.autoConnectVpnOnWifiDisconnect) {
                Log.d("WifiAutomator: autoConnectVpnOnWifiDisconnect is enabled and lastConnectedSsid is $lastConnectedSsid")
                val targetSsid = serviceStore.wifiSsidForVpn
                if (targetSsid?.isNotEmpty() == true && lastConnectedSsid == targetSsid) {
                    Log.d("WifiAutomator: Disconnected from target WiFi $targetSsid, starting Clash")
                    context.startClashService()
                }
            } else {
                Log.d("WifiAutomator: autoConnectVpnOnWifiDisconnect is disabled")
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateCurrentSsid(network)
        }
    }

    private fun updateCurrentSsid(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            var ssid: String? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                ssid = wifiInfo?.ssid
            }
            
            // Fallback or if Q+ API returned null/unknown
            if (ssid == null || ssid == WifiManager.UNKNOWN_SSID) {
                // Check permissions before calling legacy API to avoid SecurityException
                // Note: ACCESS_BACKGROUND_LOCATION is needed for background access on Android 10+
                val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasFineLocation) {
                    try {
                        val info = wifiManager.connectionInfo
                        if (info != null && info.supplicantState.name == "COMPLETED") {
                            ssid = info.ssid
                        }
                    } catch (e: Exception) {
                        Log.w("WifiAutomator: Failed to get legacy wifi info", e)
                    }
                } else {
                    Log.w("WifiAutomator: Missing location permission for legacy wifi info")
                }
            }

            val cleanSsid = ssid?.trim('"')
            if (cleanSsid != null && cleanSsid != "<unknown ssid>" && cleanSsid != "0x") {
                lastConnectedSsid = cleanSsid
                Log.d("WifiAutomator: WiFi Connected to $cleanSsid")
                
                if (serviceStore.autoConnectVpnOnWifiDisconnect) {
                    val targetSsid = serviceStore.wifiSsidForVpn
                    if (targetSsid?.isNotEmpty() == true && cleanSsid == targetSsid) {
                        Log.d("WifiAutomator: Connected to trusted WiFi $targetSsid, stopping Clash")
                        context.stopClashService()
                    }
                }
            } else {
                Log.d("WifiAutomator: Failed to get valid SSID. Raw: $ssid")
            }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Initial check
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            updateCurrentSsid(activeNetwork)
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }
}