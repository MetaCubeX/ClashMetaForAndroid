package com.github.kr328.clash.util

import android.view.View
import android.widget.EditText
import com.github.kr328.clash.BaseActivity
import com.github.kr328.clash.PropertiesActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Quick-edit name / URL / interval. [PropertiesActivity] is still the full editor (YAML, etc.).
 */
fun BaseActivity<*>.showProfileQuickEditSheet(
    design: Design<*>,
    profile: Profile,
    afterSaved: suspend () -> Unit,
) {
    val dialog = AppBottomSheetDialog(this, fitContentHeight = true)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_profile_quick_edit, null)
    dialog.setContentView(view)

    val nameInput = view.findViewById<EditText>(R.id.input_profile_name)
    val sourceInput = view.findViewById<EditText>(R.id.input_profile_source)
    val intervalInput = view.findViewById<EditText>(R.id.input_profile_interval)
    val saveButton = view.findViewById<MaterialButton>(R.id.btn_save_profile)
    val advancedButton = view.findViewById<View>(R.id.btn_open_advanced_profile)

    nameInput.setText(profile.name)
    sourceInput.setText(profile.source)
    intervalInput.setText(profile.interval.toString())
    sourceInput.isEnabled = profile.type != Profile.Type.File

    saveButton.setOnClickListener {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val source = sourceInput.text?.toString()?.trim().orEmpty()
        val interval = intervalInput.text?.toString()?.trim()?.toLongOrNull() ?: profile.interval

        if (name.isBlank()) {
            launch { design.showToast(DesignR.string.empty_name, ToastDuration.Short) }
            return@setOnClickListener
        }

        if (profile.type != Profile.Type.File && source.isBlank()) {
            launch { design.showToast(DesignR.string.invalid_url, ToastDuration.Short) }
            return@setOnClickListener
        }

        dialog.dismiss()
        launch {
            withProfile { patch(profile.uuid, name, source, interval) }
            afterSaved()
            design.showToast(DesignR.string.profile_quick_saved, ToastDuration.Short)
        }
    }

    advancedButton.setOnClickListener {
        dialog.dismiss()
        startActivity(PropertiesActivity::class.intent.setUUID(profile.uuid))
    }

    dialog.show()
}
