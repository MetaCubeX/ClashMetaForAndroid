package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.github.kr328.clash.design.components.StandardBackButton
import com.github.kr328.clash.design.theme.AppDimensions
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ApkBrokenScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.apk_broken_page_title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StandardBackButton { (context as? android.app.Activity)?.finish() }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues
        ) {
            item {
                Spacer(modifier = Modifier.height(AppDimensions.spacing_lg))

                // 提示信息
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = MLang.apk_broken_tips,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(AppDimensions.spacing_lg)
                    )
                }

                Spacer(modifier = Modifier.height(AppDimensions.spacing_sm))
            }

            // 重新安装分组
            item {
                SmallTitle(text = MLang.section_reinstall)
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    SuperArrow(
                        title = MLang.github_releases,
                        summary = MLang.meta_github_url,
                        onClick = {
                            onNavigate(MLang.meta_github_url)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(AppDimensions.spacing_lg))
            }
        }
    }
}

