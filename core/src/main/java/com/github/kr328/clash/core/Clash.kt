package com.github.kr328.clash.core

import com.github.kr328.clash.common.util.SubscriptionDeviceHeaders
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.core.util.parseInetSocketAddress
import com.github.kr328.clash.common.Global
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

object Clash {
    enum class OverrideSlot {
        Persist, Session
    }

    private val ConfigurationOverrideJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun reset() {
        Bridge.nativeReset()
    }

    fun forceGc() {
        Bridge.nativeForceGc()
    }

    fun suspendCore(suspended: Boolean) {
        Bridge.nativeSuspend(suspended)
    }

    fun queryTunnelState(): TunnelState {
        val json = Bridge.nativeQueryTunnelState()

        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    fun queryTrafficNow(): Traffic {
        return Bridge.nativeQueryTrafficNow()
    }

    fun queryTrafficTotal(): Traffic {
        return Bridge.nativeQueryTrafficTotal()
    }

    fun notifyDnsChanged(dns: List<String>) {
        Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
    }

    fun notifyTimeZoneChanged(name: String, offset: Int) {
        Bridge.nativeNotifyTimeZoneChanged(name, offset)
    }

    fun notifyInstalledAppsChanged(uids: List<Pair<Int, String>>) {
        val uidList = uids.joinToString(separator = ",") { "${it.first}:${it.second}" }

        Bridge.nativeNotifyInstalledAppChanged(uidList)
    }

    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ) {
        Bridge.nativeStartTun(fd, stack, gateway, portal, dns, object : TunInterface {
            override fun markSocket(fd: Int) {
                markSocket(fd)
            }

            override fun querySocketUid(protocol: Int, source: String, target: String): Int {
                return querySocketUid(
                    protocol,
                    parseInetSocketAddress(source),
                    parseInetSocketAddress(target)
                )
            }
        })
    }

    fun stopTun() {
        Bridge.nativeStopTun()
    }

    fun startHttp(listenAt: String): String? {
        return Bridge.nativeStartHttp(listenAt)
    }

    fun stopHttp() {
        Bridge.nativeStopHttp()
    }

    fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
        val names = Json.Default.decodeFromString(
            JsonArray.serializer(),
            Bridge.nativeQueryGroupNames(excludeNotSelectable)
        )

