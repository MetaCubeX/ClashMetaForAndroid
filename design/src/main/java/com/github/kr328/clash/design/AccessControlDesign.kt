package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.*
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore

class AccessControlDesign(
    context: Context,
    val uiStore: UiStore,
    initialSelected: Set<String>,
) : Design<AccessControlDesign.Request>(context) {
    sealed class Request {
        object ReloadApps : Request()
        object ImportFromClipboard : Request()
        object ExportToClipboard : Request()
        object RequestPermission : Request()
    }

    internal var apps by mutableStateOf<List<AppInfo>>(emptyList())
    val selected = mutableStateMapOf<String, Boolean>().apply {
        initialSelected.forEach { put(it, true) }
    }
    internal var query by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var hasPermission by mutableStateOf(true)

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.AccessControlScreen(design = this)
    }

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        isLoading = false
    }

    fun selectAll() {
        apps.forEach { selected[it.packageName] = true }
    }

    fun selectNone() {
        selected.clear()
    }

    fun invertSelection() {
        val currentApps = apps.map { it.packageName }.toSet()
        val currentSelected = selected.keys.intersect(currentApps)
        val inverted = currentApps - currentSelected
        selected.clear()
        inverted.forEach { selected[it] = true }
    }

    fun importSelection(packages: Set<String>) {
        val currentApps = apps.map { it.packageName }.toSet()
        selected.clear()
        packages.intersect(currentApps).forEach { selected[it] = true }
    }
}

