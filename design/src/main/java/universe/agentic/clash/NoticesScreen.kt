package universe.agentic.clash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

@Composable
fun NoticesScreen(
    insets: Insets,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            IosInsetGroup(
                title = stringResource(R.string.about_notices_components),
                rows = AboutContent.components.map { component ->
                    IosDetail(
                        title = component.name,
                        subtitle = "${component.author} · ${component.license}",
                        onClick = { onOpen(component.url) },
                        disclosure = false,
                        external = true,
                        spokenDescription = listOf(
                            component.name,
                            component.author,
                            component.license,
                            stringResource(R.string.opens_in_browser),
                        ).joinToString(stringResource(R.string.author_spoken_separator)),
                    )
                },
            )
            Text(
                text = stringResource(R.string.about_notices_hint),
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
    }
}
