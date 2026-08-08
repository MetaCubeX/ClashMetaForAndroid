package com.github.kr328.clash.agent.tools

import com.github.kr328.clash.agent.authorization.AgentOperationRisk.CRITICAL
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.HIGH
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.LOW
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.MEDIUM
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.READ_ONLY
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object AgentExecutableTools {
    val all: List<AgentToolSpec> = listOf(
        tool("profiles_list", "List every profile and identify the active profile.", READ_ONLY),
        tool("profile_read_config", "Read the complete YAML configuration and its SHA-256. This is the source of truth for any edit; secrets are included only so edits can preserve them, never repeat secrets in chat.", READ_ONLY,
            string("profile_id", "Profile UUID; omit to use the active profile.", required = false)),
        tool("profile_create", "Create, validate, commit, and optionally activate a complete profile from YAML. Use when starting from zero or importing a full config.", HIGH,
            string("name", "Human-friendly profile name."),
            string("yaml", "Complete valid mihomo YAML configuration. Never invent credentials; ask the user for missing values."),
            boolean("activate", "Activate after successful validation.", required = false)),
        tool("profile_replace_config", "Transactionally replace the complete YAML of an existing profile: validate with the bundled core, keep a backup, roll back on failure. Return the FULL YAML and preserve every field the user did not ask to change, including comments when possible.", HIGH,
            string("profile_id", "Profile UUID; omit to use the active profile.", required = false),
            string("expected_sha256", "SHA-256 returned by profile_read_config. Prevents overwriting concurrent edits."),
            string("yaml", "Complete replacement YAML. Include all existing sections, even untouched ones."),
            boolean("activate", "Activate the profile after commit.", required = false)),
        tool("profile_restore_latest", "Restore the most recent known-good configuration backup.", HIGH,
            string("profile_id", "Profile UUID.")),
        tool("profile_activate", "Activate an already imported profile.", HIGH, string("profile_id", "Profile UUID.")),
        tool("profile_clone", "Clone a profile into a new editable file profile.", MEDIUM, string("profile_id", "Profile UUID.")),
        tool("profile_update_metadata", "Rename a profile or update its subscription source and refresh interval without changing its YAML.", HIGH,
            string("profile_id", "Profile UUID."),
            string("name", "New profile name; omit to preserve.", required = false),
            string("source", "New subscription URL/source; omit to preserve. Never invent this value.", required = false),
            integer("update_interval_minutes", "Automatic update interval in minutes; 0 disables it. Omit to preserve.", required = false)),
        tool("profile_delete", "Permanently delete a profile.", CRITICAL, string("profile_id", "Profile UUID.")),
        tool("installed_apps", "List installed launchable/user apps with package names for app-aware routing rules.", READ_ONLY),
        tool("access_control_read", "Read Android VPN per-app access-control mode and selected package names.", READ_ONLY),
        tool("access_control_replace", "Replace Android VPN per-app access control. Use installed_apps first and only pass exact installed package names.", HIGH,
            string("mode", "accept_all, accept_selected, or deny_selected."),
            stringArray("packages", "Complete package-name selection; use an empty array for accept_all."),
            boolean("restart_if_running", "Restart the VPN immediately so the change takes effect. Defaults to true.", required = false)),
        tool("vpn_settings_read", "Read Android VPN integration settings such as TUN stack, DNS hijacking, IPv6, LAN bypass, and system proxy.", READ_ONLY),
        tool("vpn_settings_update", "Partially update Android VPN integration settings. Omitted fields are preserved.", HIGH,
            boolean("enable_vpn", "Use Android VPN/TUN instead of proxy-only service.", required = false),
            boolean("bypass_private_network", "Bypass private networks.", required = false),
            boolean("dns_hijacking", "Hijack DNS into mihomo.", required = false),
            boolean("system_proxy", "Expose Android system proxy when supported.", required = false),
            boolean("allow_bypass", "Allow applications to bypass VPN.", required = false),
            boolean("allow_ipv6", "Route IPv6 through VPN.", required = false),
            string("tun_stack_mode", "system, gvisor, or mixed.", required = false),
            boolean("dynamic_notification", "Show live traffic in the notification.", required = false),
            boolean("restart_if_running", "Restart the VPN immediately so network-affecting changes take effect. Defaults to true.", required = false)),
        tool("network_info", "Read active Android network, transports, DNS servers, routes, metering, and validation state.", READ_ONLY),
        tool("logs_recent", "Read the tail of the newest saved mihomo log capture for diagnosis. Returns unavailable when no capture has been recorded.", READ_ONLY),
        tool("app_exit_history", "Read Android's system process-exit history for the VPN process, including low-memory kills, Java/native crashes, ANRs, user stops, and signals. Android 11 or newer.", READ_ONLY),
        tool("runtime_status", "Read VPN state, active profile, tunnel mode, traffic totals, proxy groups, and providers.", READ_ONLY),
        tool("runtime_set_mode", "Set the current session tunnel mode (rule, global, direct, or script).", MEDIUM,
            string("mode", "One of: rule, global, direct, script.")),
        tool("runtime_start", "Start the Android VPN. May require the system VPN consent dialog.", HIGH),
        tool("runtime_stop", "Stop the Android VPN.", HIGH),
        tool("override_read", "Read the complete app-supported session or persistent override configuration as JSON. Secrets are included only to preserve them during edits; never repeat them in chat.", READ_ONLY,
            string("slot", "session or persist.")),
        tool("override_replace", "Replace the complete app-supported session or persistent override configuration from JSON. Preserve unrelated fields and secrets.", HIGH,
            string("slot", "session or persist."),
            string("json", "Complete ConfigurationOverride JSON.")),
        tool("override_clear", "Clear all overrides in the selected slot.", HIGH,
            string("slot", "session or persist.")),
        tool("proxy_groups", "Read selectable proxy groups, members, current selections, and latency.", READ_ONLY),
        tool("proxy_select", "Select a proxy or policy in a running selector group.", LOW,
            string("group", "Selector group name."), string("proxy", "Member name.")),
        tool("proxy_healthcheck", "Run health checks for a proxy group.", LOW, string("group", "Group name.")),
        tool("providers_list", "List runtime proxy and rule providers.", READ_ONLY),
        tool("provider_refresh", "Refresh one runtime provider.", MEDIUM,
            string("type", "Provider type exactly as returned by providers_list."), string("name", "Provider name.")),
        tool("connections_list", "Read active connections, destinations, matched rules, chains, upload, and download. Results may be truncated.", READ_ONLY),
        tool("connection_close", "Close one active connection by ID.", LOW, string("id", "Connection ID.")),
        tool("connections_close_all", "Close every active connection.", HIGH),
    )

    private data class Parameter(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean,
    )

    private fun string(name: String, description: String, required: Boolean = true) =
        Parameter(name, "string", description, required)

    private fun boolean(name: String, description: String, required: Boolean = true) =
        Parameter(name, "boolean", description, required)

    private fun integer(name: String, description: String, required: Boolean = true) =
        Parameter(name, "integer", description, required)

    private fun stringArray(name: String, description: String, required: Boolean = true) =
        Parameter(name, "array", description, required)

    private fun tool(
        name: String,
        description: String,
        risk: com.github.kr328.clash.agent.authorization.AgentOperationRisk,
        vararg parameters: Parameter,
    ) = AgentToolSpec(
        name,
        description,
        risk,
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                parameters.forEach { parameter ->
                    putJsonObject(parameter.name) {
                        put("type", parameter.type)
                        put("description", parameter.description)
                        if (parameter.type == "array") {
                            putJsonObject("items") { put("type", "string") }
                        }
                    }
                }
            }
            put("additionalProperties", false)
            put("required", buildJsonArray {
                parameters.filter(Parameter::required).forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) }
            })
        },
    )
}
