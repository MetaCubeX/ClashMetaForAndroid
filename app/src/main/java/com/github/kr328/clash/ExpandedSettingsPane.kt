package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.ExpandedSettingsDesign
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.design.OverrideSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver

class ExpandedSettingsPane(
    private val activity: BaseActivity<*>,
    private val running: Boolean,
) {
    lateinit var design: ExpandedSettingsDesign
        private set

    lateinit var appDesign: AppSettingsDesign
        private set

    lateinit var networkDesign: NetworkSettingsDesign
        private set

    lateinit var overrideDesign: OverrideSettingsDesign
        private set

    lateinit var metaFeatureDesign: MetaFeatureSettingsDesign
        private set

    private val serviceStore by lazy { ServiceStore(activity) }
    private val behavior by lazy { activity.createAppSettingsBehavior() }
    private var currentSection = ExpandedSettingsDesign.Section.App
    private lateinit var configuration: com.github.kr328.clash.core.model.ConfigurationOverride

    suspend fun initialize(): ExpandedSettingsDesign {
        configuration = loadPersistOverride()
        design = ExpandedSettingsDesign(activity)
        rebuildDetailDesigns()
        showSection(ExpandedSettingsDesign.Section.App)
        return design
    }

    suspend fun save() {
        savePersistOverride(configuration)
    }

    suspend fun handleExpandedRequest(request: ExpandedSettingsDesign.Request) {
        when (request) {
            is ExpandedSettingsDesign.Request.SelectSection -> showSection(request.section)
            ExpandedSettingsDesign.Request.ResetSection -> handleReset()
        }
    }

    suspend fun handleAppRequest(request: AppSettingsDesign.Request) {
        when (request) {
            AppSettingsDesign.Request.ReCreateAllActivities ->
                ApplicationObserver.createdActivities.forEach { it.recreate() }
        }
    }

    suspend fun handleNetworkRequest(request: NetworkSettingsDesign.Request) {
        when (request) {
            NetworkSettingsDesign.Request.StartAccessControlList ->
                activity.startActivity(AccessControlActivity::class.intent)
        }
    }

    suspend fun handleOverrideRequest(request: OverrideSettingsDesign.Request) {
        when (request) {
            OverrideSettingsDesign.Request.ResetOverride -> handleReset()
        }
    }

    suspend fun handleMetaFeatureRequest(request: MetaFeatureSettingsDesign.Request) {
        when (request) {
            MetaFeatureSettingsDesign.Request.ResetOverride -> handleReset()
            MetaFeatureSettingsDesign.Request.ImportGeoIp,
            MetaFeatureSettingsDesign.Request.ImportGeoSite,
            MetaFeatureSettingsDesign.Request.ImportCountry,
            MetaFeatureSettingsDesign.Request.ImportASN -> activity.importGeoFile(request)
        }
    }

    private suspend fun rebuildDetailDesigns() {
        appDesign = AppSettingsDesign(
            activity,
            activity.uiPreferences,
            serviceStore,
            behavior,
            running,
            { activity.updateMainActivityAliasVisibility(it) },
            embedded = true,
        )
        networkDesign = NetworkSettingsDesign(
            activity,
            activity.uiPreferences,
            serviceStore,
            running,
            embedded = true,
        )
        overrideDesign = OverrideSettingsDesign(
            activity,
            configuration,
            embedded = true,
        )
        metaFeatureDesign = MetaFeatureSettingsDesign(
            activity,
            configuration,
            embedded = true,
        )
    }

    private suspend fun showSection(section: ExpandedSettingsDesign.Section) {
        currentSection = section

        when (section) {
            ExpandedSettingsDesign.Section.App -> {
                design.showSection(section, showReset = false)
                design.showDetail(appDesign)
            }
            ExpandedSettingsDesign.Section.Network -> {
                design.showSection(section, showReset = false)
                design.showDetail(networkDesign)
            }
            ExpandedSettingsDesign.Section.Override -> {
                design.showSection(section, showReset = true)
                design.showDetail(overrideDesign)
            }
            ExpandedSettingsDesign.Section.MetaFeature -> {
                design.showSection(section, showReset = true)
                design.showDetail(metaFeatureDesign)
            }
        }
    }

    private suspend fun handleReset() {
        val confirmed = when (currentSection) {
            ExpandedSettingsDesign.Section.Override -> overrideDesign.requestResetConfirm()
            ExpandedSettingsDesign.Section.MetaFeature -> metaFeatureDesign.requestResetConfirm()
            else -> false
        }

        if (!confirmed) {
            return
        }

        clearPersistOverride()
        configuration = loadPersistOverride()
        rebuildDetailDesigns()
        showSection(currentSection)
    }
}
