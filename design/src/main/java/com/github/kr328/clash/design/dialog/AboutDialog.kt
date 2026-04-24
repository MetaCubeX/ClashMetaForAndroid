package com.github.kr328.clash.design.dialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.util.layoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the "About" bottom sheet. Extracted so multiple designs (Main, Settings) can reuse it.
 */
suspend fun showAboutDialog(
    context: Context,
    versionName: String,
    coreVersion: String,
    supportUrl: String? = null,
    onCheckUpdates: (((Boolean) -> Unit, (String?) -> Unit) -> Unit)? = null,
) {
    withContext(Dispatchers.Main) {
        val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
            this.versionName = versionName
            this.coreVersion = coreVersion
            runCatching {
                aboutAppIcon.setImageDrawable(
                    context.packageManager.getApplicationIcon(context.packageName)
                )
            }
        }
        val dialog = AppBottomSheetDialog(context, fitContentHeight = true)
        dialog.setContentView(binding.root)

        if (!supportUrl.isNullOrBlank()) {
            binding.aboutSupportButton.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        if (onCheckUpdates != null) {
            binding.aboutCheckUpdatesButton.apply {
                visibility = View.VISIBLE
                var statusText: String? = null

                fun render(loading: Boolean) {
                    isEnabled = !loading
                    alpha = if (loading) 0.65f else 1f
                    text = when {
                        loading -> context.getString(R.string.about_checking_updates)
                        !statusText.isNullOrBlank() -> statusText
                        else -> context.getString(R.string.about_check_updates)
                    }
                }

                fun setLoading(loading: Boolean) {
                    render(loading)
                }

                fun setStatus(text: String?) {
                    statusText = text
                    render(loading = false)
                }

                setOnClickListener {
                    if (!isEnabled) return@setOnClickListener
                    statusText = null
                    render(loading = true)
                    onCheckUpdates(::setLoading, ::setStatus)
                }
            }
        }

        dialog.show()
    }
}
