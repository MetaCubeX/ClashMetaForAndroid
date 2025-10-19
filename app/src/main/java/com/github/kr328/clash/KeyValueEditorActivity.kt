package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.KeyValueEditorDesign
import com.github.kr328.clash.design.theme.YumeTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Serializable

class KeyValueEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MAP = "map"
        const val EXTRA_KEY_PLACEHOLDER = "key_placeholder"
        const val EXTRA_VALUE_PLACEHOLDER = "value_placeholder"
        const val RESULT_MAP = "result_map"

        fun start(
            context: Context,
            title: String,
            map: HashMap<String, String>,
            keyPlaceholder: String = "键",
            valuePlaceholder: String = "值"
        ): Intent {
            return Intent(context, KeyValueEditorActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MAP, map as Serializable)
                putExtra(EXTRA_KEY_PLACEHOLDER, keyPlaceholder)
                putExtra(EXTRA_VALUE_PLACEHOLDER, valuePlaceholder)
            }
        }
    }

    private val scope = MainScope()

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "编辑键值对"
        val map = (intent.getSerializableExtra(EXTRA_MAP) as? HashMap<String, String>) ?: hashMapOf()
        val keyPlaceholder = intent.getStringExtra(EXTRA_KEY_PLACEHOLDER) ?: "键"
        val valuePlaceholder = intent.getStringExtra(EXTRA_VALUE_PLACEHOLDER) ?: "值"

        scope.launch {
            val design = KeyValueEditorDesign(
                this@KeyValueEditorActivity,
                title,
                map,
                keyValidator = { it.isNotBlank() },
                valueValidator = { it.isNotBlank() },
                keyValidatorMessage = "键不能为空",
                valueValidatorMessage = "值不能为空",
                keyPlaceholder = keyPlaceholder,
                valuePlaceholder = valuePlaceholder,
                onSave = { resultMap ->
                    val resultIntent = Intent().apply {
                        putExtra(RESULT_MAP, HashMap(resultMap) as Serializable)
                    }
                    setResult(RESULT_OK, resultIntent)
                },
                onRequestClose = {
                    finish()
                }
            )

            setContent {
                YumeTheme {
                    design.Content()
                }
            }

            for (req in design.requests) {
                when (req) {
                    KeyValueEditorDesign.Request.Save -> {
                    }

                    KeyValueEditorDesign.Request.Close -> {
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

