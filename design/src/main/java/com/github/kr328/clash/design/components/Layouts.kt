package com.github.kr328.clash.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.kr328.clash.design.theme.AppDimensions
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back

@Composable
fun StandardBackButton(onClick: () -> Unit) {
    IconButton(
        modifier = Modifier.padding(start = AppDimensions.spacing_xxl),
        onClick = onClick
    ) {
        Icon(
            imageVector = MiuixIcons.Useful.Back,
            contentDescription = MLang.action_back
        )
    }
}
