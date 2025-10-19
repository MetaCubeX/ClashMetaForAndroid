package com.github.kr328.clash

// TODO: Legacy screen slated for removal after NewProfile flow migration.

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.grantPermissions
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.FilesDesign
import com.github.kr328.clash.design.theme.YumeTheme
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.fileName
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import com.github.kr328.clash.design.model.File as CfaFile

class FilesActivity : ComponentActivity() {
    private val scope = MainScope()

    private lateinit var getContentLauncher: ActivityResultLauncher<String>
    private lateinit var createDocLauncher: ActivityResultLauncher<String>
    private lateinit var startActivityLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uuid = intent.uuid ?: return finish()

        // Pre-register launchers to avoid late registration crash
        getContentLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { pendingContinuationGetContent?.resume(it) }
        createDocLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
            pendingContinuationCreateDoc?.resume(it)
        }
        startActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingContinuationStart?.resume(Unit)
        }

        val design = FilesDesign(this)
        setContent {
            YumeTheme {
                design.Content()
            }
        }

        val client = FilesClient(this)
        val stack = Stack<String>()
        var root = ""

        scope.launch {
            val profile = withProfile { queryByUUID(uuid) } ?: return@launch finish()
            design.configurationEditable = profile.type != Profile.Type.Url
            root = uuid.toString()
            fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
        }

        scope.launch {
            for (req in design.requests) when (req) {
                is FilesDesign.Request.PopStack -> {
                    if (stack.isNotEmpty()) {
                        stack.pop()
                        fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
                    } else finish()
                }

                is FilesDesign.Request.OpenDirectory -> {
                    stack.push(req.file.id)
                    fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
                }

                is FilesDesign.Request.OpenFile -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(client.buildDocumentUri(req.file.id), "text/plain")
                        .grantPermissions()
                    awaitStartActivity(intent)
                }

                is FilesDesign.Request.RenameFile -> {
                    val newName = design.requestFileName(req.file.name)
                    if (newName.isNotBlank()) runCatching { client.renameDocument(req.file.id, newName) }
                    fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
                }

                is FilesDesign.Request.DeleteFile -> {
                    runCatching { client.deleteDocument(req.file.id) }
                    fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
                }

                is FilesDesign.Request.ImportFile -> {
                    val uri: Uri? = awaitGetContent("*/*")
                    if (uri != null) {
                        val f = req.file
                        val atBase = stack.empty()

                        if (atBase) {
                            when {
                                // 在根目录且未选中文件：导入为 config.yaml（覆盖）
                                f == null -> client.copyDocument("${root}/config.yaml", uri)
                                // 在根目录且选中目录：向该目录下导入新文件
                                f.isDirectory -> {
                                    val name = design.requestFileName(uri.fileName ?: "File")
                                    client.importDocument(f.id, uri, name)
                                }
                                // 在根目录且选中文件：覆盖该文件
                                else -> client.copyDocument(f.id, uri)
                            }
                        } else {
                            // 非根目录：目录下新建/覆盖选中文件
                            val parentOrTargetId = if (f == null || f.isDirectory) {
                                f?.id ?: (stack.lastOrNull() ?: root)
                            } else null

                            if (parentOrTargetId != null) {
                                val name = design.requestFileName(uri.fileName ?: "File")
                                client.importDocument(parentOrTargetId, uri, name)
                            } else {
                                client.copyDocument(f!!.id, uri)
                            }
                        }

                        fetch(client, stack, root) { files -> scope.launch { design.swapFiles(files, stack.empty()) } }
                    }
                }

                is FilesDesign.Request.ExportFile -> {
                    val out: Uri? = awaitCreateDocument("text/plain", req.file.name)
                    if (out != null) runCatching { client.copyDocument(out, req.file.id) }
                }
            }
        }
    }

    private suspend fun fetch(
        client: FilesClient,
        stack: Stack<String>,
        root: String,
        sink: (List<CfaFile>) -> Unit
    ) {
        val documentId = stack.lastOrNull() ?: root
        val files = withContext(Dispatchers.IO) {
            if (stack.empty()) {
                val list = client.list(documentId)
                val config = list.firstOrNull { it.id.endsWith("config.yaml") }
                if (config == null || config.size > 0) list else listOf(config)
            } else {
                client.list(documentId)
            }
        }
        sink(files)
    }

    private var pendingContinuationGetContent: kotlin.coroutines.Continuation<Uri?>? = null
    private var pendingContinuationCreateDoc: kotlin.coroutines.Continuation<Uri?>? = null
    private var pendingContinuationStart: kotlin.coroutines.Continuation<Unit>? = null

    private suspend fun awaitGetContent(mime: String): Uri? = suspendCancellableCoroutineCompat { cont ->
        pendingContinuationGetContent = cont
        getContentLauncher.launch(mime)
    }

    private suspend fun awaitCreateDocument(mime: String, name: String): Uri? =
        suspendCancellableCoroutineCompat { cont ->
            pendingContinuationCreateDoc = cont
            createDocLauncher.launch(name)
        }

    private suspend fun awaitStartActivity(intent: Intent) = suspendCancellableCoroutineCompat<Unit> { cont ->
        pendingContinuationStart = cont
        startActivityLauncher.launch(intent)
    }

    private suspend fun <T> suspendCancellableCoroutineCompat(block: (kotlin.coroutines.Continuation<T>) -> Unit): T =
        kotlinx.coroutines.suspendCancellableCoroutine { c ->
            block(c)
            c.invokeOnCancellation {
                if (pendingContinuationGetContent == c) pendingContinuationGetContent = null
                if (pendingContinuationCreateDoc == c) pendingContinuationCreateDoc = null
                if (pendingContinuationStart == c) pendingContinuationStart = null
            }
        }

    // Very simple placeholder for input; you can hook up a dialog if needed.
    private suspend fun requestFileName(default: String): String = default

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}