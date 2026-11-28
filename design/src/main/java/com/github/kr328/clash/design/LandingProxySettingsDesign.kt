package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.LandingProxyType
import com.github.kr328.clash.service.store.ServiceStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LandingProxySettingsDesign(
    context: Context,
    private val srvStore: ServiceStore,
) : Design<LandingProxySettingsDesign.Request>(context) {
    enum class Request {
        PickFrontGroup
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private lateinit var frontGroup: ClickablePreference

    /**
     * Let the user pick the front group from [groups] (may be empty when the
     * profile could not be loaded), "auto", or a custom name.
     */
    suspend fun requestFrontGroup(groups: List<String>) {
        val current = withContext(Dispatchers.IO) { srvStore.landingFrontGroup }

        val items = buildList {
            add(context.getString(R.string.landing_front_group_auto))
            addAll(groups)
            add(context.getString(R.string.landing_front_group_custom))
        }

        val checked = when {
            current.isEmpty() -> 0
            current in groups -> groups.indexOf(current) + 1
            else -> items.size - 1
        }

        val index = suspendCancellableCoroutine<Int?> { ctx ->
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.landing_front_group)
                .setSingleChoiceItems(items.toTypedArray(), checked) { dialog, which ->
                    ctx.resume(which)

                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted)
                    ctx.resume(null)
            }

            ctx.invokeOnCancellation {
                dialog.dismiss()
            }
        } ?: return

        val value = when (index) {
            0 -> ""
            items.size - 1 -> context.requestModelTextInput(
                initial = current,
                title = context.getText(R.string.landing_front_group),
                reset = null,
                hint = context.getText(R.string.landing_front_group),
            )?.trim() ?: return
            else -> groups[index - 1]
        }

        withContext(Dispatchers.IO) {
            srvStore.landingFrontGroup = value
        }

        updateFrontGroupSummary(value)
    }

    private fun updateFrontGroupSummary(value: String) {
        frontGroup.summary = value.ifEmpty {
            context.getString(R.string.landing_front_group_auto)
        }
    }

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val text = object : NullableTextAdapter<String> {
            override fun from(value: String): String = value
            override fun to(text: String?): String = text?.trim() ?: ""
        }

        val port = object : NullableTextAdapter<Int> {
            override fun from(value: Int): String = if (value > 0) value.toString() else ""
            override fun to(text: String?): Int =
                text?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
        }

        val password = object : NullableTextAdapter<String> {
            override fun from(value: String): String = if (value.isEmpty()) "" else PASSWORD_MASK
            override fun to(text: String?): String = when (text) {
                null -> ""
                PASSWORD_MASK -> srvStore.landingPassword // unchanged
                else -> text
            }
        }

        val screen = preferenceScreen(context) {
            val httpsOnly = mutableListOf<Preference>()
            val socks5Only = mutableListOf<Preference>()

            fun applyType(type: LandingProxyType) {
                httpsOnly.forEach {
                    it.view.visibility =
                        if (type == LandingProxyType.Https) View.VISIBLE else View.GONE
                }
                socks5Only.forEach {
                    it.view.visibility =
                        if (type == LandingProxyType.Socks5) View.VISIBLE else View.GONE
                }
            }

            switch(
                value = srvStore::landingEnabled,
                icon = R.drawable.ic_baseline_vpn_lock,
                title = R.string.landing_proxy_enable,
            ) {
                listener = OnChangedListener {
                    launch(Dispatchers.Main) {
                        val incomplete = withContext(Dispatchers.IO) {
                            srvStore.landingEnabled &&
                                    (srvStore.landingServer.isBlank() || srvStore.landingPort <= 0)
                        }

                        if (incomplete) {
                            showToast(R.string.landing_proxy_incomplete, ToastDuration.Long)
                        }
                    }
                }
            }

            frontGroup = clickable(
                title = R.string.landing_front_group,
            ) {
                clicked {
                    requests.trySend(Request.PickFrontGroup)
                }
            }

            category(R.string.landing_server_category)

            selectableList(
                value = srvStore::landingType,
                values = LandingProxyType.values(),
                valuesText = arrayOf(
                    R.string.landing_type_http,
                    R.string.landing_type_https,
                    R.string.landing_type_socks5,
                ),
                title = R.string.landing_type,
            ) {
                listener = OnChangedListener {
                    launch(Dispatchers.Main) {
                        applyType(withContext(Dispatchers.IO) { srvStore.landingType })
                    }
                }
            }

            editableText(
                value = srvStore::landingServer,
                adapter = text,
                title = R.string.landing_server,
                empty = R.string.landing_not_set,
            )

            editableText(
                value = srvStore::landingPort,
                adapter = port,
                title = R.string.landing_port,
                empty = R.string.landing_not_set,
            )

            editableText(
                value = srvStore::landingUsername,
                adapter = text,
                title = R.string.landing_username,
                empty = R.string.landing_auth_none,
            )

            editableText(
                value = srvStore::landingPassword,
                adapter = password,
                title = R.string.landing_password,
                empty = R.string.landing_auth_none,
            )

            switch(
                value = srvStore::landingSkipCertVerify,
                title = R.string.landing_skip_cert_verify,
                summary = R.string.landing_skip_cert_verify_summary,
                configure = httpsOnly::add,
            )

            editableText(
                value = srvStore::landingSni,
                adapter = text,
                title = R.string.landing_sni,
                empty = R.string.landing_sni_default,
                configure = httpsOnly::add,
            )

            switch(
                value = srvStore::landingUdp,
                title = R.string.landing_udp,
                configure = socks5Only::add,
            )

            launch(Dispatchers.Main) {
                val (type, group) = withContext(Dispatchers.IO) {
                    srvStore.landingType to srvStore.landingFrontGroup
                }

                applyType(type)
                updateFrontGroupSummary(group)
            }
        }

        binding.content.addView(screen.root)
    }

    companion object {
        private const val PASSWORD_MASK = "••••••••"
    }
}
