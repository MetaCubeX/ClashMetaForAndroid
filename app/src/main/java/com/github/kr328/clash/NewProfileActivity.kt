package com.github.kr328.clash

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.NewProfileDesign
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*

class NewProfileActivity : BaseActivity<NewProfileDesign>() {
    private val self: NewProfileActivity
        get() = this

    private companion object {
        const val DOWNLOAD_COMPLETE_DELAY = 800L
    }

    // 编辑模式相关
    private var editingProfile: Profile? = null
    private var isEditMode = false

    // 文件导入相关
    private var pendingProfileUuid: UUID? = null
    private var isFileImported = false

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            launch {
                try {
                    val scheme = selectedUri.scheme?.lowercase()
                    if (scheme != "content" && scheme != "file") {
                        showToast("选择的文件格式不支持，请重新选择")
                        return@launch
                    }
                    handleFileSelection(selectedUri)
                } catch (e: Exception) {
                    showToast("文件选择失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(
                this@NewProfileActivity,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createDownloadObserver(design: NewProfileDesign, defaultResourceName: String = ""): IFetchObserver {
        return IFetchObserver { status ->
            launch(Dispatchers.Main) {
                when (status.action) {
                    FetchStatus.Action.FetchConfiguration -> {
                        val resourceName = status.args.firstOrNull() ?: defaultResourceName
                        design.showDownload(
                            "正在下载外部资源  $resourceName",
                            status.progress,
                            status.max
                        )
                    }

                    FetchStatus.Action.FetchProviders -> {
                        val providerName = status.args.firstOrNull() ?: "提供者"
                        design.updateDownload(
                            "正在下载外部资源  $providerName",
                            status.progress,
                            status.max
                        )
                    }

                    FetchStatus.Action.Verifying -> {
                        // 验证阶段不显示进度
                    }
                }
            }
        }
    }

    private suspend fun showDownloadComplete(design: NewProfileDesign) {
        withContext(Dispatchers.Main) {
            design.updateDownload("下载完成", 100, 100)
            kotlinx.coroutines.delay(DOWNLOAD_COMPLETE_DELAY)
            design.hideDownload()
        }
    }

    override suspend fun main() {
        val design = NewProfileDesign(this)

        // 检查是否是编辑模式
        val profileUuid = intent.uuid
        if (profileUuid != null) {
            withProfile {
                editingProfile = queryByUUID(profileUuid)
            }

            editingProfile?.let { profile ->
                isEditMode = true
                design.isEditMode = true  // 设置 design 的编辑模式标志
                pendingProfileUuid = profile.uuid

                // 根据配置类型预设界面
                when (profile.type) {
                    Profile.Type.File -> {
                        design.providerIndex = 0 // File provider
                        design.fileName = profile.name
                        loadProfileFiles(profile.uuid, design)
                    }

                    Profile.Type.Url -> {
                        design.providerIndex = 1 // URL provider
                        design.urlName = profile.name
                        design.url = profile.source
                        design.urlHoursIndex = when (profile.interval) {
                            0L -> 0
                            1L -> 1
                            3L -> 2
                            6L -> 3
                            12L -> 4
                            24L -> 5
                            else -> 0
                        }
                    }

                    Profile.Type.External -> withProfile {
                        // External类型，找到对应的provider
                        val providers = queryProfileProviders()
                        design.patchProviders(providers)
                        val extIndex = providers.indexOfFirst {
                            it is ProfileProvider.External && it.intent.component?.packageName == profile.source
                        }
                        if (extIndex >= 0) {
                            design.providerIndex = extIndex
                        }
                    }
                }
            }
        }

        if (!isEditMode) {
            design.patchProviders(queryProfileProviders())
        } else {
            design.patchProviders(queryProfileProviders())
        }

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // Handle events
                }
                design.requests.onReceive {
                    when (it) {
                        is NewProfileDesign.Request.CreateEmptyFile -> {
                            // 空请求，只是切换到文件模式
                        }

                        is NewProfileDesign.Request.CreateEmptyUrl -> {
                            // 空请求，只是切换到URL模式
                        }

                        is NewProfileDesign.Request.ExternalOpen -> {
                            launchAppDetailed(it.provider)
                        }

                        is NewProfileDesign.Request.ImportFile -> {
                            filePickerLauncher.launch("*/*")
                        }

                        is NewProfileDesign.Request.SaveFile -> {
                            handleSaveFile(it.name, design)
                        }

                        is NewProfileDesign.Request.SaveUrl -> {
                            handleSaveUrl(it.name, it.url, it.intervalHours, design)
                        }

                        is NewProfileDesign.Request.Cancel,
                        is NewProfileDesign.Request.PopStack -> {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleSaveFile(name: String, design: NewProfileDesign) {
        if (name.isBlank()) {
            showToast("请输入配置名称")
            return
        }

        val files = design.currentFiles()
        if (files.isEmpty()) {
            showToast("请先导入配置文件")
            return
        }

        // 防止重复保存
        if (design.isSaving) {
            return
        }

        design.isSaving = true

        // 显示初始进度
        withContext(Dispatchers.Main) {
            design.showDownload("正在保存配置...", 0, 100)
        }

        withProfile {
            try {
                val uuid = if (isEditMode && editingProfile != null) {
                    // 编辑模式：更新现有配置
                    val profile = editingProfile!!

                    // 需要重新commit的情况：
                    // 1. 导入了新文件
                    // 2. 配置处于pending状态（未完成验证）
                    val needsCommit = isFileImported || profile.pending

                    if (needsCommit) {
                        // 先用正确的 source 更新配置
                        patch(profile.uuid, name, "file://local", profile.interval)

                        // 显示进度并commit
                        val fetchObserver = createDownloadObserver(design, "配置文件")
                        withContext(Dispatchers.IO) {
                            commit(profile.uuid, fetchObserver)
                        }
                    } else {
                        // 配置已完成验证，只更新元数据
                        patch(profile.uuid, name, profile.source, profile.interval)
                    }
                    profile.uuid
                } else {
                    // 新建模式
                    if (pendingProfileUuid == null) {
                        showToast("未找到待保存的配置")
                        withContext(Dispatchers.Main) {
                            design.hideDownload()
                            design.isSaving = false
                        }
                        return@withProfile
                    }

                    val uuid = pendingProfileUuid!!
                    patch(uuid, name, "file://local", 0L)

                    // 显示进度
                    val fetchObserver = createDownloadObserver(design, "配置文件")
                    withContext(Dispatchers.IO) {
                        commit(uuid, fetchObserver)
                    }
                    uuid
                }

                // 显示完成
                showDownloadComplete(design)

                // 重置状态
                isFileImported = false
                pendingProfileUuid = null

                showToast("配置保存成功")
                finish()
            } catch (e: Exception) {
                android.util.Log.e("NewProfileActivity", "保存配置失败", e)
                withContext(Dispatchers.Main) {
                    design.hideDownload()
                    design.isSaving = false
                }
                showToast("保存配置失败: ${e.message}")
            }
        }
    }

    private suspend fun handleSaveUrl(name: String, url: String, intervalHours: Long, design: NewProfileDesign) {
        if (name.isBlank()) {
            showToast("请输入配置名称")
            return
        }
        if (url.isBlank()) {
            showToast("请输入订阅链接")
            return
        }

        // 防止重复保存
        if (design.isSaving) {
            return
        }

        design.isSaving = true

        // 显示初始进度
        withContext(Dispatchers.Main) {
            design.showDownload("正在保存配置...", 0, 100)
        }

        withProfile {
            try {
                val uuid = if (isEditMode && editingProfile != null) {
                    // 编辑模式
                    val profile = editingProfile!!
                    patch(profile.uuid, name, url, intervalHours)

                    // 如果是pending状态或者URL改变了，需要重新commit
                    if (profile.pending || profile.source != url) {
                        val fetchObserver = createDownloadObserver(design, url)
                        withContext(Dispatchers.IO) {
                            commit(profile.uuid, fetchObserver)
                        }
                        showDownloadComplete(design)
                    } else {
                        // 仅更新了元数据，显示完成
                        showDownloadComplete(design)
                    }
                    profile.uuid
                } else {
                    // 新建模式
                    val createdUuid = create(Profile.Type.Url, name, url)
                    patch(createdUuid, name, url, intervalHours)

                    commit(createdUuid, createDownloadObserver(design, url))
                    showDownloadComplete(design)
                    createdUuid
                }

                showToast("配置保存成功")
                finish()
            } catch (e: Exception) {
                android.util.Log.e("NewProfileActivity", "保存配置失败", e)
                withContext(Dispatchers.Main) {
                    design.hideDownload()
                    design.isSaving = false
                }
                showToast("保存配置失败: ${e.message}")
            }
        }
    }

    private fun launchAppDetailed(provider: ProfileProvider.External) {
        val data = Uri.fromParts(
            "package",
            provider.intent.component?.packageName ?: return,
            null
        )
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(data))
    }

    private suspend fun handleFileSelection(uri: Uri) {
        val design = design ?: return

        android.util.Log.d("NewProfileActivity", "Selected URI: $uri")

        val fileName = withContext(Dispatchers.IO) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            cursor.getString(nameIndex)
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                android.util.Log.w("NewProfileActivity", "Failed to get display name", e)
                null
            }
        } ?: "配置文件"

        withProfile {
            try {
                val uuid = if (isEditMode && editingProfile != null) {
                    // 编辑模式：删除旧文件，强制重新创建 pending 记录
                    val profile = editingProfile!!
                    val importedDir =
                        this@NewProfileActivity.filesDir.resolve("imported").resolve(profile.uuid.toString())
                    if (importedDir.exists()) {
                        android.util.Log.d("NewProfileActivity", "删除旧的imported目录")
                        importedDir.deleteRecursively()
                    }

                    // 通过修改 source 触发 patch 创建 Pending 记录
                    // 使用一个临时的不同 source，然后再改回来
                    val tempSource = "file://local?t=${System.currentTimeMillis()}"
                    patch(profile.uuid, profile.name, tempSource, profile.interval)

                    profile.uuid
                } else {
                    // 新建模式：创建新profile
                    val profileName = if (design.fileName.isBlank()) fileName else design.fileName
                    create(Profile.Type.File, profileName, "file://local")
                }

                pendingProfileUuid = uuid

                // 清理并创建pending目录
                val pendingDir = this@NewProfileActivity.filesDir.resolve("pending").resolve(uuid.toString())
                if (pendingDir.exists()) {
                    pendingDir.deleteRecursively()
                }
                pendingDir.mkdirs()

                val configFile = pendingDir.resolve("config.yaml")

                // 复制文件
                var totalBytes = 0L
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    configFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }

                        if (totalBytes == 0L) {
                            throw Exception("文件为空")
                        }
                    }
                } ?: throw Exception("无法读取文件")

                isFileImported = true

                // 更新UI
                withContext(Dispatchers.Main) {
                    if (design.fileName.isBlank()) {
                        design.fileName = fileName
                    }
                }

                loadProfileFiles(uuid, design)

                showToast(if (isEditMode) "已导入新配置文件：$fileName" else "已导入配置文件：$fileName")

            } catch (e: Exception) {
                android.util.Log.e("NewProfileActivity", "导入文件失败", e)
                showToast("导入文件失败: ${e.message}")
                pendingProfileUuid = null
                isFileImported = false
                design.swapFiles(emptyList())
            }
        }
    }

