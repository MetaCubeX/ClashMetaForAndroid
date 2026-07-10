package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.net.*
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.asSocketAddressText
import com.github.kr328.clash.service.util.sendConnectionsChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NetworkObserveModule(service: Service) : Module<Network?>(service) {
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val store = ServiceStore(service)
    private val networks: Channel<Network> = Channel(Channel.CONFLATED)
    private val preferredNetworks: Channel<Network?> = Channel(Channel.CONFLATED)
    private val shouldEmitParentNetwork =
        service is VpnService && Build.VERSION.SDK_INT in 22..28
    private val usesPlatformPreferredNetwork =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && service !is VpnService)
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    /**
     * Separate request for [ConnectivityManager.registerBestMatchingNetworkCallback]: it goes
     * through the requestNetwork plumbing, which rejects NET_CAPABILITY_FOREGROUND with
     * "IllegalArgumentException: Cannot request network with FOREGROUND" (device-verified on
     * Android 16) — registering with the observe [request] above silently downgrades every
     * 12+ device to the legacy heuristic path.
     */
    private val bestMatchingRequest = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    private data class NetworkInfo(
        @Volatile var losingMs: Long = 0,
        @Volatile var dnsList: List<InetAddress> = emptyList(),
        @Volatile var capabilities: NetworkCapabilities? = null,
    ) {
        fun isAvailable(): Boolean = losingMs < System.currentTimeMillis()
    }

    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var curDnsList = emptyList<String>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("NetworkObserve onAvailable")
            val dns = runCatching {
                connectivity.getLinkProperties(network)?.dnsServers ?: emptyList()
            }.getOrDefault(emptyList())
            networkInfos[network] = NetworkInfo(dnsList = dns)
            networks.trySend(network)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Log.i("NetworkObserve onLosing")
            networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive

            networks.trySend(network)
        }

        override fun onLost(network: Network) {
            Log.i("NetworkObserve onLost")
            networkInfos.remove(network)

            networks.trySend(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            Log.i("NetworkObserve onLinkPropertiesChanged")
            networkInfos[network]?.dnsList = linkProperties.dnsServers

            networks.trySend(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.i("NetworkObserve onCapabilitiesChanged")
            networkInfos[network]?.capabilities = networkCapabilities
            networks.trySend(network)
        }

        override fun onUnavailable() {
            Log.i("NetworkObserve onUnavailable")
        }
    }

    @Volatile
    private var callbackPreferredNetwork: Network? = null

    private val preferredNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("NetworkObserve preferred onAvailable: $network")
            callbackPreferredNetwork = network
            preferredNetworks.trySend(network)
        }

        override fun onLost(network: Network) {
            Log.i("NetworkObserve preferred onLost: $network")
            if (callbackPreferredNetwork == network) {
                callbackPreferredNetwork = null
                preferredNetworks.trySend(null)
            }
        }
    }

    private var callbackRegistered = false
    private var preferredCallbackRegistered = false
    private var platformPreferredNetwork: Network? = null

    private fun register(): Boolean {
        Log.i("NetworkObserve start register")
        callbackRegistered = try {
            connectivity.registerNetworkCallback(request, callback)
            true
        } catch (e: Exception) {
            Log.w("NetworkObserve register failed", e)
            false
        }

        if (usesPlatformPreferredNetwork) {
            preferredCallbackRegistered = try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        connectivity.registerBestMatchingNetworkCallback(
                            bestMatchingRequest,
                            preferredNetworkCallback,
                            Handler(service.mainLooper),
                        )

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                        connectivity.registerDefaultNetworkCallback(preferredNetworkCallback)
                }
                true
            } catch (e: Exception) {
                Log.w("NetworkObserve preferred network register failed", e)
                false
            }
        }

        return callbackRegistered || preferredCallbackRegistered
    }

    private fun unregister(): Boolean {
        Log.i("NetworkObserve start unregister")
        if (callbackRegistered) {
            try {
                connectivity.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w("NetworkObserve unregister failed", e)
            }
        }
        if (preferredCallbackRegistered) {
            try {
                connectivity.unregisterNetworkCallback(preferredNetworkCallback)
            } catch (e: Exception) {
                Log.w("NetworkObserve preferred network unregister failed", e)
            }
        }

        return false
    }

    private fun networkToInt(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = entry.value.capabilities ?: connectivity.getNetworkCapabilities(entry.key)
        // calculate priority based on transport type, available state
        // lower value means higher priority
        // wifi > ethernet > usb tethering > bluetooth tethering > cellular > satellite > other
        return when {
            capabilities == null -> 100
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> 5
            // TRANSPORT_LOWPAN / TRANSPORT_THREAD / TRANSPORT_WIFI_AWARE are not for general internet access, which will not set as default route.
            else -> 20
        } + (if (entry.value.isAvailable()) 0 else 10)
    }

    private fun selectLegacyPreferredNetwork(): Network? {
        val available = networkInfos.entries.filter { it.value.isAvailable() }
        val validated = available.filter {
            it.value.capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
        return (validated.ifEmpty { available }).minByOrNull { networkToInt(it) }?.key
    }

    private fun notifyDnsChange(network: Network? = currentPreferredNetwork()) {
        val dnsServers = network?.let {
            networkInfos[it]?.dnsList?.takeIf(List<InetAddress>::isNotEmpty)
                ?: connectivity.getLinkProperties(it)?.dnsServers
        } ?: emptyList()
        val dnsList = dnsServers.map { it.asSocketAddressText(53) }
        val prevDnsList = curDnsList
        if (prevDnsList != dnsList) {
            Log.i("notifyDnsChange updated: ${prevDnsList.size} -> ${dnsList.size}")
            curDnsList = dnsList
            Clash.notifyDnsChanged(dnsList)
        }
    }

    private val moduleStartedAt = SystemClock.elapsedRealtime()
    private val switchGate = NetworkSwitchReactionGate<Network>(moduleStartedAt)
    private var pendingSwitchRetry: Job? = null
    private var lastParentNetwork: Network? = null
    private var parentNetworkInitialized = false

    private fun currentPreferredNetwork(): Network? =
        if (usesPlatformPreferredNetwork && preferredCallbackRegistered) {
            platformPreferredNetwork
        } else {
            selectLegacyPreferredNetwork()
        }

    private fun CoroutineScope.observePreferredNetwork(network: Network?) {
        applySwitchDecision(
            switchGate.observe(
                candidate = network,
                now = SystemClock.elapsedRealtime(),
                enabled = store.networkSwitchReaction,
            )
        )
    }

    private fun CoroutineScope.applySwitchDecision(
        decision: NetworkSwitchReactionDecision<Network>,
    ) {
        if (decision.cancelPendingRetry) {
            pendingSwitchRetry?.cancel()
            pendingSwitchRetry = null
        }

        val retryAfterMs = decision.retryAfterMs
        if (retryAfterMs != null) {
            pendingSwitchRetry = launch {
                delay(retryAfterMs)
                pendingSwitchRetry = null
                applySwitchDecision(
                    switchGate.retry(
                        candidate = currentPreferredNetwork(),
                        now = SystemClock.elapsedRealtime(),
                        enabled = store.networkSwitchReaction,
                    )
                )
            }
            return
        }

        decision.reaction?.let { reactToPreferredNetworkSwitch(it) }
    }

    private fun CoroutineScope.reactToPreferredNetworkSwitch(network: Network) {
        val closed = Clash.closeAllConnections()
        service.sendConnectionsChanged()
        Log.i("NetworkObserve preferred network switched -> $network: closed $closed stale connections")

        launch {
            try {
                val groups = Clash.queryAllGroupNamesIncludingHidden()
                val checks = groups.map { it to Clash.healthCheck(it) }
                var failures = 0
                checks.forEach { (name, check) ->
                    try {
                        check.await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures++
                        Log.w("NetworkObserve health check failed for group $name", e)
                    }
                }

                Log.i("NetworkObserve health checks completed: ${groups.size - failures}/${groups.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NetworkObserve could not start group health checks", e)
            }
            service.sendConnectionsChanged()
        }
    }

    private suspend fun emitParentNetworkIfChanged(network: Network?) {
        if (!shouldEmitParentNetwork) return
        if (parentNetworkInitialized && network == lastParentNetwork) return

        parentNetworkInitialized = true
        lastParentNetwork = network
        enqueueEvent(network)
    }

    override suspend fun run() = coroutineScope {
        register()

        try {
            while (true) {
                val quit = select {
                    networks.onReceive {
                        coalesceNetworkEvents(it)
                        if (!usesPlatformPreferredNetwork || !preferredCallbackRegistered) {
                            val preferred = selectLegacyPreferredNetwork()
                            notifyDnsChange(preferred)
                            observePreferredNetwork(preferred)
                            emitParentNetworkIfChanged(preferred)
                        } else {
                            notifyDnsChange()
                        }

                        false
                    }
                    if (usesPlatformPreferredNetwork && preferredCallbackRegistered) {
                        preferredNetworks.onReceive { network ->
                            platformPreferredNetwork = network
                            notifyDnsChange(network)
                            observePreferredNetwork(network)
                            false
                        }
                    }
                }
                if (quit) {
                    return@coroutineScope
                }
            }
        } finally {
            pendingSwitchRetry?.cancel()
            withContext(NonCancellable) {
                unregister()

                Log.i("NetworkObserve dns = []")
                Clash.notifyDnsChanged(emptyList())
            }
        }
    }

    /**
     * Wait up to ~180ms for additional network events so we batch a quick "available -> losing
     * -> link-properties" burst into a single notification. Sleeps efficiently and exits early
     * once events stop arriving - the previous version did a hard `delay(500)` on every single
     * event, which was wasted CPU when nothing else was happening.
     */
    private suspend fun coalesceNetworkEvents(first: Network): Network {
        var latest = first
        val deadline = System.currentTimeMillis() + 180
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return latest
            val next = withTimeoutOrNull(remaining) { networks.receive() } ?: return latest
            latest = next
        }
    }
}
