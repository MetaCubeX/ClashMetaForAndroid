package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignRuleProvidersEditorBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuleProvidersEditorDesign(context: Context) : Design<RuleProvidersEditorDesign.Request>(context) {
    enum class Request {
        Save,
    }

    private val binding = DesignRuleProvidersEditorBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.btnSave.setOnClickListener { requests.trySend(Request.Save) }
    }

    suspend fun setYaml(text: String) {
        withContext(Dispatchers.Main) {
            binding.yamlInput.setText(text)
        }
    }

    fun getYaml(): String = binding.yamlInput.text?.toString().orEmpty()
}
