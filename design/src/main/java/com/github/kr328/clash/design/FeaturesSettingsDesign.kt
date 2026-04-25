package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.selectableList
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class FeaturesSettingsDesign(
    context: Context,
    configuration: ConfigurationOverride,
) : Design<FeaturesSettingsDesign.Request>(context) {
    enum class Request {
        StartMetaFeatures,
        StartNetwork,
        StartGeoDataSource,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface
        binding.toolbar.title = (context as? Activity)?.title?.toString().orEmpty()
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }

        val booleanValues: Array<Boolean?> = arrayOf(
            null,
            true,
            false
        )
        val booleanValuesText: Array<Int> = arrayOf(
            R.string.dont_modify,
            R.string.enabled,
            R.string.disabled
        )

        val screen = preferenceScreen(context) {
            tips(R.string.features_intro)

            clickable(
                icon = R.drawable.ic_baseline_extension,
                title = R.string.meta_features,
                summary = R.string.advanced_meta_features_summary,
            ) {
                clicked {
                    requests.trySend(Request.StartMetaFeatures)
                }
            }

            clickable(
                icon = R.drawable.ic_baseline_vpn_lock,
                title = R.string.network,
            ) {
                clicked {
                    requests.trySend(Request.StartNetwork)
                }
            }

            clickable(
                icon = R.drawable.ic_baseline_language,
                title = R.string.geo_data_source,
                summary = R.string.geo_data_source_summary,
            ) {
                clicked {
                    requests.trySend(Request.StartGeoDataSource)
                }
            }

            selectableList(
                value = configuration::unifiedDelay,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.unified_delay,
            )

            selectableList(
                value = configuration::geodataMode,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.geodata_mode,
            )

            selectableList(
                value = configuration::tcpConcurrent,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.tcp_concurrent,
            )
        }

        binding.content.addView(screen.root)
    }
}
