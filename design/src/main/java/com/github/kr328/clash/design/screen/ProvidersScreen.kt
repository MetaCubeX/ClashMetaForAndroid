package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ProvidersDesign
import com.github.kr328.clash.design.components.EmptyState
import com.github.kr328.clash.design.util.toRelativeTimeString
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ProvidersScreen(design: ProvidersDesign) {
    if (design.providers.isEmpty()) {
        EmptyProviderScreen()
    } else {
        ProvidersContentScreen(design = design)
    }
}

@Composable
private fun ProvidersContentScreen(design: ProvidersDesign) {
    val scrollBehavior = MiuixScrollBehavior()
    var topRotate by remember { mutableStateOf(0f) }
    LaunchedEffect(design.allUpdating) {
        if (design.allUpdating) {
            while (isActive && design.allUpdating) {
                topRotate = (topRotate + 6f) % 360f // ~60fps
                delay(16)
            }
        }
        topRotate = 0f
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.providers_page_title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = 24.dp),
                        onClick = { design.requestUpdateAll() }, enabled = !design.allUpdating
                    ) {
                        Icon(
                            MiuixIcons.Useful.Refresh,
                            contentDescription = MLang.action_update_all,
                            modifier = Modifier
                                .rotate(if (design.allUpdating) topRotate else 0f)
                        )
                    }
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
            item { Spacer(modifier = Modifier.height(16.dp)) }
            items(design.providers, key = { it.name }) { p ->
                val isUpdating = design.isProviderUpdating(p.name)
                ProviderRow(provider = p, isUpdating = isUpdating) {
                    if (p.vehicleType != Provider.VehicleType.Inline) {
                        design.markProviderUpdating(p.name, true)
                        design.requests.trySend(ProvidersDesign.Request.Update(p))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: Provider, isUpdating: Boolean, onUpdate: () -> Unit) {
    val summaryText = buildString {
        append("${MLang.type_label}${provider.type} · ${MLang.vehicle_label}${provider.vehicleType}")
        val timeText = provider.updatedAt.toRelativeTimeString()
        append("\n${MLang.update_time_label}$timeText")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        onClick = { /* no-op */ }
    ) {
        BasicComponent(
            title = provider.name,
            summary = summaryText,
            rightActions = {
                val updatable = provider.vehicleType != Provider.VehicleType.Inline
                var rowRotate by remember(isUpdating) { mutableStateOf(0f) }
                LaunchedEffect(isUpdating) {
                    if (isUpdating) {
                        while (isActive) {
                            rowRotate = (rowRotate + 6f) % 360f
                            delay(16)
                        }
                    }
                    rowRotate = 0f
                }
                IconButton(onClick = onUpdate, enabled = updatable && !isUpdating) {
                    Icon(
                        MiuixIcons.Useful.Refresh,
                        contentDescription = MLang.action_update,
                        modifier = Modifier.rotate(if (isUpdating) rowRotate else 0f)
                    )
                }
            }
        )
    }
}

@Composable
fun EmptyProviderScreen() {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.empty_title,
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            EmptyState(
                message = MLang.empty_text,
                hint = MLang.empty_hint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    )
}

