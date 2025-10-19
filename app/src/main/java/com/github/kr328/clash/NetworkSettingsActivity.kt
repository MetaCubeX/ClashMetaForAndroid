package com.github.kr328.clash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

class NetworkSettingsActivity : ComponentActivity(), Broadcasts.Observer {
    private lateinit var design: NetworkSettingsDesign
    private val serviceStore by lazy { ServiceStore(this) }
    private val uiStore by lazy { UiStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialRunning = Remote.broadcasts.clashRunning
        design = NetworkSettingsDesign(this, uiStore, serviceStore, initialRunning)

        design.requests
            .receiveAsFlow()
            .onEach { request ->
                when (request) {
                    NetworkSettingsDesign.Request.StartAccessControlList ->
                        startActivity(AccessControlActivity::class.intent)
                }
            }
            .launchIn(lifecycleScope)

        setContent {
            YumeTheme {
                design.Content()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        design.running = Remote.broadcasts.clashRunning
        Remote.broadcasts.addObserver(this)
    }

    override fun onStop() {
        super.onStop()
        Remote.broadcasts.removeObserver(this)
    }


    override fun onServiceRecreated() {
        design.running = false
    }

    override fun onStarted() {
        design.running = true
    }

    override fun onStopped(cause: String?) {
        design.running = false
    }


    override fun onProfileChanged() {}
    override fun onProfileUpdateCompleted(uuid: java.util.UUID?) {}
    override fun onProfileUpdateFailed(uuid: java.util.UUID?, reason: String?) {}
    override fun onProfileLoaded() {}
}