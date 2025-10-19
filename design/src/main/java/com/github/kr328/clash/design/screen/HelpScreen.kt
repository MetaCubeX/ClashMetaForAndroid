package com.github.kr328.clash.design.screen

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun HelpScreen(onOpen: (Uri) -> Unit, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.help_page_title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Useful.Back, contentDescription = MLang.action_back)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .padding(paddingValues)
        ) {
            HELP_LINKS.forEach { (title, uri) ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = { onOpen(uri) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(text = title, style = MiuixTheme.textStyles.body1)
                            Spacer(Modifier.height(4.dp))
                            Text(uri.toString(), style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }
        }
    }
}

private val HELP_LINKS = listOf(
    "Clash Wiki" to Uri.parse("https://github.com/Dreamacro/clash/wiki"),
    "Clash Meta Wiki" to Uri.parse("https://github.com/MetaCubeX/Clash.Meta"),
    "Clash Meta Core" to Uri.parse("https://github.com/MetaCubeX/mihomo")
)

