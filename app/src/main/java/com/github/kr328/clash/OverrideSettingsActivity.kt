package com.github.kr328.clash

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.OverrideSettingsDesign
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*

class OverrideSettingsActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var configuration: ConfigurationOverride
    private lateinit var design: OverrideSettingsDesign


    private var currentListEditorCallback: ((List<String>) -> Unit)? = null
    private val listEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val items = result.data?.getStringArrayListExtra(ListEditorActivity.RESULT_ITEMS)
                if (items != null && currentListEditorCallback != null) {
                    currentListEditorCallback?.invoke(items)
                    currentListEditorCallback = null
                }
            }
        }


    private var currentKeyValueEditorCallback: ((Map<String, String>) -> Unit)? = null
    private val keyValueEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                @Suppress("UNCHECKED_CAST")
                val map = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getSerializableExtra(
                        KeyValueEditorActivity.RESULT_MAP,
                        HashMap::class.java
                    ) as? HashMap<String, String>
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getSerializableExtra(KeyValueEditorActivity.RESULT_MAP) as? HashMap<String, String>
                }
                if (map != null && currentKeyValueEditorCallback != null) {
                    currentKeyValueEditorCallback?.invoke(map)
                    currentKeyValueEditorCallback = null
                }
            }
        }

    private fun launchListEditor(
        title: String,
        items: List<String>,
        validatorType: String,
        callback: (List<String>) -> Unit
    ) {
        currentListEditorCallback = callback
        val intent = ListEditorActivity.start(
            this@OverrideSettingsActivity,
            title,
            ArrayList(items),
            validatorType
        )
        listEditorLauncher.launch(intent)
    }

    private fun launchKeyValueEditor(
        title: String,
        items: Map<String, String>,
        keyPlaceholder: String = "键",
        valuePlaceholder: String = "值",
        callback: (Map<String, String>) -> Unit
    ) {
        currentKeyValueEditorCallback = callback
        val intent = KeyValueEditorActivity.start(
            this@OverrideSettingsActivity,
            title,
            HashMap(items),
            keyPlaceholder,
            valuePlaceholder
        )
        keyValueEditorLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scope.launch {
            val config = withClash { queryOverride(Clash.OverrideSlot.Persist) } ?: ConfigurationOverride()
            configuration = config

            design = OverrideSettingsDesign(
                this@OverrideSettingsActivity,
                config,
                onConfigChange = { newConfig ->
                    configuration = newConfig
                }
            )

            withContext(Dispatchers.Main) {
                setContent {
                    YumeTheme {
                        design.Content()
                    }
                }
            }


            for (req in design.requests) {
                when (req) {
                    OverrideSettingsDesign.Request.Close -> finish()
                    OverrideSettingsDesign.Request.ResetOverride -> {
                        scope.launch {
                            try {
                                withClash { clearOverride(Clash.OverrideSlot.Persist) }
                                val newConfig =
                                    withClash { queryOverride(Clash.OverrideSlot.Persist) } ?: ConfigurationOverride()
                                configuration = newConfig
                                design.config = newConfig
                                runOnUiThread {
                                    Toast.makeText(this@OverrideSettingsActivity, "重置成功", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@OverrideSettingsActivity,
                                        "重置失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    OverrideSettingsDesign.Request.Save -> {
                        scope.launch {
                            try {
                                withClash { patchOverride(Clash.OverrideSlot.Persist, design.config) }
                                runOnUiThread {
                                    Toast.makeText(this@OverrideSettingsActivity, "保存成功", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@OverrideSettingsActivity,
                                        "保存失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }


                    OverrideSettingsDesign.Request.EditAuthentication -> {
                        launchListEditor(
                            "认证",
                            design.config.authentication ?: emptyList(),
                            ListEditorActivity.VALIDATOR_NONE
                        ) { newItems ->
                            design.config = design.config.copy(authentication = newItems)
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditCorsOrigins -> {
                        launchListEditor(
                            "外部控制器 CORS 允许域",
                            design.config.externalControllerCors.allowOrigins ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DOMAIN
                        ) { newItems ->
                            design.config = design.config.copy(
                                externalControllerCors = design.config.externalControllerCors.copy(allowOrigins = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditNameServers -> {
                        launchListEditor(
                            "上游 DNS",
                            design.config.dns.nameServer ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DNS
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(nameServer = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditFallbackDns -> {
                        launchListEditor(
                            "回退 DNS",
                            design.config.dns.fallback ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DNS
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(fallback = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditDefaultDns -> {
                        launchListEditor(
                            "默认 DNS",
                            design.config.dns.defaultServer ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DNS
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(defaultServer = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditFakeIpFilter -> {
                        launchListEditor(
                            "FakeIP 过滤",
                            design.config.dns.fakeIpFilter ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DOMAIN
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(fakeIpFilter = newItems)
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditFallbackDomains -> {
                        launchListEditor(
                            "回退域名",
                            design.config.dns.fallbackFilter.domain ?: emptyList(),
                            ListEditorActivity.VALIDATOR_DOMAIN
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(
                                    fallbackFilter = design.config.dns.fallbackFilter.copy(domain = newItems)
                                )
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditFallbackIpCidr -> {
                        launchListEditor(
                            "回退网段",
                            design.config.dns.fallbackFilter.ipcidr ?: emptyList(),
                            ListEditorActivity.VALIDATOR_CIDR
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(
                                    fallbackFilter = design.config.dns.fallbackFilter.copy(ipcidr = newItems)
                                )
                            )
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditHosts -> {
                        launchKeyValueEditor(
                            "Hosts",
                            design.config.hosts ?: emptyMap(),
                            "域名",
                            "IP地址"
                        ) { newItems ->
                            design.config = design.config.copy(hosts = newItems)
                            configuration = design.config
                        }
                    }

                    OverrideSettingsDesign.Request.EditNameServerPolicy -> {
                        launchKeyValueEditor(
                            "NameServer 策略",
                            design.config.dns.nameserverPolicy ?: emptyMap(),
                            "域名",
                            "DNS 服务器"
                        ) { newItems ->
                            design.config = design.config.copy(
                                dns = design.config.dns.copy(nameserverPolicy = newItems)
                            )
                            configuration = design.config
                        }
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
