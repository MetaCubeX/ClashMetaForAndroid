package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.net.*
import android.os.Build
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.util.resolveDns
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.withContext

class NetworkObserveModule(service: Service) : Module<Network?>(service) {
    private data class Action(val type: Type, val network: Network) {
        enum class Type { Available, Lost, Changed }
    }

    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val actions = Channel<Action>(Channel.UNLIMITED)
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            actions.trySendBlocking(Action(Action.Type.Available, network))
        }

        override fun onLost(network: Network) {
            actions.trySendBlocking(Action(Action.Type.Lost, network))
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            actions.trySendBlocking(Action(Action.Type.Changed, network))
        }
    }

    override suspend fun run() {
        try {
            connectivity.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w("Observe network failed: $e", e)

            return
        }

        try {
            val networks = mutableSetOf<Network>()

            while (true) {
                val action = actions.receive()

                val resolveDefault = when (action.type) {
                    Action.Type.Available -> {
                        networks.add(action.network)

                        true
                    }
                    Action.Type.Lost -> {
                        networks.remove(action.network)

                        true
                    }
                    Action.Type.Changed -> {
                        false
                    }
                }

                val dns = networks.flatMap { network ->
                    connectivity?.resolveDns(network) ?: emptyList()
                }

                Clash.notifyDnsChanged(dns)

                Log.d("DNS: $dns")

                if (resolveDefault) {
                    val network = networks.maxByOrNull { net ->
                        connectivity.getNetworkCapabilities(net)?.let { cap ->
                            TRANSPORT_PRIORITY.indexOfFirst { cap.hasTransport(it) }
                        } ?: -1
                    }

                    enqueueEvent(network)

                    Log.d("Network: $network of $networks")
                }
            }
        } finally {
            withContext(NonCancellable) {
                enqueueEvent(null)

                Clash.notifyDnsChanged(emptyList())

                runCatching {
                    connectivity.unregisterNetworkCallback(callback)
                }
            }
        }
    }

    companion object {
        private val TRANSPORT_PRIORITY = sequence {
            yield(NetworkCapabilities.TRANSPORT_CELLULAR)

            if (Build.VERSION.SDK_INT >= 27) {
                yield(NetworkCapabilities.TRANSPORT_LOWPAN)
            }

            yield(NetworkCapabilities.TRANSPORT_BLUETOOTH)

            if (Build.VERSION.SDK_INT >= 26) {
                yield(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            }

            yield(NetworkCapabilities.TRANSPORT_WIFI)

            if (Build.VERSION.SDK_INT >= 31) {
                yield(NetworkCapabilities.TRANSPORT_USB)
            }

            yield(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.toList()
    }
}