package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.ProviderRowUi
import com.github.kr328.clash.design.ios.ProvidersScreen
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProvidersDesign(
    context: Context,
    providers: List<Provider>,
) : Design<ProvidersDesign.Request>(context) {
    sealed class Request {
        data class Update(val index: Int, val provider: Provider) : Request()
    }

    private class ProviderRow(
        val index: Int,
        val provider: Provider,
        updatedAt: Long,
    ) {
        var updatedAt by mutableStateOf(updatedAt)
        var updating by mutableStateOf(false)
    }

    private val rows = providers.mapIndexed { index, provider ->
        ProviderRow(index, provider, provider.updatedAt)
    }
    private var now by mutableStateOf(System.currentTimeMillis())
    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.providers)
        setContent {
            ClashMetaXTheme {
                ProvidersScreen(
                    insets = insets,
                    title = title,
                    rows = rows.map { row ->
                        ProviderRowUi(
                            index = row.index,
                            provider = row.provider,
                            updatedAt = row.updatedAt,
                            updating = row.updating,
                        )
                    },
                    now = now,
                    onBack = {
                        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onUpdate = ::requestUpdate,
                    onUpdateAll = ::requestUpdateAll,
                )
            }
        }
    }

    fun updateElapsed() {
        now = System.currentTimeMillis()
    }

    suspend fun notifyUpdated(index: Int) {
        withContext(Dispatchers.Main) {
            rows.getOrNull(index)?.updating = false
        }
    }

    suspend fun notifyChanged(index: Int) {
        withContext(Dispatchers.Main) {
            rows.getOrNull(index)?.let { row ->
                row.updating = false
                row.updatedAt = System.currentTimeMillis()
            }
        }
    }

    fun requestUpdateAll() {
        rows.forEach { row ->
            if (row.updating) return@forEach
            if (row.provider.vehicleType == Provider.VehicleType.Inline) return@forEach
            requests.trySend(Request.Update(row.index, row.provider))
            row.updating = true
        }
    }

    private fun requestUpdate(index: Int) {
        val row = rows.getOrNull(index) ?: return
        if (row.updating) return
        if (row.provider.vehicleType == Provider.VehicleType.Inline) return
        row.updating = true
        requests.trySend(Request.Update(row.index, row.provider))
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
