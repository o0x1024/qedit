package com.qedit.app.model

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID

enum class LineEnding(val label: String, val separator: String) {
    LF("LF", "\n"),
    CRLF("CRLF", "\r\n");

    companion object {
        fun detect(rawText: String): LineEnding {
            return if (rawText.contains("\r\n")) CRLF else LF
        }
    }
}

enum class FileType(val label: String, val defaultIndentSize: Int) {
    PLAIN("TXT", 4),
    YAML("YAML", 2),
    JSON("JSON", 2),
    MARKDOWN("MD", 2),
    CONFIG("CONF", 2);

    companion object {
        fun fromFileName(fileName: String): FileType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "yaml", "yml" -> YAML
                "json", "jsonc" -> JSON
                "md", "markdown" -> MARKDOWN
                "ini", "conf", "cfg", "toml", "properties", "env", "sh", "zsh" -> CONFIG
                else -> PLAIN
            }
        }
    }
}

data class SyntaxDiagnostic(
    val line: Int,
    val column: Int,
    val message: String,
    val isError: Boolean
)

data class DocumentTab(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val displayName: String = "untitled.txt",
    val fieldValue: TextFieldValue = TextFieldValue(""),
    val savedContent: String = "",
    val lineEnding: LineEnding = LineEnding.LF,
    val fileType: FileType = FileType.PLAIN,
    val diagnostic: SyntaxDiagnostic? = null
) {
    val isDirty: Boolean
        get() = fieldValue.text != savedContent

    fun cursorLineAndColumn(): Pair<Int, Int> {
        val text = fieldValue.text
        val offset = fieldValue.selection.start.coerceIn(0, text.length)
        var line = 1
        var lastNewline = -1
        for (i in 0 until offset) {
            if (text[i] == '\n') {
                line++
                lastNewline = i
            }
        }
        val column = offset - lastNewline
        return line to column
    }
}

data class RecentFileEntry(
    val uriString: String,
    val displayName: String,
    val lastOpenedMillis: Long
)

data class FolderEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

data class SearchMatch(
    val start: Int,
    val end: Int
) {
    val range: TextRange get() = TextRange(start, end)
}
