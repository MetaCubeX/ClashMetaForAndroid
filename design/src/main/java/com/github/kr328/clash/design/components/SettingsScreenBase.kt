package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.theme.AppDimensions
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun BaseSettingsScreen(
    title: String,
    sections: List<SettingSection>,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    dialogs: @Composable () -> Unit = {}
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StandardBackButton(onBack)
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .height(getWindowSize().height.dp)
                .padding(top = AppDimensions.spacing_lg)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues
        ) {
            item {
                SettingsRenderer(sections)
                Spacer(modifier = Modifier.height(AppDimensions.spacing_xxl))
            }
        }
    }

    dialogs()
}


