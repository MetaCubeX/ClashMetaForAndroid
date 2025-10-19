package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.github.kr328.clash.design.components.StandardBackButton
import com.github.kr328.clash.design.theme.AppDimensions
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppCrashedScreen(logs: String) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.app_crashed_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StandardBackButton { (context as? android.app.Activity)?.finish() }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            SelectionContainer {
                Text(
                    text = logs,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MiuixTheme.textStyles.body2.lineHeight * 1.5f
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(AppDimensions.spacing_lg)
                )
            }
        }
    }
}

