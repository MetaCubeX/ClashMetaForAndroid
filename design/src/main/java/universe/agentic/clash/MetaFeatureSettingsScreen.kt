package universe.agentic.clash

import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.ui.Insets

@Composable
fun MetaFeatureSettingsScreen(
    insets: Insets,
    title: String,
    groups: List<SettingGroup>,
    onBack: () -> Unit,
    onReset: () -> Unit,
    scrollAnchor: String? = null,
) {
    SettingsPage(
        insets = insets,
        title = title,
        groups = groups,
        onBack = onBack,
        onReset = onReset,
        scrollAnchor = scrollAnchor,
    )
}
