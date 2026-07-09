package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.net.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.asSocketAddressText
import com.github.kr328.clash.service.util.sendConnectionsChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NetworkObserveModule(service: Service) : Module<Network>(service) {
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val store = ServiceStore(service)
    private val networks: Channel<Network> = Channel(Channel.CONFLATED)
    private val shouldEmitParentNetwork =
        service is VpnService && Build.VERSION.SDK_INT in 22..28
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    private data class NetworkInfo(
        @Volatile var losingMs: Long = 0,
        @Volatile var dnsList: List<InetAddress> = emptyList()
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
            networks.trySend(network)
        }

        override fun onUnavailable() {
            Log.i("NetworkObserve onUnavailable")
        }
    }

    private fun register(): Boolean {
        Log.i("NetworkObserve start register")
        return try {
            connectivity.registerNetworkCallback(request, callback)

            true
        } catch (e: Exception) {
            Log.w("NetworkObserve register failed", e)

            false
        }
    }

    private fun unregister(): Boolean {
        Log.i("NetworkObserve start unregister")
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w("NetworkObserve unregister failed", e)
        }

        return false
    }

    private fun networkToInt(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = connectivity.getNetworkCapabilities(entry.key)
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

    private fun notifyDnsChange() {
        val dnsList = (networkInfos.asSequence().minByOrNull { networkToInt(it) }?.value?.dnsList
            ?: emptyList()).map { x -> x.asSocketAddressText(53) }
        val prevDnsList = curDnsList
        if (prevDnsList != dnsList) {
            Log.i("notifyDnsChange updated: ${prevDnsList.size} -> ${dnsList.size}")
            curDnsList = dnsList
            Clash.notifyDnsChanged(dnsList)
        }
    }

    /** Highest-priority (= default route) network we last observed, per [networkToInt]. */
    @Volatile
    private var defaultNetwork: Network? = null
    private var defaultNetworkInitialized = false
    private var lastSwitchReactionAt = 0L
    private val moduleStartedAt = SystemClock.elapsedRealtime()

    /**
     * The missing half of network handling (the callback above only refreshes DNS): when the
     * DEFAULT network actually changes — Wi-Fi <-> cellular, Wi-Fi roaming, airplane-mode
     * recovery — the engine's existing sockets ran over the dead network and its
     * fallback/url-test groups won't re-evaluate the world until their test `interval` expires
     * (minutes of "VPN on, nothing loads", e.g. leaving home Wi-Fi onto whitelisted LTE).
     *
     * So on a real switch we (1) close stale connections — re-dials go out over the new network
     * and re-match the current group state, and (2) force-run group health checks so
     * fallback/url-test converge in one probe round-trip instead of an interval. Capability
     * chirps on the SAME network don't qualify — only a change of the winning [Network] handle.
     */
    private fun reactToDefaultNetworkSwitch() {
        val winner = networkInfos.entries.minByOrNull { networkToInt(it) }?.key
        if (!defaultNetworkInitialized) {
            // First observation after module start: just record it. VPN startup delivers the
            // initial batch of onAvailable events — reacting there would kill newborn
            // connections for no reason.
            defaultNetworkInitialized = true
            defaultNetwork = winner
            return
        }
        if (winner == defaultNetwork) return
        // Everything vanished (airplane mode): nothing to probe; the recovery event reacts.
        if (winner == null) {
            defaultNetwork = null
            return
        }
        if (!store.networkSwitchReaction) {
            defaultNetwork = winner
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - moduleStartedAt < 5_000) {
            // Startup grace: the initial event burst can elect Wi-Fi after briefly seeing
            // cellular — commit without reacting.
            defaultNetwork = winner
            return
        }
        // Flap guard: a bouncing network must not storm the engine with close/probe cycles.
        // Deliberately do NOT commit defaultNetwork here — the periodic capability chirps of
        // the surviving network retry the reaction once the guard window has passed, so a
        // switch that lands inside the window is deferred, not lost.
        if (now - lastSwitchReactionAt < 3_000) return
        lastSwitchReactionAt = now
        defaultNetwork = winner

        val closed = Clash.closeAllConnections()
        Clash.healthCheckAll()
        Log.i("NetworkObserve default network switched -> $winner: closed $closed stale connections, forced group health checks")
        // Nudge the UI (Event.ConnectionsChanged) so the dashboard re-fetches where the auto
        // groups converged instead of waiting for the next ticker.
        service.sendConnectionsChanged()
    }

    override suspend fun run() {
        register()

        try {
            while (true) {
                val quit = select {
                    networks.onReceive {
                        val network = coalesceNetworkEvents(it)
                        notifyDnsChange()
                        reactToDefaultNetworkSwitch()
                        if (shouldEmitParentNetwork) {
                            enqueueEvent(network)
                        }

                        false
                    }
                }
                if (quit) {
                    return
                }
            }
        } finally {
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
