package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyChainNode
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.ComponentProxyDetailNodeBinding
import com.github.kr328.clash.design.databinding.ComponentProxyDetailRowBinding
import com.github.kr328.clash.design.databinding.DialogProxyDetailBinding
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    groupNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class ShowDetail(val index: Int, val proxy: Proxy) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private var config = ProxyViewConfig(context, uiStore.proxyLine)

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.proxyLine = uiStore.proxyLine
        }
    }

    private val adapter: ProxyPageAdapter
        get() = binding.pagesView.adapter!! as ProxyPageAdapter

    private var horizontalScrolling = false
    private val verticalBottomScrolled: Boolean
        get() = adapter.states[binding.pagesView.currentItem].bottom
    private var urlTesting: Boolean
        get() = adapter.states[binding.pagesView.currentItem].urlTesting
        set(value) {
            adapter.states[binding.pagesView.currentItem].urlTesting = value
        }

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        adapter.updateAdapter(position, proxies, selectable, parent, links)

        adapter.states[position].urlTesting = false

        updateUrlTestButtonStatus()
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter.requestRedrawVisible()
        }
    }

    /**
     * Long-press detail for a proxy. A single node renders as one card; a
     * dialer-proxy chain renders as a connected stack from entry hop to exit hop.
     */
    suspend fun showProxyDetailDialog(proxy: Proxy) {
        withContext(Dispatchers.Main) {
            val binding = DialogProxyDetailBinding
                .inflate(context.layoutInflater, context.root, false)

            val nodes: List<ProxyChainNode> = when {
                proxy.chainDetail.isNotEmpty() -> proxy.chainDetail
                proxy.chain.isNotEmpty() -> proxy.chain.map { ProxyChainNode(name = it) }
                else -> listOf(
                    ProxyChainNode(name = proxy.name, type = proxy.type, server = proxy.server)
                )
            }
            val isChain = nodes.size > 1
            var hasMaskedField = false

            nodes.forEachIndexed { index, node ->
                val nodeBinding = ComponentProxyDetailNodeBinding
                    .inflate(context.layoutInflater, binding.detailContainer, false)

                nodeBinding.connectorView.isVisible = index > 0

                if (isChain) {
                    nodeBinding.hopView.isVisible = true
                    nodeBinding.hopView.setText(
                        when (index) {
                            0 -> R.string.chain_entry
                            nodes.lastIndex -> R.string.chain_exit
                            else -> R.string.chain_middle
                        }
                    )
                }

                nodeBinding.nameView.text = node.name

                // A single-node dialog has no per-hop lookup, so fall back to the
                // proxy's own type/server rather than showing an empty header.
                val type = node.type.ifEmpty { if (isChain) "" else proxy.type }
                val server = node.server.ifEmpty { if (isChain) "" else proxy.server }

                if (type.isNotEmpty()) {
                    nodeBinding.typeView.isVisible = true
                    nodeBinding.typeView.text = type.uppercase()
                }
                if (server.isNotEmpty()) {
                    nodeBinding.serverView.isVisible = true
                    nodeBinding.serverView.text = server
                }
                nodeBinding.metaView.isVisible = type.isNotEmpty() || server.isNotEmpty()

                // Type and Server already head the card; repeating them as rows
                // would just pad the list.
                val details = node.details.filterNot { detail ->
                    (detail.label == LABEL_TYPE && detail.value.equals(type, ignoreCase = true)) ||
                        (detail.label == LABEL_SERVER && detail.value == server)
                }
                nodeBinding.dividerView.isVisible = details.isNotEmpty()

                details.forEach { detail ->
                    val rowBinding = ComponentProxyDetailRowBinding
                        .inflate(context.layoutInflater, nodeBinding.rowsView, false)

                    rowBinding.labelView.text = detail.label
                    rowBinding.valueView.text = detail.value

                    // The core masks these before they ever reach the UI, so there
                    // is no full value to reveal or copy.
                    if (detail.label in MASKED_LABELS) {
                        hasMaskedField = true
                    } else {
                        rowBinding.root.setBackgroundResource(clickableBackground)
                        rowBinding.root.setOnClickListener {
                            copyDetail(detail.label, detail.value)
                        }
                    }

                    nodeBinding.rowsView.addView(rowBinding.root)
                }

                binding.detailContainer.addView(nodeBinding.root)
            }

            binding.maskedHintView.isVisible = true
            binding.maskedHintView.text = if (hasMaskedField) {
                context.getString(R.string.proxy_detail_copy_hint) + "\n" +
                    context.getString(R.string.proxy_detail_masked_hint)
            } else {
                context.getString(R.string.proxy_detail_copy_hint)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(if (isChain) R.string.chain_detail else R.string.proxy_detail)
                .setView(binding.root)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun copyDetail(label: String, value: String) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, value))

        launch { showToast(R.string.copied, ToastDuration.Short) }
    }

    private val clickableBackground =
        context.resolveThemedResourceId(android.R.attr.selectableItemBackground)

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.urlTestView.visibility = View.GONE
            binding.tabLayoutView.visibility = View.GONE
            binding.elevationView.visibility = View.GONE
            binding.pagesView.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
            )

            binding.pagesView.apply {
                adapter = ProxyPageAdapter(
                    surface,
                    config,
                    List(groupNames.size) { index ->
                        ProxyAdapter(
                            config,
                            { name ->
                                requests.trySend(Request.Select(index, name))
                            },
                            { proxy ->
                                requests.trySend(Request.ShowDetail(index, proxy))
                            }
                        )
                    }
                ) {
                    if (it == currentItem)
                        updateUrlTestButtonStatus()
                }

                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrollStateChanged(state: Int) {
                        horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE

                        updateUrlTestButtonStatus()
                    }

                    override fun onPageSelected(position: Int) {
                        uiStore.proxyLastGroup = groupNames[position]
                    }
                })
            }

            TabLayoutMediator(binding.tabLayoutView, binding.pagesView) { tab, index ->
                tab.text = groupNames[index]
            }.attach()

            val initialPosition = groupNames.indexOf(uiStore.proxyLastGroup)

            binding.pagesView.post {
                if (initialPosition > 0)
                    binding.pagesView.setCurrentItem(initialPosition, false)
            }
        }
    }

    fun requestUrlTesting() {
        urlTesting = true

        requests.trySend(Request.UrlTest(binding.pagesView.currentItem))

        updateUrlTestButtonStatus()
    }

    private fun updateUrlTestButtonStatus() {
        if (verticalBottomScrolled || horizontalScrolling || urlTesting) {
            binding.urlTestFloatView.hide()
        } else {
            binding.urlTestFloatView.show()
        }

        if (urlTesting) {
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }
    }
    private companion object {
        const val LABEL_TYPE = "Type"
        const val LABEL_SERVER = "Server"
        val MASKED_LABELS = setOf("UUID", "Password", "Token")
    }
}
