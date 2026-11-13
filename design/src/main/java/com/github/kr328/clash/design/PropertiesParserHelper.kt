package com.github.kr328.clash.design

import android.view.ViewGroup
import android.widget.LinearLayout
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.util.ValidatorHttpUrl
import com.github.kr328.clash.design.view.ActionTextField
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.launch

class PropertiesParserHelper(
    private val design: PropertiesDesign,
    container: ViewGroup,
) {
    private val context = design.context
    private val field = ActionTextField(context)

    private var originalParserUrl: String? = null
    private var currentProfile: Profile? = null

    var parserUrl: String? = null
        private set

    init {
        field.icon = context.getDrawableCompat(R.drawable.ic_baseline_extension)
        field.title = context.getText(R.string.parser_url)
        field.placeholder = context.getText(R.string.parser_url_hint)
        field.setOnClickListener { inputParserUrl() }

        val marginVertical =
            context.resources.getDimensionPixelSize(R.dimen.properties_element_margin_vertical)
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginVertical
            bottomMargin = marginVertical
        }

        container.addView(field, findInsertIndex(container), layoutParams)
    }

    fun setInitial(url: String?) {
        originalParserUrl = normalize(url)
        parserUrl = originalParserUrl
        refreshField()
    }

    fun onProfileChanged(profile: Profile) {
        currentProfile = profile
        refreshField()
    }

    fun isChanged(): Boolean {
        return parserUrl != originalParserUrl
    }

    private fun refreshField() {
        val enabled = currentProfile?.type != Profile.Type.File
        field.isEnabled = enabled
        if (enabled) {
            field.text = parserUrl
        }
    }

    private fun inputParserUrl() {
        if (currentProfile?.type == Profile.Type.File) {
            return
        }

        design.launch {
            val result = context.requestModelTextInput(
                initial = parserUrl ?: "",
                title = context.getText(R.string.parser_url),
                reset = context.getText(R.string.reset),
                hint = context.getText(R.string.parser_url_hint),
                error = context.getText(R.string.parser_url_error),
                validator = { it.isBlank() || ValidatorHttpUrl(it.trim()) }
            )

            val normalized = normalize(result)
            if (normalized != parserUrl) {
                parserUrl = normalized
                refreshField()
            }
        }
    }

    private fun findInsertIndex(container: ViewGroup): Int {
        val urlTitle = context.getText(R.string.url).toString()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is ActionTextField && child.title?.toString() == urlTitle) {
                return i + 1
            }
        }
        return container.childCount
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}

