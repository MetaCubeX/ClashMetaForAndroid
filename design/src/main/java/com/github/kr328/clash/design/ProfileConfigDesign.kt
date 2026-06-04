package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignProfileConfigBinding
import com.github.kr328.clash.design.util.YamlHighlighter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only viewer of a profile's processed `config.yaml`. Displays YAML with lightweight
 * syntax highlighting in a monospace view; never writes the file. Copy/Share are routed to
 * the host activity via [Request].
 */
class ProfileConfigDesign(context: Context) : Design<ProfileConfigDesign.Request>(context) {
    enum class Request {
        Copy, Download
    }

    private val binding = DesignProfileConfigBinding
        .inflate(context.layoutInflater, context.root, false)

    // Resolved from yaml* theme attrs so the palette follows the app's in-app day/night
    // (the app applies AppThemeLight/Dark via theme.applyStyle, not the night resource qualifier).
    private val highlightColors by lazy {
        YamlHighlighter.Colors(
            key = themeColor(R.attr.yamlKey),
            string = themeColor(R.attr.yamlString),
            number = themeColor(R.attr.yamlNumber),
            keyword = themeColor(R.attr.yamlKeyword),
            comment = themeColor(R.attr.yamlComment),
            punctuation = themeColor(R.attr.yamlPunctuation),
            plain = themeColor(R.attr.yamlPlain),
        )
    }

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(binding.configText, attr)

    override val root: View
        get() = binding.root

    suspend fun setTitle(title: String) {
        withContext(Dispatchers.Main) {
            binding.screenTitle.text = title
        }
    }

    suspend fun setConfig(text: String?) {
        val content = text.orEmpty()
        // Resolve theme colors on the main thread before highlighting off-main.
        val colors = if (content.isBlank() || content.length > MAX_HIGHLIGHT_CHARS) null else highlightColors
        val rendered: CharSequence = if (colors == null) {
            content
        } else {
            withContext(Dispatchers.Default) { YamlHighlighter.highlight(content, colors) }
        }
        withContext(Dispatchers.Main) {
            binding.empty = content.isBlank()
            binding.configText.text = rendered
        }
    }

    init {
        binding.self = this
        binding.empty = false
    }

    companion object {
        // Above this size, skip highlighting (rare multi-MB configs) and show plain text.
        private const val MAX_HIGHLIGHT_CHARS = 300_000
    }
}
