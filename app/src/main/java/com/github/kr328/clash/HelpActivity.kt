package com.github.kr328.clash

import com.github.kr328.clash.design.HelpDesign
import kotlinx.coroutines.isActive

class HelpActivity : BaseActivity<HelpDesign>() {
    override suspend fun main() {
        val design = HelpDesign(context = this)

        setContentDesign(design)

        while (isActive) {
            when (val req = design.requests.receive()) {
                is HelpDesign.Request.Open -> startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW).setData(req.uri)
                )

                HelpDesign.Request.Back -> finish()
            }
        }
    }
}