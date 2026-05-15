package com.github.kr328.clash.util

import com.github.kr328.clash.BaseActivity
import android.widget.Toast
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showYamlPreviewDialog
import com.github.kr328.clash.service.model.YamlPreview
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val yamlPreviewJson = Json { ignoreUnknownKeys = true }

fun BaseActivity<*>.showYamlPreview(
    previewJson: String?,
    onApplied: suspend () -> Unit,
) {
    val preview = previewJson
        ?.let { runCatching { yamlPreviewJson.decodeFromString(YamlPreview.serializer(), it) }.getOrNull() }
        ?: return run {
            Toast.makeText(this, R.string.rule_snippet_apply_failed, Toast.LENGTH_LONG).show()
        }
    showYamlPreviewDialog(preview) {
        launch {
            val ok = withProfile { applyYamlPreview(preview.id) }
            if (ok) {
                onApplied()
            } else {
                Toast.makeText(this@showYamlPreview, R.string.yaml_preview_stale, Toast.LENGTH_LONG).show()
            }
        }
    }
}
