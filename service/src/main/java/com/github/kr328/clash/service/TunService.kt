package com.github.kr328.clash.service

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.*
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.VpnRoutePlanner
import com.github.kr328.clash.service.util.VpnRouteSession
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val self: TunService
        get() = this

    private var reason: String? = null

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        val close = install(CloseModule(self))
        val tun = install(TunModule(self))
        val routes = VpnRouteSession { tun.open(it) }
        val config = install(ConfigurationModule(self) { configuration ->
            routes.update(configuration.routeExcludeAddress)
        })
        val network = install(NetworkObserveModule(self))

        if (store.dynamicNotification)
            install(DynamicNotificationModule(self))
        else
            install(StaticNotificationModule(self))

        install(AppListCacheModule(self))
        install(TimeZoneModule(self))
        install(SuspendModule(self))

        try {
            while (isActive) {
                val quit = select<Boolean> {
                    close.onEvent {
                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    network.onEvent { n ->
                        if (Build.VERSION.SDK_INT in 22..28) @TargetApi(22) {
                            setUnderlyingNetworks(n?.let { arrayOf(it) })
                        }

                        false
                    }
                }

                if (quit) break
            }
        } catch (e: Exception) {
            Log.e("Create clash runtime: ${e.message}", e)

            reason = e.message
        } finally {
            withContext(NonCancellable) {
                tun.close()

                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (StatusProvider.serviceRunning)
            return stopSelf()

        StatusProvider.serviceRunning = true

        StaticNotificationModule.createNotificationChannel(this)
        StaticNotificationModule.notifyLoadingNotification(this)

        runtime.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendClashStarted()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        TunModule.requestStop()

        StatusProvider.serviceRunning = false

        sendClashStopped(reason)

        cancelAndJoinBlocking()

        Log.i("TunService destroyed: ${reason ?: "successfully"}")

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runtime.requestGc()
    }

    private fun TunModule.open(exclusions: List<String>) {
        val store = ServiceStore(self)

        val includes = mutableListOf<String>()
        if (store.bypassPrivateNetwork) {
            includes.addAll(resources.getStringArray(R.array.bypass_private_route))
            if (store.allowIpv6) {
                includes.addAll(resources.getStringArray(R.array.bypass_private_route6))
            }
            includes.add("$TUN_DNS/32")
            if (store.allowIpv6) includes.add("$TUN_DNS6/128")
        } else {
            includes.add("$NET_ANY/0")
            if (store.allowIpv6) includes.add("$NET_ANY6/0")
        }
        val plan = try {
            VpnRoutePlanner.plan(includes, exclusions, Build.VERSION.SDK_INT >= 33)
        } catch (e: IllegalArgumentException) {
            // Planner errors contain fixed diagnostics, never raw configuration values.
            Log.w("VPN_ROUTE_PLAN_REJECTED: ${e.message}")
            throw e
        }
        Log.i("ROUTE_EXCLUDE_MODE=${plan.mode}")
        Log.i("VPN_ROUTE_INCLUDE_COUNT=${plan.includes.size}")
        Log.i("VPN_ROUTE_EXCLUDE_COUNT=${plan.requestedExcludes.size}")
        plan.requestedExcludes.forEach { Log.i("VPN_ROUTE_EXCLUDE=$it") }
        if (!store.allowIpv6 && plan.requestedExcludes.any { it.bits == 128 }) {
            Log.w("VPN_ROUTE_IPV6_EXCLUDE_INACTIVE: IPv6 remains disabled")
        }

        val device = with(Builder()) {
            // Interface address
            addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
            if (store.allowIpv6) {
                addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
            }

            // Route
            plan.includes.forEach { addRoute(it.address, it.length) }
            if (Build.VERSION.SDK_INT >= 33) {
                plan.excludes.forEach { excludeRoute(IpPrefix(it.address, it.length)) }
            }

            // Access Control
            when (store.accessControlMode) {
                AccessControlMode.AcceptAll -> Unit
                AccessControlMode.AcceptSelected -> {
                    (store.accessControlPackages + packageName).forEach {
                        runCatching { addAllowedApplication(it) }
                    }
                }
                AccessControlMode.DenySelected -> {
                    (store.accessControlPackages - packageName).forEach {
                        runCatching { addDisallowedApplication(it) }
                    }
                }
            }

            // Blocking
            setBlocking(false)

            // Mtu
            setMtu(TUN_MTU)

            // Session Name
            setSession("Clash")

            // Virtual Dns Server
            addDnsServer(TUN_DNS)
            if (store.allowIpv6) {
                addDnsServer(TUN_DNS6)
            }

            // Open MainActivity
            setConfigureIntent(
                PendingIntent.getActivity(
                    self,
                    R.id.nf_vpn_status,
                    Intent().setComponent(Components.MAIN_ACTIVITY),
                    pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                )
            )

            // Metered
            if (Build.VERSION.SDK_INT >= 29) {
                setMetered(false)
            }

            // System Proxy
            if (Build.VERSION.SDK_INT >= 29 && store.systemProxy) {
                listenHttp()?.let {
                    setHttpProxy(
                        ProxyInfo.buildDirectProxy(
                            it.address.hostAddress,
                            it.port,
                            HTTP_PROXY_BLACK_LIST + if (store.bypassPrivateNetwork) HTTP_PROXY_LOCAL_LIST else emptyList()
                        )
                    )
                }
            }

            if (store.allowBypass) {
                allowBypass()
            }

            TunModule.TunDevice(
                fd = establish()?.detachFd()
                    ?: throw NullPointerException("Establish VPN rejected by system"),
                stack = store.tunStackMode,
                gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" + if (store.allowIpv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
                portal = TUN_PORTAL + if (store.allowIpv6) ",$TUN_PORTAL6" else "",
                dns = if (store.dnsHijacking) NET_ANY else (TUN_DNS + if (store.allowIpv6) ",$TUN_DNS6" else ""),
            )
        }

        attach(device)
    }

    companion object {
        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
        private const val TUN_DNS = TUN_PORTAL
        private const val TUN_DNS6 = TUN_PORTAL6
        private const val NET_ANY = "0.0.0.0"
        private const val NET_ANY6 = "::"

        private val HTTP_PROXY_LOCAL_LIST: List<String> = listOf(
            "localhost",
            "*.local",
            "127.*",
            "10.*",
            "172.16.*",
            "172.17.*",
            "172.18.*",
            "172.19.*",
            "172.2*",
            "172.30.*",
            "172.31.*",
            "192.168.*"
        )
        private val HTTP_PROXY_BLACK_LIST: List<String> = listOf(
            "*zhihu.com",
            "*zhimg.com",
            "*jd.com",
            "100ime-iat-api.xfyun.cn",
            "*360buyimg.com",
        )
    }
}
