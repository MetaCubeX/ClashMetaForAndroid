package com.github.kr328.clash.service.util

import android.content.Context
import java.util.UUID

object ProfileParserHelper {
    private const val PARSER_URL_FILE = "parser.url"

    fun load(context: Context, uuid: UUID): String? {
        val pendingProfileDir = context.pendingDir.resolve(uuid.toString())
        val targetDir = if (pendingProfileDir.exists()) {
            pendingProfileDir
        } else {
            context.importedDir.resolve(uuid.toString())
        }

        val file = targetDir.resolve(PARSER_URL_FILE)
        if (!file.isFile) {
            return null
        }

        return file.readText().trim().takeIf { it.isNotEmpty() }
    }

    fun savePending(context: Context, uuid: UUID, parserUrl: String?) {
        val dir = context.pendingDir.resolve(uuid.toString())
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val normalized = parserUrl?.trim().orEmpty()
        dir.resolve(PARSER_URL_FILE).writeText(normalized)
    }
}