    private suspend fun loadProfileFiles(uuid: UUID, design: NewProfileDesign) {
        withContext(Dispatchers.IO) {
            val pendingDir = filesDir.resolve("pending").resolve(uuid.toString())
            val importedDir = filesDir.resolve("imported").resolve(uuid.toString())

            android.util.Log.d("NewProfileActivity", "loadProfileFiles: uuid=$uuid")
            android.util.Log.d("NewProfileActivity", "  pending dir exists: ${pendingDir.exists()}")
            android.util.Log.d("NewProfileActivity", "  imported dir exists: ${importedDir.exists()}")

            val profileDir = if (pendingDir.exists()) {
                android.util.Log.d("NewProfileActivity", "  using pending dir")
                pendingDir
            } else {
                android.util.Log.d("NewProfileActivity", "  using imported dir")
                importedDir
            }

            if (!profileDir.exists()) {
                android.util.Log.w("NewProfileActivity", "  profile dir does not exist!")
                design.swapFiles(emptyList())
                return@withContext
            }

            android.util.Log.d("NewProfileActivity", "  profile dir: ${profileDir.absolutePath}")
            android.util.Log.d("NewProfileActivity", "  files in dir: ${profileDir.listFiles()?.map { it.name }}")

            val files = mutableListOf<File>()

            // 添加配置文件
            val configFiles = listOf("config.yaml", "config.json")
            configFiles.forEach { fileName ->
                val file = profileDir.resolve(fileName)
                if (file.exists()) {
                    android.util.Log.d("NewProfileActivity", "  found config file: $fileName")
                    files.add(
                        File(
                            id = file.absolutePath,
                            name = fileName,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            isDirectory = false
                        )
                    )
                }
            }

            // 添加proxy-providers
            val proxyProvidersFile = profileDir.resolve("proxy-providers.json")
            if (proxyProvidersFile.exists()) {
                android.util.Log.d("NewProfileActivity", "  found proxy-providers.json")
                files.add(
                    File(
                        id = proxyProvidersFile.absolutePath,
                        name = "proxy-providers.json",
                        size = proxyProvidersFile.length(),
                        lastModified = proxyProvidersFile.lastModified(),
                        isDirectory = false
                    )
                )
            }

            android.util.Log.d("NewProfileActivity", "  total files found: ${files.size}")
            design.swapFiles(files)
        }
    }

    private suspend fun queryProfileProviders(): List<ProfileProvider> {
        return withContext(Dispatchers.IO) {
            val providers = packageManager.queryIntentActivities(
                Intent(Intents.ACTION_PROVIDE_URL),
                0
            ).map {
                val activity = it.activityInfo
                val name = activity.applicationInfo.loadLabel(packageManager)
                val summary = activity.loadLabel(packageManager)
                val icon = activity.loadIcon(packageManager)
                val intent = Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(
                        ComponentName(
                            activity.packageName,
                            activity.name
                        )
                    )

                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
            }

            listOf(ProfileProvider.File(self), ProfileProvider.Url(self)) + providers
        }
    }
}
