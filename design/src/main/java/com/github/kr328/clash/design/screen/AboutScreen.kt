package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.AboutDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.components.StandardListScreen
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(design: AboutDesign) {
    StandardListScreen(
        title = MLang.about_page_title,
        onBack = { design.requests.trySend(AboutDesign.Request.Back) },
        contentPaddingTop = 16.dp
    ) {
        item {
            // 顶部居中的内容
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 应用图标
                Icon(
                    painter = painterResource(id = R.drawable.yume),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 应用名称
                Text(
                    text = "YumeBox",
                    style = MiuixTheme.textStyles.title1
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 版本信息
                Text(
                    text = design.versionName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 项目信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = MLang.app_name,
                        style = MiuixTheme.textStyles.title3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = MLang.app_description,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmallTitle(MLang.section_project_links)

            // 项目链接
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                BasicComponent(
                    title = "YumeBox",
                    summary = "https://github.com/YumeYuka/YumeBox",
                    onClick = {
                        design.requests.trySend(AboutDesign.Request.OpenUrl("https://github.com/YumeYuka/YumeBox"))
                    }
                )
                BasicComponent(
                    title = "Clash Meta For Android",
                    summary = "https://github.com/MetaCubeX/ClashMetaForAndroid",
                    onClick = {
                        design.requests.trySend(AboutDesign.Request.OpenUrl("https://github.com/MetaCubeX/ClashMetaForAndroid"))
                    }
                )
                BasicComponent(
                    title = "Mihomo",
                    summary = "https://github.com/MetaCubeX/mihomo",
                    onClick = {
                        design.requests.trySend(AboutDesign.Request.OpenUrl("https://github.com/MetaCubeX/mihomo"))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmallTitle(MLang.section_join_groups)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                SuperArrow(
                    title = MLang.join_qq_group,
                    summary = "https://join.oom-wg.dev",
                    onClick = {
                        design.requests.trySend(AboutDesign.Request.OpenUrl("https://join.oom-wg.dev"))
                    }
                )
                SuperArrow(
                    title = MLang.join_tg_group,
                    summary = "https://t.me/OOM_Group",
                    onClick = {
                        design.requests.trySend(AboutDesign.Request.OpenUrl("https://t.me/OOM_Group"))
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmallTitle(MLang.section_license)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GNU General Public License v3.0",
                        style = MiuixTheme.textStyles.body1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}