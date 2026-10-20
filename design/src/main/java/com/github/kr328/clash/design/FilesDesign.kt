package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.FilesScreen
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.ValidatorFileName
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesDesign(context: Context) : Design<FilesDesign.Request>(context) {
    sealed class Request {
        data class OpenFile(val file: File) : Request()
        data class OpenDirectory(val file: File) : Request()
        data class RenameFile(val file: File) : Request()
        data class DeleteFile(val file: File) : Request()
        data class ImportFile(val file: File?) : Request()
        data class ExportFile(val file: File) : Request()

        object PopStack : Request()
    }

    private var insets by mutableStateOf(Insets.EMPTY)
    private var files by mutableStateOf<List<File>>(emptyList())
    private var currentInBaseDir by mutableStateOf(true)
    private var now by mutableStateOf(System.currentTimeMillis())

    var configurationEditable by mutableStateOf(false)

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ClashMetaXTheme {
                FilesScreen(
                    insets = insets,
                    title = context.getString(R.string.files),
                    files = files,
                    currentInBaseDir = currentInBaseDir,
                    configurationEditable = configurationEditable,
                    now = now,
                    onBack = { (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed() },
                    onNew = { requestNew() },
                    onOpen = { requestOpen(it) },
                    onImport = { requestImport(it) },
                    onExport = { requestExport(it) },
                    onRename = { requestRename(it) },
                    onDelete = { requestDelete(it) },
                )
            }
        }
    }

    suspend fun swapFiles(next: List<File>, inBase: Boolean) {
        withContext(Dispatchers.Main) {
            files = next
            currentInBaseDir = inBase
        }
    }

    fun showAtRoot() {
        currentInBaseDir = true
    }

    fun updateElapsed() {
        now = System.currentTimeMillis()
    }

    suspend fun requestFileName(name: String): String {
        return context.requestModelTextInput(
            initial = name,
            title = context.getText(R.string.file_name),
            hint = context.getText(R.string.file_name),
            error = context.getText(R.string.invalid_file_name),
            validator = ValidatorFileName,
        )
    }

    fun requestRename(dialog: Dialog, file: File) {
        dialog.dismiss()
        requestRename(file)
    }

    fun requestImport(dialog: Dialog, file: File) {
        dialog.dismiss()
        requestImport(file)
    }

    fun requestExport(dialog: Dialog, file: File) {
        dialog.dismiss()
        requestExport(file)
    }

    fun requestDelete(dialog: Dialog, file: File) {
        dialog.dismiss()
        requestDelete(file)
    }

    fun requestNew() {
        // Root stack is empty. ImportFile(null) calls stack.last() and throws.
        if (currentInBaseDir) return
        requests.trySend(Request.ImportFile(null))
    }

    private fun requestOpen(file: File) {
        if (file.isDirectory) {
            requests.trySend(Request.OpenDirectory(file))
        } else {
            requests.trySend(Request.OpenFile(file))
        }
    }

    private fun requestRename(file: File) {
        requests.trySend(Request.RenameFile(file))
    }

    private fun requestImport(file: File) {
        confirm(context.getString(R.string.import_), file.name, destructive = true) {
            requests.trySend(Request.ImportFile(file))
        }
    }

    private fun requestExport(file: File) {
        requests.trySend(Request.ExportFile(file))
    }

    private fun requestDelete(file: File) {
        confirm(context.getString(R.string.delete), file.name, destructive = true) {
            requests.trySend(Request.DeleteFile(file))
        }
    }

    private fun confirm(title: String, message: String, destructive: Boolean, block: () -> Unit) {
        launch {
            val accepted = withContext(Dispatchers.Main) {
                iosConfirm(
                    context,
                    title,
                    message,
                    context.getString(R.string.ok),
                    destructive,
                )
            }
            if (accepted) block()
        }
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
