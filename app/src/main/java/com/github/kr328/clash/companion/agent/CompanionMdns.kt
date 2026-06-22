package com.github.kr328.clash.companion.agent

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.companion.protocol.DiscoveryTxt

/**
 * Advertises the agent over mDNS/DNS-SD as `_clashctl._tcp` (PROTOCOL.md §4) while the gateway is
 * up, with the TXT record `app,id,name,ver,fp`. Registration is withdrawn on [unregister] so that
 * nothing is discoverable while the feature is OFF (§2).
 */
class CompanionMdns(context: Context) {
    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private var listener: NsdManager.RegistrationListener? = null

    fun register(txt: DiscoveryTxt, port: Int) {
        unregister()

        val info = NsdServiceInfo().apply {
            serviceName = txt.name
            serviceType = SERVICE_TYPE
            setPort(port)
            txt.attributes().forEach { (k, v) -> setAttribute(k, v) }
        }

        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i("Companion: mDNS registered as ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w("Companion: mDNS registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w("Companion: mDNS unregistration failed: $errorCode")
            }
        }
        listener = l

        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            Log.w("Companion: mDNS register error: ${e.message}")
            listener = null
        }
    }

    fun unregister() {
        listener?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Exception) {
                // already unregistered / never registered
            }
        }
        listener = null
    }

    private companion object {
        const val SERVICE_TYPE = "_clashctl._tcp"
    }
}
