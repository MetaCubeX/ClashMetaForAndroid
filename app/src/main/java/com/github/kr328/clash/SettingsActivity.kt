@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.github.kr328.clash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.theme.YumeTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YumeTheme {
                val items = remember {
                    mutableStateListOf(
                        SettingEntry("App Settings") { startActivity(AppSettingsActivity::class.intent) },
                        SettingEntry("Network Settings") { startActivity(NetworkSettingsActivity::class.intent) },
                        SettingEntry("Override Settings") { startActivity(OverrideSettingsActivity::class.intent) },
                        SettingEntry("Meta Features") { startActivity(MetaFeatureSettingsActivity::class.intent) },
                    )
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Settings") }) }
                ) { inner ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(inner)) {
                        SettingsList(items)
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsList(entries: List<SettingEntry>) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(entries, key = { it.title }) { e ->
                ListItem(
                    headlineContent = { Text(e.title) },
                    trailingContent = { TextButton(onClick = e.onClick) { Text("Open") } }
                )
                HorizontalDivider()
            }
        }
    }
}

private data class SettingEntry(val title: String, val onClick: () -> Unit)