package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

suspend fun Context.prepareQuickTileStart(includeInstalledPackages: Boolean = true) = withContext(Dispatchers.IO) {
    val store = ServiceStore(this@prepareQuickTileStart)
    store.seedDefaultGeoMirrors = true

    val active = withProfile { queryActive() }
    if (active == null) {
        val imported = withProfile { queryAll() }
            .filter { it.imported && !it.pending }
        if (imported.size == 1) {
            withProfile { setActive(imported.first()) }
        }
    }

    val existing = store.accessControlPackages
    val seed = existing + RussianBypassDefaults.PACKAGES +
        if (includeInstalledPackages) RussianBypassDefaults.installed(packageManager) else emptySet()
    store.accessControlMode = AccessControlMode.DenySelected
    store.accessControlPackages = seed
    store.russianBypassSeeded = true
}

suspend fun autoSelectFirstRuntimeProxy() {
    val knownGroups = waitForProxyGroups()
    if (knownGroups.isEmpty()) return

    val preferredGroups = knownGroups.filterNot { it.equals("GLOBAL", ignoreCase = true) } +
        knownGroups.filter { it.equals("GLOBAL", ignoreCase = true) }

    for (group in preferredGroups) {
        val selected = selectFirstLeaf(group, knownGroups, linkedSetOf())
        if (selected != null) {
            val (selectedGroup, selectedProxy) = selected
            withClash { patchSelector(selectedGroup, selectedProxy) }
            if (!selectedGroup.equals(group, ignoreCase = true)) {
                runCatching { withClash { patchSelector(group, selectedGroup) } }
            }
            return
        }
    }
}

private suspend fun waitForProxyGroups(): Set<String> {
    repeat(50) {
        val groups = runCatching {
            withClash { queryProxyGroupNames(false) }
        }.getOrDefault(emptyList())
        if (groups.isNotEmpty()) return groups.toSet()
        delay(200L)
    }
    return emptySet()
}

private suspend fun selectFirstLeaf(
    groupName: String,
    knownGroups: Set<String>,
    visited: MutableSet<String>,
): Pair<String, String>? {
    if (!visited.add(groupName)) return null

    val group = runCatching {
        withClash { queryProxyGroup(groupName, ProxySort.Default) }
    }.getOrNull() ?: return null

    for (proxy in group.proxies) {
        if (proxy.name.matches(Regex("^sub\\d+$", RegexOption.IGNORE_CASE))) continue
        if (proxy.type.group || proxy.name in knownGroups) {
            val nested = selectFirstLeaf(proxy.name, knownGroups, visited)
            if (nested != null) return nested
        } else {
            return groupName to proxy.name
        }
    }

    return null
}
