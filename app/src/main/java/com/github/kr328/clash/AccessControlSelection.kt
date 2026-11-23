package com.github.kr328.clash

/**
 * Bulk selection edits the visible rows only.
 * Packages already selected but absent from [visible] stay selected.
 */
internal fun selectAllVisible(selected: Set<String>, visible: Collection<String>): Set<String> {
    val next = LinkedHashSet(selected)
    next.addAll(visible)
    return next
}

/** Flips membership of [visible] and keeps every selected package outside that list. */
internal fun invertVisible(selected: Set<String>, visible: Collection<String>): Set<String> {
    val visibleSet = visible as? Set<String> ?: visible.toSet()
    val next = LinkedHashSet<String>()
    for (name in selected) {
        if (name !in visibleSet) next.add(name)
    }
    for (name in visible) {
        if (name !in selected) next.add(name)
    }
    return next
}

internal fun clipboardPackageNames(text: CharSequence): Set<String> {
    val names = LinkedHashSet<String>()
    for (line in text.split('\n')) {
        val name = line.replace("\r", "").trim()
        if (name.isNotEmpty()) names.add(name)
    }
    return names
}

/**
 * Replaces the visible selection with installed clipboard names.
 * Visibility is not a match condition. When no clipboard line names an installed
 * package, returns [selected] itself so the caller can leave the set untouched.
 */
internal fun importAccessSelection(
    selected: Set<String>,
    visible: Set<String>,
    installed: Set<String>,
    clipboard: Set<String>,
): Set<String> {
    val matched = clipboard.intersect(installed)
    if (matched.isEmpty()) return selected
    val next = LinkedHashSet<String>()
    for (name in selected) {
        if (name !in visible) next.add(name)
    }
    next.addAll(matched)
    return next
}
