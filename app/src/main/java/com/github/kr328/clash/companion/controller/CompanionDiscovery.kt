package com.github.kr328.clash.companion.controller

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.companion.protocol.DiscoveryTxt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Browses `_clashctl._tcp` over mDNS and resolves each instance to a [DiscoveredAgent]
 * (PROTOCOL.md §4). Resolves are serialized because pre-API-31 NsdManager allows only one at a
 * time. The TXT `id` (agent deviceId) lets callers relocate a paired agent at a new address (§4.4).
 */
class CompanionDiscovery(
    context: Context,
    private val onUpdate: (List<DiscoveredAgent>) -> Unit,
) {
    data class DiscoveredAgent(
        val deviceId: String,
        val name: String,
        val app: String,
        val fp: String,
        val ver: Int,
        val host: String,
        val port: Int,
    )

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private val agents = ConcurrentHashMap<String, DiscoveredAgent>()
    private val resolveQueue = LinkedBlockingQueue<NsdServiceInfo>()
    @Volatile private var resolving = false
    @Volatile private var started = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w("Companion: discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onDiscoveryStarted(serviceType: String) {}

        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType.contains(SERVICE_TYPE.trimEnd('.'))) {
                enqueueResolve(serviceInfo)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            // Remove by display name (the only stable handle pre-resolve); emit refreshed list.
            val removed = agents.entries.filter { it.value.name == serviceInfo.serviceName }
            removed.forEach { agents.remove(it.key) }
            if (removed.isNotEmpty()) onUpdate(agents.values.toList())
        }
    }

    fun start() {
        if (started) return
        started = true
        agents.clear()
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w("Companion: discovery error: ${e.message}")
            started = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            nsd.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {
        }
        resolveQueue.clear()
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        resolveQueue.offer(info)
        pumpResolve()
    }

    @Synchronized
    private fun pumpResolve() {
        if (resolving) return
        val next = resolveQueue.poll() ?: return
        resolving = true
        try {
            nsd.resolveService(next, resolveListener())
        } catch (e: Exception) {
            Log.w("Companion: resolve error: ${e.message}")
            resolving = false
            pumpResolve()
        }
    }

    private fun resolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            resolving = false
            pumpResolve()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            try {
                val attrs = serviceInfo.attributes.mapValues { (_, v) ->
                    v?.toString(Charsets.UTF_8) ?: ""
                }
                val txt = DiscoveryTxt.fromMap(attrs)
                val host = serviceInfo.host?.hostAddress
                if (txt != null && host != null) {
                    agents[txt.id] = DiscoveredAgent(
                        deviceId = txt.id,
                        name = txt.name,
                        app = txt.app,
                        fp = txt.fp,
                        ver = txt.ver,
                        host = host,
                        port = serviceInfo.port,
                    )
                    onUpdate(agents.values.toList())
                }
            } catch (e: Exception) {
                Log.w("Companion: resolved-parse error: ${e.message}")
            } finally {
                resolving = false
                pumpResolve()
            }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_clashctl._tcp"
    }
}
