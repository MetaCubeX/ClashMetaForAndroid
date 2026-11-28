package com.github.kr328.clash.service.remote

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface IClashManager {
    fun queryTunnelState(): TunnelState
    fun queryTrafficTotal(): Long
    fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String>
    fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup
    fun queryConfiguration(): UiConfiguration
    fun queryProviders(): ProviderList

    fun patchSelector(group: String, name: String): Boolean

    suspend fun healthCheck(group: String)
    suspend fun updateProvider(type: Provider.Type, name: String)

    fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride
    fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride)
    fun clearOverride(slot: Clash.OverrideSlot)

    fun setLogObserver(observer: ILogObserver?)

    /**
     * Load the active profile without starting the service, so proxies can be
     * browsed and tested. No-op while the service is running.
     *
     * @return null on success, otherwise the failure reason.
     */
    suspend fun preparePreview(): String?

    /**
     * Load the preview again after the service stopped, for a page already
     * showing it. Needs no extra [releasePreview].
     *
     * @return null on success, otherwise the failure reason.
     */
    suspend fun reloadPreview(): String?

    /** Release the preview loaded by [preparePreview] after a grace period. */
    fun releasePreview()
}