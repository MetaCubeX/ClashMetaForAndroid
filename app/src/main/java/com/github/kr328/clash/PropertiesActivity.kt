package com.github.kr328.clash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.launch
import java.util.*

class PropertiesActivity : ComponentActivity() {
    private lateinit var uuid: UUID
    private var current: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uuid = intent.uuid ?: run { finish(); return }
        lifecycleScope.launch {
            current = withProfile { queryByUUID(uuid) }
            if (current == null) {
                finish(); return@launch
            }
            setCompose()
        }
    }

    private fun setCompose() {
        val origin = current ?: return
        setContent {
            var working by remember { mutableStateOf(origin) }
            var processing by remember { mutableStateOf(false) }
            var progress by remember { mutableStateOf("") }

            Surface(color = MaterialTheme.colorScheme.background) {
                androidx.compose.material3.Text("此页面已废弃，请使用新建页保存")
                finish()
            }
        }
    }

    private fun statusToText(st: com.github.kr328.clash.core.model.FetchStatus): String {
        return when (st.action) {
            com.github.kr328.clash.core.model.FetchStatus.Action.FetchConfiguration -> getString(
                R.string.format_fetching_configuration,
                st.args[0]
            )

            com.github.kr328.clash.core.model.FetchStatus.Action.FetchProviders -> getString(
                R.string.format_fetching_provider,
                st.args[0]
            ) + " ${st.progress}/${st.max}"

            com.github.kr328.clash.core.model.FetchStatus.Action.Verifying -> getString(R.string.verifying) + " ${st.progress}/${st.max}"
        }
    }


}