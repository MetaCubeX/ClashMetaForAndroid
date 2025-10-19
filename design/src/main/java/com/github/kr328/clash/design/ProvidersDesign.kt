package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.*
import com.github.kr328.clash.core.model.Provider

class ProvidersDesign(
    context: Context,
) : Design<ProvidersDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        data class Update(val provider: Provider) : Request()
    }

    var providers by mutableStateOf<List<Provider>>(emptyList())
    internal var allUpdating by mutableStateOf(false)
    private val providerUpdating = mutableStateMapOf<String, Boolean>()

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.ProvidersScreen(this)
    }

    suspend fun patchProviders(newProviders: List<Provider>) {
        providers = newProviders
        if (allUpdating && newProviders.isEmpty()) allUpdating = false
    }

    fun requestUpdateAll() {
        if (allUpdating) return
        allUpdating = true
        providers.forEach { p ->
            if (p.vehicleType != Provider.VehicleType.Inline) providerUpdating[p.name] = true
        }
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false
        providerUpdating.clear()
    }

    fun markProviderUpdating(name: String, updating: Boolean) {
        if (updating) providerUpdating[name] = true else providerUpdating.remove(name)
    }

    fun isProviderUpdating(name: String): Boolean = providerUpdating[name] == true
}