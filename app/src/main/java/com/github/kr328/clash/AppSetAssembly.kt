package com.github.kr328.clash

import android.content.Context
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.AppSetIds
import com.github.kr328.clash.design.model.AppSetUi
import com.github.kr328.clash.design.model.effectiveBuiltinPackages
import com.github.kr328.clash.service.model.AppSetData

internal fun assembleAppSets(
    context: Context,
    customSets: List<AppSetData>,
    systemPackages: Set<String>,
    domesticPackages: Set<String>,
    browserPackages: Set<String>,
): List<AppSetUi> {
    fun builtin(id: String, nameRes: Int, packages: Set<String>): AppSetUi {
        val saved = customSets.find { it.id == id }
        val frozen = saved?.membershipFrozen == true
        return AppSetUi(
            id = id,
            name = saved?.name?.takeIf { it.isNotBlank() } ?: context.getString(nameRes),
            packages = effectiveBuiltinPackages(packages, saved?.packages?.toSet(), frozen),
            isBuiltin = true,
            description = saved?.description.orEmpty(),
            membershipFrozen = frozen,
            catalogPackages = packages,
        )
    }
    return buildList {
        add(builtin(AppSetIds.SYSTEM, R.string.system_apps, systemPackages))
        add(builtin(AppSetIds.DOMESTIC, R.string.app_set_domestic, domesticPackages))
        add(builtin(AppSetIds.BROWSERS, R.string.app_set_browsers, browserPackages))
        customSets.forEach { set ->
            if (AppSetIds.isBuiltin(set.id)) return@forEach
            add(
                AppSetUi(
                    id = set.id,
                    name = set.name,
                    packages = set.packages.toSet(),
                    isBuiltin = false,
                    description = set.description,
                ),
            )
        }
    }
}

internal fun builtinAppSetFallbackName(context: Context, id: String): String {
    val res = when (id) {
        AppSetIds.SYSTEM -> R.string.system_apps
        AppSetIds.DOMESTIC -> R.string.app_set_domestic
        AppSetIds.BROWSERS -> R.string.app_set_browsers
        else -> R.string.app_sets
    }
    return context.getString(res)
}
