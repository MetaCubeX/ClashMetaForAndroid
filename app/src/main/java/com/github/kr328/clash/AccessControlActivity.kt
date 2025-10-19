package com.github.kr328.clash

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.design.AccessControlDesign
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.design.util.toAppInfo
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

class AccessControlActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var design: AccessControlDesign
    private lateinit var service: ServiceStore
    private lateinit var uiStore: UiStore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        design.hasPermission = isGranted
        if (isGranted) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = ServiceStore(this)
        uiStore = UiStore(this)

        design = AccessControlDesign(this, uiStore, service.accessControlPackages)

        setContent {
            YumeTheme {
                design.Content()
            }
        }

        design.requests.receiveAsFlow().onEach {
            when (it) {
                AccessControlDesign.Request.ReloadApps -> {
                    loadApps()
                }

                AccessControlDesign.Request.ImportFromClipboard -> importFromClipboard()
                AccessControlDesign.Request.ExportToClipboard -> exportToClipboard()
                AccessControlDesign.Request.RequestPermission -> requestPermission()
            }
        }.launchIn(lifecycleScope)

        checkAndLoadApps()
    }

    private fun checkAndLoadApps() {
        if (checkPermission()) {
            design.hasPermission = true
            loadApps()
        } else {
            design.hasPermission = false
            design.isLoading = false
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            design.hasPermission = true
            loadApps()
        } else {
            design.hasPermission = true
            loadApps()
        }
    }

    override fun onPause() {
        super.onPause()
        val newPackages = design.selected.filter { it.value }.keys
        if (newPackages != service.accessControlPackages) {
            service.accessControlPackages = newPackages
            scope.launch {
                try {
                    stopClashService()
                    startClashService()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadApps() {
        scope.launch {
            design.isLoading = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = packageManager
                    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

                    packages.asSequence()
                        .filter { it.packageName != packageName }
                        .filter { it.applicationInfo != null }
                        .filter {
                            it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true ||
                                    it.applicationInfo!!.uid < android.os.Process.FIRST_APPLICATION_UID
                        }
                        .map { it.toAppInfo(pm) }
                        .toList()
                }
                design.updateApps(apps)
            } catch (e: Exception) {
                e.printStackTrace()
                design.isLoading = false
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService<ClipboardManager>()
        val data = clipboard?.primaryClip
        if (data != null && data.itemCount > 0) {
            val text = data.getItemAt(0).text
            if (text != null) {
                val packages = text.split('\n').filter { it.isNotBlank() }.toSet()
                design.importSelection(packages)
            }
        }
    }

    private fun exportToClipboard() {
        val clipboard = getSystemService<ClipboardManager>()
        val packages = design.selected.filter { it.value }.keys
        val data = ClipData.newPlainText("packages", packages.joinToString("\n"))
        clipboard?.setPrimaryClip(data)
    }
}
