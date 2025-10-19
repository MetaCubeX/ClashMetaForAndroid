package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.*
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.model.ProfileProvider

class NewProfileDesign(context: Context) : Design<NewProfileDesign.Request>(context) {
    sealed class Request {
        object CreateEmptyFile : Request()
        data class ExternalOpen(val provider: ProfileProvider.External) : Request()
        object CreateEmptyUrl : Request()
        object Cancel : Request()
        object PopStack : Request()

        data class ImportFile(val file: File?) : Request()
        data class SaveFile(val name: String) : Request()
        data class SaveUrl(val name: String, val url: String, val intervalHours: Long) : Request()
    }

    private val providers = mutableStateListOf<ProfileProvider>()
    private val filesState = mutableStateListOf<File>()

    var fileName by mutableStateOf("")
    var providerIndex by mutableStateOf(0)

    var urlName by mutableStateOf("")
    var url by mutableStateOf("")
    var urlHoursIndex by mutableStateOf(0)

    internal var downloadVisible by mutableStateOf(false)
    internal var downloadTitle by mutableStateOf("")
    internal var downloadProgress by mutableStateOf(0)
    internal var downloadMax by mutableStateOf(0)

    var isSaving by mutableStateOf(false)

    var isEditMode by mutableStateOf(false)

    fun showDownload(title: String, progress: Int, max: Int) {
        downloadTitle = title
        downloadProgress = progress.coerceAtLeast(0)
        downloadMax = max.coerceAtLeast(0)
        downloadVisible = true
    }

    fun updateDownload(title: String?, progress: Int, max: Int) {
        if (title != null && title.isNotBlank()) downloadTitle = title
        downloadProgress = progress.coerceAtLeast(0)
        downloadMax = max.coerceAtLeast(0)
        if (!downloadVisible) downloadVisible = true
    }

    fun hideDownload() {
        downloadVisible = false
        downloadTitle = ""
        downloadProgress = 0
        downloadMax = 0
    }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.NewProfileScreen(this)
    }

    suspend fun patchProviders(list: List<ProfileProvider>) {
        providers.clear()
        providers.addAll(list)
    }

    fun requestProvider(provider: ProfileProvider) {
        when (provider) {
            is ProfileProvider.File -> requests.trySend(Request.CreateEmptyFile)
            is ProfileProvider.Url -> requests.trySend(Request.CreateEmptyUrl)
            is ProfileProvider.External -> requests.trySend(Request.ExternalOpen(provider))
        }
    }

    fun currentProviders(): List<ProfileProvider> = providers

    suspend fun swapFiles(files: List<File>) {
        filesState.clear()
        filesState.addAll(files)
    }

    fun currentFiles(): List<File> = filesState
}
