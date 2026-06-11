package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.ClickablePreference
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.GeoDataSourcePreset
import com.github.kr328.clash.service.util.GeoDataSources

class GeoDataSourceSettingsDesign(
    context: Context,
    private val config: Config,
) : Design<GeoDataSourceSettingsDesign.Request>(context) {
    data class Config(
        var preset: GeoDataSourcePreset,
        var customGeoIp: String?,
        var customGeoSite: String?,
        var customMmdb: String?,
        var customAsn: String?,
    )

    enum class Request {
        UpdateGeoDatabases,
        ImportGeoIp,
        ImportGeoSite,
        ImportCountryMmdb,
        ImportAsnMmdb,
    }

    private var updateGeoClickable: ClickablePreference? = null

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private fun mirrorSummaryText(): CharSequence {
        if (config.preset == GeoDataSourcePreset.Custom) {
            return context.getString(R.string.geo_preset_mirrors_custom)
        }
        val u = GeoDataSources.defaults(config.preset)
        return buildString {
            append(context.getString(R.string.geo_preset_mirrors_title))
            append('\n')
            append(context.getString(R.string.geox_geoip)).append(": ").append(u.geoIp).append('\n')
            append(context.getString(R.string.geox_geosite)).append(": ").append(u.geoSite).append('\n')
            append(context.getString(R.string.geox_mmdb)).append(": ").append(u.mmdb).append('\n')
            append(context.getString(R.string.geox_asn)).append(": ").append(u.asn)
        }
    }

    init {
        binding.surface = surface
        binding.header.screenTitle.text = (context as? Activity)?.title?.toString().orEmpty()

        val customDependencies: MutableList<Preference> = mutableListOf()
        var mirrorTips: TipsPreference? = null

        val screen = preferenceScreen(context) {
            tips(R.string.geo_data_source_intro)

            val preset = selectableList(
                value = config::preset,
                values = GeoDataSourcePreset.values(),
                valuesText = arrayOf(
                    R.string.geo_preset_global,
                    R.string.geo_preset_cn_friendly,
                    R.string.geo_preset_ru_friendly,
                    R.string.geo_preset_custom,
                ),
                title = R.string.geo_data_source_preset,
            ) {
                listener = OnChangedListener {
                    val isCustom = config.preset == GeoDataSourcePreset.Custom
                    customDependencies.forEach { it.enabled = isCustom }
                    mirrorTips?.text = mirrorSummaryText()
                }
            }

            mirrorTips = plainTips(mirrorSummaryText()) {
                enabled = false
            }

            editableText(
                value = config::customGeoIp,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_geoip,
                placeholder = R.string.dont_modify,
                configure = customDependencies::add,
            )

            editableText(
                value = config::customGeoSite,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_geosite,
                placeholder = R.string.dont_modify,
                configure = customDependencies::add,
            )

            editableText(
                value = config::customMmdb,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_mmdb,
                placeholder = R.string.dont_modify,
                configure = customDependencies::add,
            )

            editableText(
                value = config::customAsn,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_asn,
                placeholder = R.string.dont_modify,
                configure = customDependencies::add,
            )

            category(R.string.geo_update_online)

            updateGeoClickable = clickable(
                title = R.string.geo_update_action,
                icon = R.drawable.ic_baseline_cloud_download,
            ) {
                clicked { requests.trySend(Request.UpdateGeoDatabases) }
            }

            category(R.string.import_local_geo_databases)

            clickable(title = R.string.import_geoip_file) {
                clicked { requests.trySend(Request.ImportGeoIp) }
            }
            clickable(title = R.string.import_geosite_file) {
                clicked { requests.trySend(Request.ImportGeoSite) }
            }
            clickable(title = R.string.import_country_file) {
                clicked { requests.trySend(Request.ImportCountryMmdb) }
            }
            clickable(title = R.string.import_asn_file) {
                clicked { requests.trySend(Request.ImportAsnMmdb) }
            }

            preset.listener?.onChanged()
        }

        binding.content.addView(screen.root)
    }

    fun setGeoUpdateBusy(busy: Boolean) {
        updateGeoClickable?.apply {
            enabled = !busy
            summary = if (busy) context.getString(R.string.geo_update_progress) else null
        }
    }
}
