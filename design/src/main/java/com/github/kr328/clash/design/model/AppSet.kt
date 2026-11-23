package com.github.kr328.clash.design.model

enum class AppSetCheckState {
    Checked,
    Unchecked,
    Indeterminate,
}

data class AppSetMembership(
    val state: AppSetCheckState,
    val selectedCount: Int,
    val total: Int,
)

object AppSetIds {
    const val SYSTEM = "system"
    const val DOMESTIC = "domestic"
    const val BROWSERS = "browsers"

    fun isBuiltin(id: String): Boolean =
        id == SYSTEM || id == DOMESTIC || id == BROWSERS
}

data class BuiltinAppSetMembers(
    val system: Set<String>,
    val domestic: Set<String>,
    val browsers: Set<String>,
)

data class AppSetUi(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val isBuiltin: Boolean = false,
    val description: String = "",
    val membershipFrozen: Boolean = false,
    val catalogPackages: Set<String> = emptySet(),
)

data class BuiltinSetWrite(
    val name: String,
    val description: String,
    val packages: List<String>,
    val membershipFrozen: Boolean,
)

/**
 * A blank name uses [defaultName].
 * Name and description alone do not freeze membership.
 * An unfrozen set that still has the default name and no description is not stored.
 * Returns null when the saved override should be removed.
 */
fun planBuiltinSetWrite(
    defaultName: String,
    name: String,
    description: String,
    packages: List<String>,
    membershipFrozen: Boolean,
): BuiltinSetWrite? {
    val resolvedName = name.trim().ifEmpty { defaultName }
    val resolvedDescription = description.trim()
    if (!membershipFrozen && resolvedName == defaultName && resolvedDescription.isEmpty()) {
        return null
    }
    return BuiltinSetWrite(
        name = resolvedName,
        description = resolvedDescription,
        packages = if (membershipFrozen) packages else emptyList(),
        membershipFrozen = membershipFrozen,
    )
}

/** A frozen built-in set keeps its saved packages. Otherwise it follows [catalog]. */
fun effectiveBuiltinPackages(
    catalog: Set<String>,
    savedPackages: Set<String>?,
    frozen: Boolean,
): Set<String> = if (frozen && savedPackages != null) savedPackages else catalog

/**
 * A set is checked only when every current member is selected.
 * An empty set stays unchecked and is not actionable.
 */
fun appSetMembership(members: Set<String>, selected: Set<String>): AppSetMembership {
    var selectedCount = 0
    for (member in members) {
        if (member in selected) selectedCount++
    }
    val state = when {
        members.isEmpty() || selectedCount == 0 -> AppSetCheckState.Unchecked
        selectedCount == members.size -> AppSetCheckState.Checked
        else -> AppSetCheckState.Indeterminate
    }
    return AppSetMembership(state, selectedCount, members.size)
}

/**
 * Checking a partial or empty selection selects every current member.
 * Checking a fully selected set clears only those members.
 */
fun applyAppSetToggle(members: Set<String>, selected: MutableSet<String>) {
    if (members.isEmpty()) return
    if (appSetMembership(members, selected).state == AppSetCheckState.Checked) {
        selected.removeAll(members)
    } else {
        selected.addAll(members)
    }
}

/**
 * Each pair is an installed package and whether it is a system app.
 * A browser catalog match is only a browser member.
 */
fun classifyBuiltinAppSets(
    packages: Iterable<Pair<String, Boolean>>,
    domesticCatalog: Set<String>,
    browserCatalog: Set<String>,
): BuiltinAppSetMembers {
    val system = mutableSetOf<String>()
    val domestic = mutableSetOf<String>()
    val browsers = mutableSetOf<String>()
    for ((name, isSystem) in packages) {
        if (name in browserCatalog) {
            browsers.add(name)
            continue
        }
        if (isSystem) system.add(name)
        if (name in domesticCatalog) domestic.add(name)
    }
    return BuiltinAppSetMembers(system, domestic, browsers)
}
