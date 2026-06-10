package com.github.kr328.clash.util

import android.widget.Toast
import com.github.kr328.clash.BaseActivity
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showYamlPreviewDialog
import com.github.kr328.clash.service.model.YamlPreview
import com.github.kr328.clash.service.util.YamlPreviewSupport
import kotlinx.coroutines.launch
import java.util.UUID

fun BaseActivity<*>.showRuleStatePreview(
    uuid: UUID,
    stateJson: String,
    currentYaml: String,
    proposedYaml: String?,
    onApplied: suspend () -> Unit,
) {
    val proposed = proposedYaml?.takeIf { it.isNotBlank() }
        ?: return run {
            Toast.makeText(this, R.string.rules_hub_preview_rejected, Toast.LENGTH_LONG).show()
        }
    val preview = YamlPreview(
        id = "",
        title = getString(R.string.rules_hub_preview_title),
        currentYaml = currentYaml,
        proposedYaml = proposed,
        diff = YamlPreviewSupport.unifiedDiff(currentYaml, proposed),
        valid = true,
        error = null,
    )
    showYamlPreviewDialog(preview) {
        launch {
            val ok = withProfile { applyRuleState(uuid, stateJson) }
            if (ok) {
                onApplied()
            } else {
                Toast.makeText(this@showRuleStatePreview, R.string.rules_hub_apply_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
