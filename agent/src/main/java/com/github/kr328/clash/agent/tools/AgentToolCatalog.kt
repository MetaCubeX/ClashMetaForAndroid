package com.github.kr328.clash.agent.tools

import com.github.kr328.clash.agent.authorization.AgentOperationRisk.CRITICAL
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.HIGH
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.LOW
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.MEDIUM
import com.github.kr328.clash.agent.authorization.AgentOperationRisk.READ_ONLY

/**
 * Stable public names for operations exposed to a model.
 *
 * Keeping this catalog explicit prevents a model from manufacturing privileged
 * method names and gives the authorization layer one authoritative risk map.
 */
object AgentToolCatalog {
    val tools: List<AgentToolDescriptor> = listOf(
        tool("runtime.status", "Read VPN, profile, network, traffic, and core status.", READ_ONLY),
        tool("runtime.start", "Start the VPN using the active validated profile.", HIGH),
        tool("runtime.stop", "Stop the active VPN service.", HIGH),
        tool("runtime.restart", "Restart the VPN using the active profile.", HIGH),
        tool("runtime.mode.set", "Change the running tunnel mode.", MEDIUM),
        tool("runtime.snapshot", "Collect a redacted diagnostic snapshot.", READ_ONLY),

        tool("profiles.list", "List all local, remote, and agent-managed profiles.", READ_ONLY),
        tool("profiles.get", "Read one redacted profile and its metadata.", READ_ONLY),
        tool("profiles.create", "Create a staged agent-managed profile.", MEDIUM),
        tool("profiles.clone", "Clone a profile into an agent-managed draft.", MEDIUM),
        tool("profiles.rename", "Rename a profile.", LOW),
        tool("profiles.activate", "Validate and activate a profile.", HIGH),
        tool("profiles.refresh", "Refresh a remote profile or provider.", MEDIUM),
        tool("profiles.delete", "Delete a profile and its local history.", CRITICAL),
        tool("profiles.rollback", "Restore and activate an earlier profile revision.", HIGH),

        tool("proxies.list", "List proxies with secrets redacted.", READ_ONLY),
        tool("proxies.add", "Add a proxy to a staged profile revision.", MEDIUM),
        tool("proxies.update", "Update a proxy while preserving unspecified fields.", MEDIUM),
        tool("proxies.delete", "Delete a proxy and repair or report references.", HIGH),
        tool("proxies.import_uri", "Parse and stage one or more proxy URIs.", MEDIUM),
        tool("proxies.test", "Measure proxy reachability and latency.", LOW),
        tool("proxies.deduplicate", "Find or merge semantically duplicate proxies.", MEDIUM),

        tool("groups.list", "List proxy groups and their selected policies.", READ_ONLY),
        tool("groups.create", "Create a proxy group.", MEDIUM),
        tool("groups.update", "Update a proxy group and validate its references.", MEDIUM),
        tool("groups.delete", "Delete a proxy group and repair or report references.", HIGH),
        tool("groups.reorder", "Reorder a group's members.", LOW),
        tool("groups.select", "Select the active policy for a selector group.", LOW),
        tool("groups.healthcheck", "Run group health checks.", LOW),

        tool("rules.list", "Read ordered routing rules.", READ_ONLY),
        tool("rules.add", "Add a validated routing rule at an explicit position.", MEDIUM),
        tool("rules.update", "Update a routing rule.", MEDIUM),
        tool("rules.delete", "Delete a routing rule.", MEDIUM),
        tool("rules.move", "Move a routing rule while preserving order.", MEDIUM),
        tool("rules.lint", "Find invalid, duplicate, shadowed, or unreachable rules.", READ_ONLY),
        tool("rules.simulate", "Explain the expected policy for a domain, IP, or app.", READ_ONLY),

        tool("dns.get", "Read effective DNS configuration.", READ_ONLY),
        tool("dns.patch", "Patch DNS configuration transactionally.", HIGH),
        tool("dns.query", "Run a DNS query through the active core.", LOW),
        tool("tun.get", "Read effective TUN and Android routing configuration.", READ_ONLY),
        tool("tun.patch", "Patch TUN and Android routing configuration.", CRITICAL),
        tool("general.get", "Read general mihomo configuration.", READ_ONLY),
        tool("general.patch", "Patch general mihomo configuration.", HIGH),

        tool("providers.list", "List proxy and rule providers.", READ_ONLY),
        tool("providers.create", "Create a proxy or rule provider.", MEDIUM),
        tool("providers.update", "Update provider metadata or source.", HIGH),
        tool("providers.delete", "Delete a provider and repair or report references.", HIGH),
        tool("providers.refresh", "Refresh a provider immediately.", LOW),

        tool("apps.list_installed", "List installed apps using user-approved visibility.", READ_ONLY),
        tool("apps.routing.get", "Read VPN access and package routing rules.", READ_ONLY),
        tool("apps.routing.set", "Change VPN access or package routing rules.", HIGH),
        tool("apps.suggest_rules", "Suggest package rules without applying them.", READ_ONLY),

        tool("connections.list", "Read active connections with matched rules and chains.", READ_ONLY),
        tool("connections.close", "Close one active connection.", LOW),
        tool("connections.close_all", "Close every active connection.", HIGH),
        tool("logs.read", "Read redacted recent logs.", READ_ONLY),
        tool("network.diagnose", "Run local DNS, direct, proxy, and route diagnostics.", LOW),

        tool("config.validate", "Validate a staged configuration without applying it.", READ_ONLY),
        tool("config.diff", "Compare staged and active configuration semantically.", READ_ONLY),
        tool("config.apply", "Atomically apply a validated staged revision.", HIGH),
        tool("config.rollback", "Roll back to a known-good revision.", HIGH),
    )

    private val byName = tools.associateBy(AgentToolDescriptor::name)

    fun find(name: String): AgentToolDescriptor? = byName[name]

    private fun tool(
        name: String,
        description: String,
        risk: com.github.kr328.clash.agent.authorization.AgentOperationRisk,
    ) = AgentToolDescriptor(name, description, risk)
}
