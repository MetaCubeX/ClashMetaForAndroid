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
}

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
