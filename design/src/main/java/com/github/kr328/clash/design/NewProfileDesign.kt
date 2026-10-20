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
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.NewProfileScreen
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewProfileDesign(context: Context) : Design<NewProfileDesign.Request>(context) {
    sealed class Request {
        data class Create(val provider: ProfileProvider) : Request()
        data class OpenDetail(val provider: ProfileProvider.External) : Request()
        data class LaunchScanner(val provider: ProfileProvider.QR) : Request()
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var querying by mutableStateOf(true)
    private var externals by mutableStateOf<List<ProfileProvider.External>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()?.ifBlank { null }
            ?: context.getString(R.string.create_profile)
        setContent {
            ClashMetaXTheme {
                NewProfileScreen(
                    insets = insets,
                    title = title,
                    querying = querying,
                    externals = externals,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onFile = { requests.trySend(Request.Create(ProfileProvider.File(context))) },
                    onUrl = { requests.trySend(Request.Create(ProfileProvider.Url(context))) },
                    onQr = { requests.trySend(Request.LaunchScanner(ProfileProvider.QR(context))) },
                    onExternal = { requests.trySend(Request.Create(it)) },
                    onDetail = { requests.trySend(Request.OpenDetail(it)) },
                )
            }
        }
    }

    suspend fun patchProviders(providers: List<ProfileProvider>) {
        val external = providers.filterIsInstance<ProfileProvider.External>()
        withContext(Dispatchers.Main) {
            externals = external
            querying = false
        }
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
