package com.github.kr328.clash.design

import android.content.Context
import android.net.Uri
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class HelpDesign(
    context: Context,
    openLink: (Uri) -> Unit,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            tips(R.string.tips_help)

            category(R.string.document)

            clickable(
                title = R.string.clash_wiki,
                summary = R.string.clash_wiki_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.clash_wiki_url)))
                }
            }

            clickable(
                title = R.string.clash_meta_wiki,
                summary = R.string.clash_meta_wiki_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.clash_meta_wiki_url)))
                }
            }

            category(R.string.sources)

            // This build is a modified fork, so its own corresponding source has
            // to be reachable from the app itself — the upstream link below does
            // not carry these modifications (GPLv3 section 6).
            clickable(
                title = R.string.modified_source,
                summary = R.string.modified_source_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.modified_source_url)))
                }
            }

            clickable(
                title = R.string.clash_meta_core,
                summary = R.string.clash_meta_core_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.clash_meta_core_url)))
                }
            }

            clickable(
                title = R.string.clash_meta_for_android,
                summary = R.string.meta_github_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.meta_github_url)))
                }
            }

            // Reachable from the app rather than only from the repository: a user
            // who sideloaded the APK has no other route to either document.
            clickable(
                title = R.string.license_gplv3,
                summary = R.string.license_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.license_url)))
                }
            }

            clickable(
                title = R.string.privacy_policy,
                summary = R.string.privacy_policy_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.privacy_policy_url)))
                }
            }
        }

        binding.content.addView(screen.root)
    }
}