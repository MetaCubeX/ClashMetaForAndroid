package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyChainNode
import com.github.kr328.clash.core.model.ProxyDetail
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DialogProxyChainBinding
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
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
        data class ShowChain(val index: Int, val proxy: Proxy) : Request()
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

    suspend fun showChainDialog(proxy: Proxy) {
        withContext(Dispatchers.Main) {
            val binding = DialogProxyChainBinding
                .inflate(context.layoutInflater, context.root, false)

            val colorSurfaceVariant = context.resolveThemedColor(
                com.google.android.material.R.attr.colorSurfaceVariant
            )
            val colorOnSurface = context.resolveThemedColor(
                com.google.android.material.R.attr.colorOnSurface
            )
            val colorOnSurfaceVariant = context.resolveThemedColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
            val colorOutline = context.resolveThemedColor(
                com.google.android.material.R.attr.colorOutline
            )
            val colorPrimary = context.resolveThemedColor(
                com.google.android.material.R.attr.colorPrimary
            )
            val colorOnPrimary = context.resolveThemedColor(
                com.google.android.material.R.attr.colorOnPrimary
            )

            val chainNodes: List<ProxyChainNode> = if (proxy.chainDetail.isNotEmpty()) {
                proxy.chainDetail
            } else {
                proxy.chain.map { ProxyChainNode(name = it) }
            }

            val container = binding.chainContainer
            container.removeAllViews()

            fun makeCardBackground(): android.graphics.drawable.GradientDrawable {
                return android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 12.dp.toFloat()
                    setColor(colorSurfaceVariant)
                }
            }

            fun makeDivider(): View {
                return android.view.View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dp
                    ).apply { topMargin = 10.dp; bottomMargin = 10.dp }
                    background = android.graphics.drawable.ColorDrawable(colorOutline)
                }
            }

            fun applyTextWrap(view: TextView) {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    // BREAK_STRATEGY_HIGH_QUALITY and HYPHENATION_FREQUENCY_LOCALE
                    view.breakStrategy = 1
                    view.hyphenationFrequency = 1
                }
            }

            fun makeDetailRow(detail: ProxyDetail): LinearLayout {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, 3.dp, 0, 3.dp)
                }

                row.addView(TextView(context).apply {
                    text = detail.label
                    setTextSize(12f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colorOnSurfaceVariant)
                }, LinearLayout.LayoutParams(
                    WRAP_CONTENT_LABEL_WIDTH,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 12.dp })

                val valueText = TextView(context).apply {
                    text = detail.value
                    setTextSize(13f)
                    setTextColor(colorOnSurface)
                    includeFontPadding = false
                    applyTextWrap(this)
                }

                if (detail.label == "UUID" || detail.label == "Password" ||
                    detail.label == "Token"
                ) {
                    valueText.text = SpannableString(detail.value).apply {
                        setSpan(
                            StyleSpan(Typeface.ITALIC),
                            0,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    valueText.setTextColor(colorOnSurfaceVariant)
                    valueText.setOnClickListener {
                        if (valueText.tag == null) {
                            valueText.text = detail.value
                            valueText.tag = "revealed"
                            valueText.setTextColor(colorOnSurface)
                            Toast.makeText(
                                context,
                                R.string.chain_secret_revealed,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            valueText.text = SpannableString(detail.value).apply {
                                setSpan(
                                    StyleSpan(Typeface.ITALIC),
                                    0,
                                    length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            valueText.tag = null
                            valueText.setTextColor(colorOnSurfaceVariant)
                            Toast.makeText(
                                context,
                                R.string.chain_secret_masked,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                row.addView(valueText, LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ))

                return row
            }

            chainNodes.forEachIndexed { index, node ->
                val isEntry = index == 0
                val isExit = index == chainNodes.size - 1

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(14.dp, 12.dp, 14.dp, 12.dp)
                    background = makeCardBackground()
                }

                val header = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                // Hop badges only make sense for real chains (multi-hop).
                if (chainNodes.size > 1) {
                    val badge = TextView(context).apply {
                        val label = when {
                            isEntry -> context.getString(R.string.chain_entry)
                            isExit -> context.getString(R.string.chain_exit)
                            else -> context.getString(R.string.chain_middle)
                        }
                        text = label
                        setTextSize(11f)
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(colorOnPrimary)
                        setPadding(10.dp, 3.dp, 10.dp, 3.dp)
                    }
                    val badgeBackground = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 6.dp.toFloat()
                        setColor(colorPrimary)
                    }
                    badge.background = badgeBackground
                    header.addView(badge, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 10.dp })
                }

                header.addView(TextView(context).apply {
                    text = node.name
                    setTextSize(15f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colorOnSurface)
                    applyTextWrap(this)
                }, LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ))

                if (node.type.isNotEmpty() && node.type != proxy.type) {
                    header.addView(TextView(context).apply {
                        text = node.type
                        setTextSize(11f)
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(colorPrimary)
                        setPadding(8.dp, 2.dp, 8.dp, 2.dp)
                    })
                }
                card.addView(header)

                if (node.details.isNotEmpty()) {
                    card.addView(makeDivider())

                    node.details.forEach { detail ->
                        card.addView(makeDetailRow(detail))
                    }
                }

                container.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10.dp })

                if (!isExit) {
                    container.addView(TextView(context).apply {
                        text = "\u2193"
                        setTextSize(16f)
                        gravity = Gravity.CENTER
                        setTextColor(colorOnSurfaceVariant)
                        setPadding(0, 4.dp, 0, 4.dp)
                    })
                }
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.chain_detail)
                .setView(binding.root)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val WRAP_CONTENT_LABEL_WIDTH = 96
    }

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
                                requests.trySend(Request.ShowChain(index, proxy))
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
}