        return names.map {
            require(it.jsonPrimitive.isString)

            it.jsonPrimitive.content
        }
    }

    fun queryGroup(name: String, sort: ProxySort): ProxyGroup {
        return Bridge.nativeQueryGroup(name, sort.name)
            ?.let { Json.Default.decodeFromString(ProxyGroup.serializer(), it) }
            ?: ProxyGroup(Proxy.Type.Unknown, emptyList(), "")
    }

    fun healthCheck(name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeHealthCheck(this, name)
        }
    }

    fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    fun patchSelector(selector: String, name: String): Boolean {
        return Bridge.nativePatchSelector(selector, name)
    }

    fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        subscriptionHeadersJson: String = SubscriptionDeviceHeaders.toJson(Global.application),
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeFetchAndValid(
                object : FetchCallback {
                    override fun report(statusJson: String) {
                        reportStatus(
                            Json.Default.decodeFromString(
                                FetchStatus.serializer(),
                                statusJson
                            )
                        )
                    }

                    override fun complete(error: String?) {
                        if (error != null)
                            completeExceptionally(ClashException(error))
                        else
                            complete(Unit)
                    }
                },
                path.absolutePath,
                url,
                force,
                subscriptionHeadersJson,
            )
        }
    }

    fun fetchProvidersAndValid(
        path: File,
        force: Boolean,
        subscriptionHeadersJson: String = SubscriptionDeviceHeaders.toJson(Global.application),
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeFetchProvidersAndValid(
                object : FetchCallback {
                    override fun report(statusJson: String) {
                        reportStatus(
                            Json.Default.decodeFromString(
                                FetchStatus.serializer(),
                                statusJson
                            )
                        )
                    }

                    override fun complete(error: String?) {
                        if (error != null)
                            completeExceptionally(ClashException(error))
                        else
                            complete(Unit)
                    }
                },
                path.absolutePath,
                force,
                subscriptionHeadersJson,
            )
        }
    }

    fun load(path: File): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeLoad(this, path.absolutePath)
        }
    }

    fun validateProfile(path: File): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeValidateProfile(this, path.absolutePath)
        }
    }

    /**
     * Parses the profile via mihomo and returns its structural snapshot
     * (rules, proxy-groups, providers). This is the source of truth for UI
     * READ-operations — no YAML parsing should happen in Kotlin.
     *
     * Synchronous JNI call. Profile parsing is in the 10-50ms range on typical
     * configs, so call from a background dispatcher (`Dispatchers.IO`).
     *
     * The call does not hit the network: providers are not fetched, only their
     * paths are rewritten.
     *
     * @throws ClashException with mihomo's verbatim error message when the
     *         profile cannot be parsed (invalid YAML, missing required fields,
     *         etc).
     */
    fun parseProfileSnapshot(path: File): ProfileSnapshot {
        return decodeSnapshotEnvelope(Bridge.nativeParseProfileSnapshot(path.absolutePath))
    }

    /**
     * In-memory variant of [parseProfileSnapshot] for YAML that is not yet
     * (or no longer) on disk — dry-run previews, validation-before-commit,
     * unit tests. Same engine path, no provider patching, no network.
     *
     * @throws ClashException with mihomo's verbatim error message on bad YAML.
     */
    fun parseProfileSnapshotFromYaml(yaml: String): ProfileSnapshot {
        return decodeSnapshotEnvelope(Bridge.nativeParseProfileSnapshotFromBytes(yaml))
    }

    /**
     * Runs mihomo's UnmarshalRawConfig + ParseRawConfig on in-memory YAML and
     * returns mihomo's verbatim error message, or null on success. Use this
     * to validate edited YAML *before* committing it to disk so the user
     * never sees "rules[N] [...]: not found" on a profile that loaded fine
     * a second ago.
     *
     * No disk I/O, no network, no engine state mutation. Safe to call on a
     * background dispatcher from the WRITE pipeline.
     */
    fun validateProfileBytes(yaml: String): String? {
        return Bridge.nativeValidateProfileBytes(yaml)
    }

    private fun decodeSnapshotEnvelope(rawJson: String): ProfileSnapshot {
        val envelope = ProfileSnapshotJson.decodeFromString(
            ProfileSnapshotEnvelope.serializer(),
            rawJson,
        )
        if (!envelope.ok || envelope.snapshot == null) {
            throw ClashException(envelope.error ?: "failed to parse profile")
        }
        return envelope.snapshot
    }

    private val ProfileSnapshotJson = Json {
        ignoreUnknownKeys = true
    }

    fun queryProviders(): List<Provider> {
        val providers =
            Json.Default.decodeFromString(JsonArray.serializer(), Bridge.nativeQueryProviders())

        return List(providers.size) {
            Json.Default.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    fun queryConnectionsSnapshot(): String {
        return Bridge.nativeQueryConnectionsSnapshot()
    }

    fun closeConnection(id: String): Boolean {
        return Bridge.nativeCloseConnection(id)
    }

    fun closeAllConnections(): Int {
        return Bridge.nativeCloseAllConnections()
    }

    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeUpdateProvider(this, type.toString(), name)
        }
    }

    fun queryOverride(slot: OverrideSlot): ConfigurationOverride {
        return try {
            ConfigurationOverrideJson.decodeFromString(
                ConfigurationOverride.serializer(),
                Bridge.nativeReadOverride(slot.ordinal)
            )
        } catch (e: Exception) {
            ConfigurationOverride()
        }
    }

    fun patchOverride(slot: OverrideSlot, configuration: ConfigurationOverride) {
        Bridge.nativeWriteOverride(
            slot.ordinal,
            ConfigurationOverrideJson.encodeToString(
                ConfigurationOverride.serializer(),
                configuration
            )
        )
    }

    fun clearOverride(slot: OverrideSlot) {
        Bridge.nativeClearOverride(slot.ordinal)
    }

    fun queryConfiguration(): UiConfiguration {
        return Json.Default.decodeFromString(
            UiConfiguration.serializer(),
            Bridge.nativeQueryConfiguration()
        )
    }

    fun subscribeLogcat(): ReceiveChannel<LogMessage> {
        return Channel<LogMessage>(32).apply {
            Bridge.nativeSubscribeLogcat(object : LogcatInterface {
                override fun received(jsonPayload: String) {
                    trySend(Json.decodeFromString(LogMessage.serializer(), jsonPayload))
                }
            })
        }
    }
}
