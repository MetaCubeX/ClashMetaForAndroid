package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipboardManager
import android.widget.TextView
import com.github.kr328.clash.common.util.ShareImportSupport
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import java.util.concurrent.TimeUnit

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    override suspend fun main() {
        val design = ProfilesDesign(this)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            showCreateImportSheet()
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.OpenSubscriptionSources ->
                            startActivity(ProxyProvidersEditorActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    design.requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    private fun showCreateImportSheet() {
        val dialog = AppBottomSheetDialog(this, fitContentHeight = false)
        val view = layoutInflater.inflate(DesignR.layout.bottom_sheet_home_import, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(DesignR.id.opt_clipboard).setOnClickListener {
            dialog.dismiss()
            launch { importFromClipboard() }
        }
        view.findViewById<TextView>(DesignR.id.opt_url).setOnClickListener {
            dialog.dismiss()
            startActivity(NewProfileActivity::class.intent)
        }
        view.findViewById<TextView>(DesignR.id.opt_qr).setOnClickListener {
            dialog.dismiss()
            startActivity(NewProfileActivity::class.intent)
        }
        dialog.show()
    }

    private suspend fun importFromClipboard() {
        val d = design ?: return
        val text = withContext(Dispatchers.Main) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                ?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        }
        if (text.isEmpty()) {
            d.showToast(DesignR.string.clipboard_empty, ToastDuration.Short)
            return
        }
        if (!ShareImportSupport.isAllowedUrlProfileSource(text)) {
            d.showToast(DesignR.string.clipboard_no_url, ToastDuration.Long)
            return
        }

        d.showToast(DesignR.string.import_resolving, ToastDuration.Short)
        val name = withTimeoutOrNull(4500L) {
            SubscriptionNameGuesser.guess(this@ProfilesActivity, text)
        } ?: SubscriptionNameGuesser.guessFast(text)

        val uuid = withProfile { create(Profile.Type.Url, name, text) }
        try {
            withProfile { commit(uuid) }
        } catch (e: Exception) {
            d.showExceptionToast(e)
            return
        }

        val profile = withProfile { queryByUUID(uuid) }
        if (profile?.imported == true) {
            withProfile { setActive(profile) }
            ensureGlobalSelectionSafeAfterImport()
            d.showToast(getString(DesignR.string.import_done_named, name), ToastDuration.Long)
            d.fetch()
        } else {
            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
        }
    }

    private fun isBlockedGlobalChoice(name: String): Boolean =
        name.equals("DIRECT", ignoreCase = true) || name.equals("REJECT", ignoreCase = true)

    private fun isBlockedGlobalChoice(proxy: Proxy): Boolean =
        proxy.type == Proxy.Type.Direct ||
            proxy.type == Proxy.Type.Reject ||
            isBlockedGlobalChoice(proxy.name)

    private suspend fun ensureGlobalSelectionSafeAfterImport() {
        repeat(10) { attempt ->
            val fixed = runCatching {
                val mode = withClash { queryTunnelState().mode }
                if (mode.name != "Global") return@runCatching true
                val global = withClash {
                    queryProxyGroup("GLOBAL", com.github.kr328.clash.core.model.ProxySort.Default)
                }
                if (!isBlockedGlobalChoice(global.now)) return@runCatching true
                val firstUsable = global.proxies.firstOrNull { !isBlockedGlobalChoice(it) }?.name
                    ?: return@runCatching true
                withClash { patchSelector("GLOBAL", firstUsable) }
                true
            }.getOrDefault(false)
            if (fixed) return
            if (attempt < 9) delay(180L)
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(DesignR.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
}