package universe.agentic.device.clash

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.util.downloadBytes
import com.github.kr328.clash.core.util.uploadBytes
import com.github.kr328.clash.service.ClashManager
import com.github.kr328.clash.service.ClashService
import com.github.kr328.clash.service.ProfileManager
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.TunService
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.sendBroadcastSelf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID

class McpBridge(context: Context) {
    private val app = context.applicationContext
    private val clash = ClashManager(app)
    private val profiles = ProfileManager(app)

    fun invoke(name: String, args: JsonObject): String {
        return when (name) {
            "status" -> status()
            "list_proxies" -> listProxies()
            "select_proxy" -> selectProxy(required(args, "group"), required(args, "name"))
            "list_profiles" -> listProfiles()
            "set_profile" -> setProfile(required(args, "uuid"))
            "reload" -> command { reload() }
            "start" -> command { start() }
            "stop" -> command { stop() }
            else -> throw IllegalArgumentException("unknown tool")
        }
    }

    fun close() {
        clash.cancelAndJoinBlocking()
        profiles.cancelAndJoinBlocking()
    }

    private fun status(): String {
        if (!StatusProvider.serviceRunning) {
            return buildJsonObject { put("running", false) }.toString()
        }
        val now = Clash.queryTrafficNow()
        val total = Clash.queryTrafficTotal()
        return buildJsonObject {
            put("running", true)
            put("mode", Clash.queryTunnelState().mode.name.lowercase())
            put("uploadBytesPerSecond", now.uploadBytes())
            put("downloadBytesPerSecond", now.downloadBytes())
            put("uploadBytes", total.uploadBytes())
            put("downloadBytes", total.downloadBytes())
        }.toString()
    }

    private fun listProxies(): String {
        requireRunning()
        val groups = clash.queryProxyGroupNames(false).map { name ->
            val group = clash.queryProxyGroup(name, ProxySort.Default)
            buildJsonObject {
                put("name", name)
                put("type", group.type)
                put("now", group.now)
                put("proxies", buildJsonArray {
                    group.proxies.forEach { proxy ->
                        add(buildJsonObject {
                            put("name", proxy.name)
                            put("type", proxy.type)
                            put("delay", proxy.delay)
                        })
                    }
                })
            }
        }
        return buildJsonObject {
            put("groups", buildJsonArray { groups.forEach { add(it) } })
        }.toString()
    }

    private fun selectProxy(group: String, name: String): String {
        requireRunning()
        val applied = clash.patchSelector(group, name)
        return buildJsonObject {
            put("ok", applied)
            put("group", group)
            put("name", name)
        }.toString()
    }

    private fun listProfiles(): String {
        val rows = runBlocking { profiles.queryAll() }
        val active = runBlocking { profiles.queryActive() }?.uuid
        return buildJsonObject {
            put("profiles", buildJsonArray {
                rows.forEach { profile ->
                    add(buildJsonObject {
                        put("uuid", profile.uuid.toString())
                        put("name", profile.name)
                        put("type", profile.type.name)
                        put("active", profile.uuid == active)
                    })
                }
            })
        }.toString()
    }

    private fun setProfile(uuid: String): String {
        val id = try {
            UUID.fromString(uuid)
        } catch (_: Exception) {
            throw IllegalArgumentException("invalid uuid")
        }
        val profile = runBlocking { profiles.queryByUUID(id) }
            ?: throw IllegalArgumentException("profile not found")
        runBlocking { profiles.setActive(profile) }
        return buildJsonObject {
            put("ok", true)
            put("uuid", id.toString())
            put("name", profile.name)
        }.toString()
    }

    private fun reload() {
        requireRunning()
        app.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_RELOAD))
    }

    private fun start() {
        val vpn = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
            .getBoolean("enable_vpn", true)
        if (vpn) {
            if (VpnService.prepare(app) != null) {
                throw IllegalStateException("vpn consent is required; open AgenticClash and connect once")
            }
            app.startForegroundServiceCompat(Intent(app, TunService::class.java))
        } else {
            app.startForegroundServiceCompat(Intent(app, ClashService::class.java))
        }
    }

    private fun stop() {
        app.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
    }

    private fun requireRunning() {
        if (!StatusProvider.serviceRunning) throw IllegalStateException("tunnel is not running")
    }

    private fun command(block: () -> Unit): String {
        block()
        return buildJsonObject { put("ok", true) }.toString()
    }

    private fun required(args: JsonObject, key: String): String {
        val value = (args[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (value.isEmpty()) throw IllegalArgumentException("missing $key")
        return value
    }
}
