package com.github.kr328.clash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.ListEditorDesign
import com.github.kr328.clash.design.theme.YumeTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ListEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_VALIDATOR_TYPE = "validator_type"
        const val RESULT_ITEMS = "result_items"

        const val VALIDATOR_NONE = "none"
        const val VALIDATOR_DNS = "dns"
        const val VALIDATOR_DOMAIN = "domain"
        const val VALIDATOR_IP = "ip"
        const val VALIDATOR_CIDR = "cidr"

        fun start(
            context: Context,
            title: String,
            items: ArrayList<String>,
            validatorType: String = VALIDATOR_NONE
        ): Intent {
            return Intent(context, ListEditorActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putStringArrayListExtra(EXTRA_ITEMS, items)
                putExtra(EXTRA_VALIDATOR_TYPE, validatorType)
            }
        }
    }

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "编辑列表"
        val items = intent.getStringArrayListExtra(EXTRA_ITEMS) ?: arrayListOf()
        val validatorType = intent.getStringExtra(EXTRA_VALIDATOR_TYPE) ?: VALIDATOR_NONE

        val (validator, validatorMessage) = when (validatorType) {
            VALIDATOR_DNS -> Pair(
                { s: String -> isValidDns(s) },
                "DNS 格式不正确 (如: 8.8.8.8 或 https://dns.google/dns-query)"
            )

            VALIDATOR_DOMAIN -> Pair(
                { s: String -> isValidDomain(s) },
                "域名格式不正确"
            )

            VALIDATOR_IP -> Pair(
                { s: String -> isValidIp(s) },
                "IP 地址格式不正确"
            )

            VALIDATOR_CIDR -> Pair(
                { s: String -> isValidCidr(s) },
                "CIDR 格式不正确 (如: 192.168.1.0/24)"
            )

            else -> Pair({ _: String -> true }, "格式不正确")
        }

        scope.launch {
            val design = ListEditorDesign(
                this@ListEditorActivity,
                title,
                items,
                validator,
                validatorMessage,
                onSave = { resultItems ->
                    val resultIntent = Intent().apply {
                        putStringArrayListExtra(RESULT_ITEMS, ArrayList(resultItems))
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
                    ListEditorDesign.Request.Save -> {
                    }

                    ListEditorDesign.Request.Close -> {
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

    private fun isValidDns(dns: String): Boolean {
        return when {
            dns.startsWith("https://") || dns.startsWith("tls://") -> dns.length > 10
            dns.contains(":") -> {
                val parts = dns.split(":")
                parts.size == 2 && isValidIp(parts[0]) && parts[1].toIntOrNull() != null
            }

            else -> isValidIp(dns) || isValidDomain(dns)
        }
    }

    private fun isValidDomain(domain: String): Boolean {
        return domain.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"))
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all {
            val num = it.toIntOrNull()
            num != null && num in 0..255
        }
    }

    private fun isValidCidr(cidr: String): Boolean {
        val parts = cidr.split("/")
        if (parts.size != 2) return false
        val mask = parts[1].toIntOrNull()
        return isValidIp(parts[0]) && mask != null && mask in 0..32
    }
}

