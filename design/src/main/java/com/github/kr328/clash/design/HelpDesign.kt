package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.HelpScreen
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener

class HelpDesign(
    context: Context,
    private val openLink: (Uri) -> Unit,
) : Design<Unit>(context) {
    private var insets by mutableStateOf(Insets.EMPTY)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                HelpScreen(
                    insets = insets,
                    title = context.pushedTitle(R.string.help),
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onClashWiki = { open(R.string.clash_wiki_url) },
                    onClashMetaWiki = { open(R.string.clash_meta_wiki_url) },
                    onClashMetaCore = { open(R.string.clash_meta_core_url) },
                    onRepository = { open(R.string.meta_github_url) },
                )
            }
        }
    }

    private fun open(url: Int) {
        openLink(Uri.parse(context.getString(url)))
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}

private fun Context.pushedTitle(fallback: Int): String {
    val current = (this as? Activity)?.title?.toString()?.ifBlank { null }
    return current ?: getString(fallback)
}
