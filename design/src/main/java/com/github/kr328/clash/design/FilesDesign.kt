package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import androidx.compose.runtime.*
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.util.ValidatorsUnified

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

    private val filesState = mutableStateListOf<File>()
    private var inBaseDirState by mutableStateOf(true)
    private var editableState by mutableStateOf(true)

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.FilesScreen(this)
    }

    var configurationEditable: Boolean
        get() = editableState
        set(value) {
            editableState = value
        }

    suspend fun swapFiles(files: List<File>, currentInBaseDir: Boolean) {
        filesState.clear(); filesState.addAll(files)
        inBaseDirState = currentInBaseDir
    }

    fun currentFiles(): List<File> = filesState
    fun inBaseDir(): Boolean = inBaseDirState
    fun editable(): Boolean = editableState

    suspend fun requestFileName(name: String): String =
        context.requestModelTextInput(
            initial = name,
            title = context.getText(R.string.file_name),
            hint = context.getText(R.string.file_name),
            error = context.getText(R.string.invalid_file_name),
            validator = ValidatorsUnified::fileNameLegacy,
        )

    private fun open(file: File) {
        if (file.isDirectory) requests.trySend(Request.OpenDirectory(file)) else requests.trySend(Request.OpenFile(file))
    }

    fun requestImport(dialog: Dialog, file: File) {
        requests.trySend(Request.ImportFile(file))
        dialog.dismiss()
    }
    fun requestExport(dialog: Dialog, file: File) {
        requests.trySend(Request.ExportFile(file))
        dialog.dismiss()
    }
    fun requestRename(dialog: Dialog, file: File) {
        requests.trySend(Request.RenameFile(file))
        dialog.dismiss()
    }
    fun requestDelete(dialog: Dialog, file: File) {
        requests.trySend(Request.DeleteFile(file))
        dialog.dismiss()
    }
    fun requestNew() {
        requests.trySend(Request.ImportFile(null))
    }
}
