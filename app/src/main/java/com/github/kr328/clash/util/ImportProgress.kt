package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.util.applyFetchStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Modal progress dialog + transient-error retry around `commit(uuid)`.
 *
 * The same FetchStatus-driven progress UI the home-FAB import already shows,
 * extracted so every other import entry point (Profiles → Manage → URL,
 * clipboard paste, intent/QR scan) can use it identically. Before this
 * helper, those three paths called `withProfile { commit(uuid) }` directly
 * and the user got zero visual feedback while subscription YAML +
 * rule-providers downloaded — easily 5–15s on mobile networks, looking
 * indistinguishable from a frozen UI.
 *
 * The block is also wrapped in [ImportRetry.withTransientRetry] so a single
 * DNS or EOF blip on a flaky network gets retried automatically instead of
 * surfacing as a "import failed" dialog the user has to dismiss and retry by
 * hand.
 */
suspend fun Context.commitProfileWithProgress(uuid: UUID) {
    val ctx = this
    withModelProgressBar {
        configure {
            isIndeterminate = true
            text = ctx.getString(R.string.initializing)
        }
        coroutineScope {
            ImportRetry.withTransientRetry {
                withProfile {
                    commit(uuid) { status ->
                        launch {
                            configure {
                                applyFetchStatus(ctx, status)
                            }
                        }
                    }
                }
            }
        }
    }
}
