package com.github.kr328.clash.design.util

import android.content.Context
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.RulesHubRowBuilder
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleSource

object RuleEditFormHelper {
    fun displayTitle(context: Context, meta: RuleTypeMeta): String =
        if (meta.titleRes != 0) context.getString(meta.titleRes) else meta.mihomoType

    fun displayHint(context: Context, meta: RuleTypeMeta): String =
        if (meta.hintRes != 0) context.getString(meta.hintRes)
        else context.getString(R.string.rule_type_value_generic)

    fun valueErrorMessage(context: Context, error: RuleValueError): String = when (error) {
        RuleValueError.EMPTY -> context.getString(R.string.rule_value_err_empty)
        RuleValueError.BAD_PORT -> context.getString(R.string.rule_value_err_port)
        RuleValueError.BAD_CIDR -> context.getString(R.string.rule_value_err_cidr)
        RuleValueError.BAD_NETWORK -> context.getString(R.string.rule_value_err_network)
        RuleValueError.HAS_SEPARATOR -> context.getString(R.string.rule_value_err_separator)
    }

    fun metaForType(type: String): RuleTypeMeta =
        RuleTypeCatalog.byMihomoType(type) ?: RuleTypeMeta(
            mihomoType = type.trim().uppercase(),
            group = RuleTypeMeta.Group.ADVANCED,
            requiresValue = !type.trim().equals("MATCH", ignoreCase = true),
        )

    fun previewLine(type: String, value: String, policy: String): String {
        val item = RuleItem(
            id = "",
            type = type.trim().uppercase(),
            value = value.trim(),
            policy = policy.trim(),
            source = RuleSource.MANUAL,
        )
        return RulesHubRowBuilder.buildRuleLine(item)
    }
}
