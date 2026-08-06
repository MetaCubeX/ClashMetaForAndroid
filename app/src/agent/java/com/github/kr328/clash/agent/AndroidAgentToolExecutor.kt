package com.github.kr328.clash.agent

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.agent.model.AgentToolExecutionResult
import com.github.kr328.clash.agent.runtime.AgentToolExecutor
import com.github.kr328.clash.agent.settings.AgentBackupStore
import com.github.kr328.clash.agent.tools.AgentExecutableTools
import com.github.kr328.clash.agent.tools.AgentToolSpec
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ProcessExitDiagnostics
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File

class AndroidAgentToolExecutor(
    private val context: Context,
    private val startVpn: suspend () -> Boolean,
    private val stopVpn: suspend () -> Unit,
) : AgentToolExecutor {
    override val tools: List<AgentToolSpec> = AgentExecutableTools.all
    private val backups = AgentBackupStore(context)

    override suspend fun execute(name: String, arguments: JsonObject): AgentToolExecutionResult = when (name) {
        "profiles_list" -> profilesList()
        "profile_read_config" -> profileRead(arguments)
        "profile_create" -> profileCreate(arguments)
        "profile_replace_config" -> profileReplace(arguments)
        "profile_restore_latest" -> profileRestore(arguments)
        "profile_activate" -> profileActivate(arguments)
        "profile_clone" -> profileClone(arguments)
        "profile_update_metadata" -> profileUpdateMetadata(arguments)
        "profile_delete" -> profileDelete(arguments)
        "installed_apps" -> installedApps()
        "access_control_read" -> accessControlRead()
        "access_control_replace" -> accessControlReplace(arguments)
        "vpn_settings_read" -> vpnSettingsRead()
        "vpn_settings_update" -> vpnSettingsUpdate(arguments)
        "network_info" -> networkInfo()
        "logs_recent" -> logsRecent()
        "app_exit_history" -> appExitHistory()
        "runtime_status" -> runtimeStatus()
        "runtime_set_mode" -> runtimeSetMode(arguments)
        "runtime_start" -> runtimeStart()
        "runtime_stop" -> runtimeStop()
        "override_read" -> overrideRead(arguments)
        "override_replace" -> overrideReplace(arguments)
        "override_clear" -> overrideClear(arguments)
        "proxy_groups" -> proxyGroups()
        "proxy_select" -> proxySelect(arguments)
        "proxy_healthcheck" -> proxyHealthcheck(arguments)
        "providers_list" -> providersList()
        "provider_refresh" -> providerRefresh(arguments)
        "connections_list" -> connectionsList()
        "connection_close" -> connectionClose(arguments)
        "connections_close_all" -> connectionsCloseAll()
        else -> AgentToolExecutionResult(false, "Unknown tool: $name", "不支持的操作：$name")
    }

    private suspend fun profilesList(): AgentToolExecutionResult {
        val profiles = withProfile { queryAll() }
        val body = buildJsonObject {
            put("profiles", buildJsonArray {
                profiles.forEach { profile ->
                    add(buildJsonObject {
                        put("id", profile.uuid.toString())
                        put("name", profile.name)
                        put("type", profile.type.name)
                        put("active", profile.active)
                        put("imported", profile.imported)
                        put("pending", profile.pending)
                        put("updated_at", profile.updatedAt)
                    })
                }
            })
        }
        return ok(body.toString(), "已读取 ${profiles.size} 个配置")
    }

    private suspend fun profileRead(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.optionalString("profile_id"))
        val yaml = readProfileConfig(profile.uuid)
        val body = buildJsonObject {
            put("profile_id", profile.uuid.toString())
            put("name", profile.name)
            put("active", profile.active)
            put("sha256", AgentBackupStore.sha256(yaml))
            put("yaml", yaml)
        }
        return ok(body.toString(), "已读取配置“${profile.name}”")
    }

    private suspend fun profileCreate(arguments: JsonObject): AgentToolExecutionResult {
        val name = arguments.requiredString("name").trim().take(80).ifBlank { "AI 配置" }
        val yaml = normalizeYaml(arguments.requiredString("yaml"))
        val activate = arguments.optionalBoolean("activate") ?: true
        val uuid = withProfile { create(Profile.Type.File, name) }
        try {
            withProfile {
                writeProfileConfig(uuid, yaml)
                commit(uuid)
                val profile = queryByUUID(uuid) ?: error("配置提交后不存在")
                if (activate) setActive(profile)
            }
        } catch (error: Throwable) {
            runCatching { withProfile { delete(uuid) } }
            throw IllegalArgumentException("配置验证失败，未创建：${error.message}", error)
        }
        val body = buildJsonObject {
            put("profile_id", uuid.toString())
            put("name", name)
            put("active", activate)
            put("sha256", AgentBackupStore.sha256(yaml))
            put("validated", true)
        }
        return ok(body.toString(), "配置“$name”已通过验证${if (activate) "并启用" else ""}")
    }

    private suspend fun profileReplace(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.optionalString("profile_id"))
        val expected = arguments.requiredString("expected_sha256")
        val replacement = normalizeYaml(arguments.requiredString("yaml"))
        val activate = arguments.optionalBoolean("activate") ?: profile.active
        val current = readProfileConfig(profile.uuid)
        val currentHash = AgentBackupStore.sha256(current)
        require(currentHash.equals(expected, ignoreCase = true)) {
            "配置已被其他操作修改（当前 SHA-256 为 $currentHash），请重新读取后再修改"
        }
        if (current == replacement) return ok(
            "{\"unchanged\":true,\"sha256\":\"$currentHash\"}",
            "配置内容没有变化",
        )

        backups.create(profile.uuid, current)
        var replacementWritten = false
        try {
            withProfile {
                writeProfileConfig(profile.uuid, replacement, expected)
                replacementWritten = true
                commit(profile.uuid)
                val committed = queryByUUID(profile.uuid) ?: error("提交后无法读取配置")
                if (activate) setActive(committed)
            }
        } catch (error: Throwable) {
            val rollback = if (replacementWritten) runCatching {
                withProfile {
                    writeProfileConfig(profile.uuid, current)
                    commit(profile.uuid)
                    if (profile.active) queryByUUID(profile.uuid)?.let { setActive(it) }
                }
            } else Result.success(Unit)
            val suffix = if (rollback.isSuccess) "，原配置已自动恢复" else "，且自动恢复失败：${rollback.exceptionOrNull()?.message}"
            throw IllegalArgumentException("配置验证/应用失败：${error.message}$suffix", error)
        }

        val newHash = AgentBackupStore.sha256(replacement)
        val body = buildJsonObject {
            put("profile_id", profile.uuid.toString())
            put("sha256", newHash)
            put("validated", true)
            put("backup_created", true)
            put("active", activate)
        }
        return ok(body.toString(), "配置“${profile.name}”已验证并安全应用")
    }

    private suspend fun profileRestore(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.requiredString("profile_id"))
        val backup = backups.latest(profile.uuid) ?: error("没有可用备份")
        val current = readProfileConfig(profile.uuid)
        val content = backups.read(backup)
        backups.create(profile.uuid, current)
        try {
            withProfile {
                writeProfileConfig(profile.uuid, content)
                commit(profile.uuid)
                queryByUUID(profile.uuid)?.let { if (profile.active) setActive(it) }
            }
        } catch (error: Throwable) {
            val rollback = runCatching {
                withProfile {
                    writeProfileConfig(profile.uuid, current)
                    commit(profile.uuid)
                    if (profile.active) queryByUUID(profile.uuid)?.let { setActive(it) }
                }
            }
            val suffix = if (rollback.isSuccess) "，恢复前配置已重新应用" else "，且回退失败：${rollback.exceptionOrNull()?.message}"
            throw IllegalArgumentException("备份恢复失败：${error.message}$suffix", error)
        }
        return ok(
            "{\"restored\":true,\"sha256\":\"${backup.sha256}\",\"timestamp\":${backup.timestamp}}",
            "已恢复“${profile.name}”的最近备份",
        )
    }

    private suspend fun profileActivate(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.requiredString("profile_id"))
        require(profile.imported) { "配置尚未成功提交，不能启用" }
        withProfile { setActive(profile) }
        return ok("{\"active_profile_id\":\"${profile.uuid}\"}", "已启用“${profile.name}”")
    }

    private suspend fun profileClone(arguments: JsonObject): AgentToolExecutionResult {
        val source = resolveProfile(arguments.requiredString("profile_id"))
        val uuid = withProfile { clone(source.uuid) }
        return ok("{\"profile_id\":\"$uuid\",\"pending\":true}", "已复制“${source.name}”")
    }

    private suspend fun profileDelete(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.requiredString("profile_id"))
        withProfile { delete(profile.uuid) }
        return ok("{\"deleted_profile_id\":\"${profile.uuid}\"}", "已删除“${profile.name}”")
    }

    private suspend fun profileUpdateMetadata(arguments: JsonObject): AgentToolExecutionResult {
        val profile = resolveProfile(arguments.requiredString("profile_id"))
        val name = arguments.optionalString("name")?.trim()?.take(80)?.takeIf(String::isNotBlank) ?: profile.name
        val source = arguments.optionalString("source")?.trim() ?: profile.source
        val interval = arguments.optionalLong("update_interval_minutes")?.let { minutes ->
            require(minutes == 0L || minutes in 15..525_600) { "更新间隔必须为 0（禁用）或至少 15 分钟" }
            java.util.concurrent.TimeUnit.MINUTES.toMillis(minutes)
        } ?: profile.interval
        try {
            withProfile {
                patch(profile.uuid, name, source, interval, profile.ageSecretKey)
                commit(profile.uuid)
            }
        } catch (error: Throwable) {
            runCatching {
                withProfile {
                    if (profile.pending) {
                        patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey)
                    } else {
                        release(profile.uuid)
                    }
                }
            }
            throw IllegalArgumentException("配置资料验证/更新失败：${error.message}", error)
        }
        return ok(
            buildJsonObject {
                put("profile_id", profile.uuid.toString())
                put("name", name)
                put("source_changed", source != profile.source)
                put("update_interval_minutes", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(interval))
                put("validated", true)
            }.toString(),
            "配置“$name”的资料已验证并更新",
        )
    }

    private fun installedApps(): AgentToolExecutionResult {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledPackages(0)
        }.asSequence().mapNotNull { info ->
            val application = info.applicationInfo ?: return@mapNotNull null
            val launchable = pm.getLaunchIntentForPackage(info.packageName) != null
            val system = application.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                application.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (!launchable && system) return@mapNotNull null
            AppEntry(
                label = application.loadLabel(pm).toString(),
                packageName = info.packageName,
                uid = application.uid,
                system = system,
            )
        }.distinctBy(AppEntry::packageName).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }).toList()
        val body = buildJsonObject {
            put("apps", buildJsonArray {
                packages.forEach { app -> add(buildJsonObject {
                    put("label", app.label)
                    put("package", app.packageName)
                    put("uid", app.uid)
                    put("system", app.system)
                }) }
            })
        }
        return ok(body.toString(), "已读取 ${packages.size} 个可路由应用")
    }

    private fun accessControlRead(): AgentToolExecutionResult {
        val store = ServiceStore(context)
        return ok(buildJsonObject {
            put("mode", accessModeName(store.accessControlMode))
            put("packages", buildJsonArray { store.accessControlPackages.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }.toString(), "已读取应用访问控制")
    }

    private suspend fun accessControlReplace(arguments: JsonObject): AgentToolExecutionResult {
        val mode = when (arguments.requiredString("mode").lowercase()) {
            "accept_all" -> AccessControlMode.AcceptAll
            "accept_selected" -> AccessControlMode.AcceptSelected
            "deny_selected" -> AccessControlMode.DenySelected
            else -> error("访问控制模式必须是 accept_all、accept_selected 或 deny_selected")
        }
        val packages = arguments["packages"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
            ?: error("缺少参数 packages")
        val installed = installedPackageNames()
        val unknown = packages - installed
        require(unknown.isEmpty()) { "以下包名未安装：${unknown.take(12).joinToString()}" }
        val store = ServiceStore(context)
        val selected = if (mode == AccessControlMode.AcceptAll) emptySet() else packages
        val changed = store.accessControlMode != mode || store.accessControlPackages != selected
        store.accessControlMode = mode
        store.accessControlPackages = selected
        val restarted = restartIfRequested(changed && (arguments.optionalBoolean("restart_if_running") ?: true))
        return ok(buildJsonObject {
            put("mode", accessModeName(mode))
            put("package_count", store.accessControlPackages.size)
            put("vpn_restarted", restarted)
        }.toString(), "已应用应用访问控制${if (restarted) "并重启 VPN" else ""}")
    }

    private fun vpnSettingsRead(): AgentToolExecutionResult {
        val service = ServiceStore(context)
        val ui = UiStore(context)
        return ok(buildJsonObject {
            put("enable_vpn", ui.enableVpn)
            put("bypass_private_network", service.bypassPrivateNetwork)
            put("dns_hijacking", service.dnsHijacking)
            put("system_proxy", service.systemProxy)
            put("allow_bypass", service.allowBypass)
            put("allow_ipv6", service.allowIpv6)
            put("tun_stack_mode", service.tunStackMode)
            put("dynamic_notification", service.dynamicNotification)
        }.toString(), "已读取 Android VPN 设置")
    }

    private suspend fun vpnSettingsUpdate(arguments: JsonObject): AgentToolExecutionResult {
        val service = ServiceStore(context)
        val ui = UiStore(context)
        var changed = false
        arguments.optionalBoolean("enable_vpn")?.let { changed = changed || ui.enableVpn != it; ui.enableVpn = it }
        arguments.optionalBoolean("bypass_private_network")?.let { changed = changed || service.bypassPrivateNetwork != it; service.bypassPrivateNetwork = it }
        arguments.optionalBoolean("dns_hijacking")?.let { changed = changed || service.dnsHijacking != it; service.dnsHijacking = it }
        arguments.optionalBoolean("system_proxy")?.let { changed = changed || service.systemProxy != it; service.systemProxy = it }
        arguments.optionalBoolean("allow_bypass")?.let { changed = changed || service.allowBypass != it; service.allowBypass = it }
        arguments.optionalBoolean("allow_ipv6")?.let { changed = changed || service.allowIpv6 != it; service.allowIpv6 = it }
        arguments.optionalBoolean("dynamic_notification")?.let { changed = changed || service.dynamicNotification != it; service.dynamicNotification = it }
        arguments.optionalString("tun_stack_mode")?.lowercase()?.let {
            require(it in setOf("system", "gvisor", "mixed")) { "TUN 栈必须是 system、gvisor 或 mixed" }
            changed = changed || service.tunStackMode != it
            service.tunStackMode = it
        }
        require(changed || arguments.keys.any { it != "restart_if_running" }) { "没有提供任何要修改的 VPN 设置" }
        val restarted = restartIfRequested(changed && (arguments.optionalBoolean("restart_if_running") ?: true))
        return ok(buildJsonObject {
            put("updated", changed)
            put("vpn_restarted", restarted)
        }.toString(), "已更新 Android VPN 设置${if (restarted) "并重启 VPN" else ""}")
    }

    private fun networkInfo(): AgentToolExecutionResult {
        val connectivity = checkNotNull(context.getSystemService<ConnectivityManager>())
        val network = connectivity.activeNetwork ?: return ok("{\"connected\":false}", "当前没有活动网络")
        val capabilities = connectivity.getNetworkCapabilities(network)
        val links = connectivity.getLinkProperties(network)
        val transports = buildList {
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cellular")
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
        }
        val body = buildJsonObject {
            put("connected", true)
            put("transports", buildJsonArray { transports.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            put("metered", connectivity.isActiveNetworkMetered)
            put("interface", links?.interfaceName ?: "")
            put("dns_servers", buildJsonArray { links?.dnsServers?.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.hostAddress.orEmpty())) } })
            put("routes", buildJsonArray { links?.routes?.take(32)?.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.toString())) } })
        }
        return ok(body.toString(), "已读取当前网络信息")
    }

    private fun logsRecent(): AgentToolExecutionResult {
        val file = context.cacheDir.resolve("logs").listFiles()
            ?.filter(File::isFile)?.maxByOrNull(File::lastModified)
            ?: return ok("{\"available\":false,\"lines\":[]}", "当前没有已保存的日志采集")
        val lines = file.useLines { sequence -> sequence.takeLastBounded(300) }
        val body = buildJsonObject {
            put("available", true)
            put("file", file.name)
            put("lines", buildJsonArray { lines.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.take(2000))) } })
        }
        return ok(body.toString().take(120_000), "已读取最近 ${lines.size} 行已保存日志")
    }

    private fun appExitHistory(): AgentToolExecutionResult {
        val body = ProcessExitDiagnostics.read(context)
        return ok(body, "已读取 Android 记录的 VPN 进程退出历史")
    }

    private suspend fun runtimeStatus(): AgentToolExecutionResult {
        val active = withProfile { queryActive() }
        val body = withClash {
            val state = queryTunnelState()
            val groups = queryProxyGroupNames(true)
            buildJsonObject {
                put("vpn_running", com.github.kr328.clash.remote.Remote.broadcasts.clashRunning)
                put("active_profile", active?.name ?: "")
                put("active_profile_id", active?.uuid?.toString() ?: "")
                put("mode", state.mode.name)
                put("traffic_total", queryTrafficTotal())
                put("selectable_groups", buildJsonArray { groups.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("provider_count", queryProviders().size)
            }
        }
        return ok(body.toString(), "已读取运行状态")
    }

    private suspend fun runtimeStart(): AgentToolExecutionResult {
        if (Remote.broadcasts.clashRunning) return ok("{\"started\":true,\"already_running\":true}", "代理已经在运行")
        val started = startVpn()
        return if (started) ok("{\"started\":true}", "代理已启动")
        else AgentToolExecutionResult(false, "{\"started\":false,\"reason\":\"vpn_permission_denied\"}", "未获得 VPN 权限")
    }

    private suspend fun runtimeSetMode(arguments: JsonObject): AgentToolExecutionResult {
        val requested = arguments.requiredString("mode")
        val mode = TunnelState.Mode.entries.firstOrNull { it.name.equals(requested, true) }
            ?: error("不支持的模式：$requested")
        withClash {
            val override = queryOverride(Clash.OverrideSlot.Session)
            override.mode = mode
            patchOverride(Clash.OverrideSlot.Session, override)
        }
        return ok("{\"mode\":${quote(mode.name.lowercase())}}", "运行模式已切换为 ${mode.name}")
    }

    private suspend fun runtimeStop(): AgentToolExecutionResult {
        if (!Remote.broadcasts.clashRunning) return ok("{\"stopped\":true,\"already_stopped\":true}", "代理已经停止")
        stopVpn()
        for (attempt in 0 until 50) {
            if (!Remote.broadcasts.clashRunning) break
            delay(100)
        }
        require(!Remote.broadcasts.clashRunning) { "VPN 未能及时停止" }
        return ok("{\"stopped\":true}", "代理已停止")
    }

    private suspend fun overrideRead(arguments: JsonObject): AgentToolExecutionResult {
        val slot = overrideSlot(arguments.requiredString("slot"))
        val value = withClash { queryOverride(slot) }
        val content = OVERRIDE_JSON.encodeToString(ConfigurationOverride.serializer(), value)
        return ok(
            buildJsonObject { put("slot", slot.name.lowercase()); put("override", Json.parseToJsonElement(content)) }.toString(),
            "已读取 ${slot.name.lowercase()} 覆写设置",
        )
    }

    private suspend fun overrideReplace(arguments: JsonObject): AgentToolExecutionResult {
        val slot = overrideSlot(arguments.requiredString("slot"))
        val raw = arguments.requiredString("json")
        val value = runCatching { OVERRIDE_JSON.decodeFromString(ConfigurationOverride.serializer(), raw) }
            .getOrElse { throw IllegalArgumentException("覆写 JSON 无效：${it.message}", it) }
        withClash { patchOverride(slot, value) }
        return ok("{\"slot\":${quote(slot.name.lowercase())},\"applied\":true}", "已应用 ${slot.name.lowercase()} 覆写设置")
    }

    private suspend fun overrideClear(arguments: JsonObject): AgentToolExecutionResult {
        val slot = overrideSlot(arguments.requiredString("slot"))
        withClash { clearOverride(slot) }
        return ok("{\"slot\":${quote(slot.name.lowercase())},\"cleared\":true}", "已清空 ${slot.name.lowercase()} 覆写设置")
    }

    private suspend fun proxyGroups(): AgentToolExecutionResult {
        val body = withClash {
            val names = queryProxyGroupNames(false)
            buildJsonObject {
                put("groups", buildJsonArray {
                    names.forEach { name ->
                        val group = queryProxyGroup(name, ProxySort.Default)
                        add(buildJsonObject {
                            put("name", name)
                            put("selected", group.now)
                            put("proxies", buildJsonArray { group.proxies.forEach { proxy ->
                                add(kotlinx.serialization.json.JsonPrimitive(proxy.name))
                            } })
                        })
                    }
                })
            }
        }
        return ok(body.toString(), "已读取代理组")
    }

    private suspend fun proxySelect(arguments: JsonObject): AgentToolExecutionResult {
        val group = arguments.requiredString("group")
        val proxy = arguments.requiredString("proxy")
        val changed = withClash { patchSelector(group, proxy) }
        require(changed) { "代理组或节点不存在，或者该组不可选择" }
        return ok("{\"group\":${quote(group)},\"selected\":${quote(proxy)}}", "“$group”已切换到“$proxy”")
    }

    private suspend fun proxyHealthcheck(arguments: JsonObject): AgentToolExecutionResult {
        val group = arguments.requiredString("group")
        withClash { healthCheck(group) }
        return ok("{\"group\":${quote(group)},\"checked\":true}", "已完成“$group”健康检查")
    }

    private suspend fun providersList(): AgentToolExecutionResult {
        val providers = withClash { queryProviders() }
        val body = buildJsonObject {
            put("providers", buildJsonArray {
                providers.forEach { provider -> add(buildJsonObject {
                    put("name", provider.name)
                    put("type", provider.type.name)
                    put("updated_at", provider.updatedAt)
                    put("vehicle_type", provider.vehicleType.name)
                }) }
            })
        }
        return ok(body.toString(), "已读取 ${providers.size} 个 Provider")
    }

    private suspend fun providerRefresh(arguments: JsonObject): AgentToolExecutionResult {
        val requestedType = arguments.requiredString("type")
        val type = Provider.Type.entries.firstOrNull { it.name.equals(requestedType, true) }
            ?: error("不支持的 Provider 类型：$requestedType")
        val name = arguments.requiredString("name")
        withClash { updateProvider(type, name) }
        return ok("{\"updated\":true}", "已刷新 Provider“$name”")
    }

    private suspend fun connectionsList(): AgentToolExecutionResult {
        val content = withClash { queryConnections() }
        return ok(content.take(120_000), "已读取活动连接")
    }

    private suspend fun connectionClose(arguments: JsonObject): AgentToolExecutionResult {
        val id = arguments.requiredString("id")
        val closed = withClash { closeConnection(id) }
        require(closed) { "连接不存在或已经关闭" }
        return ok("{\"closed\":true,\"id\":${quote(id)}}", "连接已关闭")
    }

    private suspend fun connectionsCloseAll(): AgentToolExecutionResult {
        withClash { closeAllConnections() }
        return ok("{\"closed_all\":true}", "全部活动连接已关闭")
    }

    private suspend fun resolveProfile(id: String?): Profile = withProfile {
        if (id.isNullOrBlank()) queryActive() else runCatching { UUID.fromString(id) }.getOrNull()?.let { queryByUUID(it) }
    } ?: error(if (id.isNullOrBlank()) "当前没有已启用配置" else "找不到配置 $id")

    private suspend fun readProfileConfig(uuid: UUID): String {
        val directory = context.cacheDir.resolve("agent-transfer").apply { mkdirs() }
        val temporary = File.createTempFile("config-read-", ".yaml", directory)
        try {
            val descriptor = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            descriptor.use { destination -> withProfile { copyConfiguration(uuid, destination) } }
            return withContext(Dispatchers.IO) { temporary.readText() }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun writeProfileConfig(uuid: UUID, content: String, expectedSha256: String? = null) {
        val directory = context.cacheDir.resolve("agent-transfer").apply { mkdirs() }
        val temporary = withContext(Dispatchers.IO) {
            File.createTempFile("config-", ".yaml", directory).apply { writeText(content) }
        }
        try {
            val descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { source -> withProfile { replaceConfiguration(uuid, source, expectedSha256) } }
        } finally {
            temporary.delete()
        }
    }

    private fun normalizeYaml(yaml: String): String = yaml.trim().let {
        require(it.isNotBlank()) { "YAML 不能为空" }
        if (it.endsWith('\n')) it else "$it\n"
    }

    private fun ok(content: String, summary: String) = AgentToolExecutionResult(true, content, summary)
    private fun quote(value: String) = kotlinx.serialization.json.JsonPrimitive(value).toString()

    private fun JsonObject.requiredString(name: String): String = this[name]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank) ?: error("缺少参数 $name")
    private fun JsonObject.optionalString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.optionalBoolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.optionalLong(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    private fun installedPackageNames(): Set<String> {
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getInstalledPackages(0)
        }
        return packages.mapTo(mutableSetOf()) { it.packageName }
    }

    private suspend fun restartIfRequested(restart: Boolean): Boolean {
        if (!restart || !Remote.broadcasts.clashRunning) return false
        stopVpn()
        for (attempt in 0 until 50) {
            if (!Remote.broadcasts.clashRunning) break
            delay(100)
        }
        require(!Remote.broadcasts.clashRunning) { "VPN 未能及时停止，设置已保存但尚未生效" }
        require(startVpn()) { "设置已保存，但 VPN 重新启动失败" }
        return true
    }

    private fun accessModeName(mode: AccessControlMode): String = when (mode) {
        AccessControlMode.AcceptAll -> "accept_all"
        AccessControlMode.AcceptSelected -> "accept_selected"
        AccessControlMode.DenySelected -> "deny_selected"
    }

    private fun Sequence<String>.takeLastBounded(limit: Int): List<String> {
        val buffer = ArrayDeque<String>(limit)
        forEach { line ->
            if (buffer.size == limit) buffer.removeFirst()
            buffer.addLast(line)
        }
        return buffer.toList()
    }

    private fun overrideSlot(value: String): Clash.OverrideSlot = when (value.lowercase()) {
        "session" -> Clash.OverrideSlot.Session
        "persist", "persistent" -> Clash.OverrideSlot.Persist
        else -> error("覆写槽必须是 session 或 persist")
    }

    private data class AppEntry(val label: String, val packageName: String, val uid: Int, val system: Boolean)

    companion object {
        private val OVERRIDE_JSON = Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
        }
    }

}
