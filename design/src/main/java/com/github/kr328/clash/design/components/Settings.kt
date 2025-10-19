package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.dialog.DialogState
import com.github.kr328.clash.design.dialog.UnifiedInputDialog
import com.github.kr328.clash.design.dialog.Validator
import com.github.kr328.clash.design.util.SelectionMapping
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown

@Composable
fun SettingInputDialog(
    title: String,
    state: DialogState,
    onDismiss: () -> Unit,
    currentValue: String,
    label: String,
    validator: (String) -> String?,
    onConfirm: (String) -> Unit
) {
    val wrappedValidator = Validator<String> { value -> validator(value) }
    UnifiedInputDialog(
        title = title,
        state = state,
        initialValue = currentValue,
        label = label,
        validator = wrappedValidator,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun SettingRowTriState(
    title: String,
    summary: String? = null,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    enabled: Boolean = true,
) {
    val items = listOf(
        MLang.tristate_not_modify,
        MLang.tristate_enabled,
        MLang.tristate_disabled,
    )
    val values = listOf<Boolean?>(null, true, false)
    val selectedIndex = values.indexOf(value).let { if (it == -1) 0 else it }

    SuperDropdown(
        title = title,
        summary = summary,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { idx -> onValueChange(values[idx]) },
        enabled = enabled,
    )
}

@Composable
fun SettingRowSwitch(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = { if (enabled) onToggle(!checked) },
        enabled = enabled,
        rightActions = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
        }
    )
}

@Composable
fun SettingRowArrow(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    rightText: String? = null,
) {
    SuperArrow(
        title = title,
        summary = summary,
        onClick = onClick,
        enabled = enabled,
        rightText = rightText,
    )
}

@Composable
fun SettingRowDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    enabled: Boolean = true,
    summary: String? = null,
) {
    SuperDropdown(
        title = title,
        summary = summary,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        enabled = enabled,
    )
}

sealed class SettingItemSpec {
    data class TriStateSpec(
        val title: String,
        val summary: String? = null,
        val value: Boolean?,
        val onChange: (Boolean?) -> Unit,
        val enabled: Boolean = true,
    ) : SettingItemSpec()

    data class SwitchSpec(
        val title: String,
        val summary: String? = null,
        val checked: Boolean,
        val onToggle: (Boolean) -> Unit,
        val enabled: Boolean = true,
    ) : SettingItemSpec()

    data class ArrowSpec(
        val title: String,
        val summary: String? = null,
        val onClick: () -> Unit,
        val enabled: Boolean = true,
        val rightText: String? = null,
    ) : SettingItemSpec()

    data class DropdownSpec(
        val title: String,
        val items: List<String>,
        val selectedIndex: Int,
        val onSelectedIndexChange: (Int) -> Unit,
        val enabled: Boolean = true,
        val summary: String? = null,
    ) : SettingItemSpec()
}

data class SettingSection(
    val title: String,
    val items: List<SettingItemSpec>
)

class SectionBuilder {
    internal val items = mutableListOf<SettingItemSpec>()

    fun triState(
        title: String,
        value: Boolean?,
        onChange: (Boolean?) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
    ) {
        items += SettingItemSpec.TriStateSpec(title, summary, value, onChange, enabled)
    }

    fun switch(
        title: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
    ) {
        items += SettingItemSpec.SwitchSpec(title, summary, checked, onToggle, enabled)
    }

    fun arrow(
        title: String,
        onClick: () -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
        rightText: String? = null,
    ) {
        items += SettingItemSpec.ArrowSpec(title, summary, onClick, enabled, rightText)
    }

    fun dropdown(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
    ) {
        this.items += SettingItemSpec.DropdownSpec(title, items, selectedIndex, onSelectedIndexChange, enabled, summary)
    }

    fun <T> dropdown(
        title: String,
        mapping: SelectionMapping<T>,
        value: T?,
        onChange: (T?) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
    ) {
        val labels = mapping.allLabels()
        val index = mapping.indexOf(value)
        dropdown(title, labels, index, { idx -> onChange(mapping.valueOf(idx)) }, summary, enabled)
    }
}

class SettingsListBuilder {
    internal val sections = mutableListOf<SettingSection>()

    fun section(title: String, block: SectionBuilder.() -> Unit) {
        val b = SectionBuilder()
        b.block()
        sections += SettingSection(title, b.items.toList())
    }
}

fun settingsList(block: SettingsListBuilder.() -> Unit): List<SettingSection> {
    val b = SettingsListBuilder()
    b.block()
    return b.sections.toList()
}

@Composable
fun SettingsRenderer(sections: List<SettingSection>) {
    sections.forEach { section ->
        SmallTitle(section.title)
        Card(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                section.items.forEach { item ->
                    when (item) {
                        is SettingItemSpec.TriStateSpec -> SettingRowTriState(
                            title = item.title,
                            summary = item.summary,
                            value = item.value,
                            onValueChange = item.onChange,
                            enabled = item.enabled,
                        )

                        is SettingItemSpec.SwitchSpec -> SettingRowSwitch(
                            title = item.title,
                            summary = item.summary,
                            checked = item.checked,
                            onToggle = item.onToggle,
                            enabled = item.enabled,
                        )

                        is SettingItemSpec.ArrowSpec -> SettingRowArrow(
                            title = item.title,
                            summary = item.summary,
                            onClick = item.onClick,
                            enabled = item.enabled,
                            rightText = item.rightText,
                        )

                        is SettingItemSpec.DropdownSpec -> SettingRowDropdown(
                            title = item.title,
                            items = item.items,
                            selectedIndex = item.selectedIndex,
                            onSelectedIndexChange = item.onSelectedIndexChange,
                            enabled = item.enabled,
                            summary = item.summary,
                        )
                    }
                }
            }
        }
    }
}

