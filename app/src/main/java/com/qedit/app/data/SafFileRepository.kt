package com.qedit.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.qedit.app.model.FolderEntry
import com.qedit.app.model.LineEnding
import com.qedit.app.model.RecentFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class LoadedDocument(
    val uri: Uri,
    val displayName: String,
    val normalizedText: String,
    val lineEnding: LineEnding
)

class SafFileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("qedit_prefs", Context.MODE_PRIVATE)

    suspend fun readDocument(uri: Uri): Result<LoadedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: error("Unable to open input stream for $uri")

            val rawText = String(bytes, StandardCharsets.UTF_8)
            val lineEnding = LineEnding.detect(rawText)
            val normalized = if (lineEnding == LineEnding.CRLF) {
                rawText.replace("\r\n", "\n")
            } else {
                rawText
            }
            LoadedDocument(
                uri = uri,
                displayName = displayName,
                normalizedText = normalized,
                lineEnding = lineEnding
            )
        }
    }

    suspend fun writeDocument(
        uri: Uri,
        normalizedText: String,
        lineEnding: LineEnding
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outputText = if (lineEnding == LineEnding.CRLF) {
                normalizedText.replace("\n", "\r\n")
            } else {
                normalizedText
            }
            // "wt" truncates the target file before writing to avoid trailing bytes when shrinking a file.
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(outputText.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } ?: error("Unable to open output stream for $uri")
            Unit
        }
    }

    fun takePersistablePermission(uri: Uri, allowWrite: Boolean = true) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (allowWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some transient document providers (e.g. ACTION_VIEW from third-party apps) do not support persistable grants.
        }
    }

    fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "untitled.txt"
    }

    suspend fun listDirectory(treeUri: Uri): Result<List<FolderEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Invalid directory URI")
            root.listFiles()
                .mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    FolderEntry(
                        uri = doc.uri,
                        name = name,
                        isDirectory = doc.isDirectory
                    )
                }
                .sortedWith(compareBy<FolderEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    fun getRecentFiles(): List<RecentFileEntry> {
        val raw = prefs.getString(KEY_RECENT_FILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RecentFileEntry(
                            uriString = obj.getString("uri"),
                            displayName = obj.getString("name"),
                            lastOpenedMillis = obj.getLong("ts")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun recordRecentFile(uri: Uri, displayName: String) {
        val updated = buildList {
            add(RecentFileEntry(uri.toString(), displayName, System.currentTimeMillis()))
            addAll(getRecentFiles().filter { it.uriString != uri.toString() })
        }.take(15)

        val array = JSONArray()
        updated.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("uri", entry.uriString)
                    put("name", entry.displayName)
                    put("ts", entry.lastOpenedMillis)
                }
            )
        }
        prefs.edit().putString(KEY_RECENT_FILES, array.toString()).apply()
    }

    fun saveLastFolderUri(uri: Uri) {
        prefs.edit().putString(KEY_LAST_FOLDER, uri.toString()).apply()
    }

    fun getLastFolderUri(): Uri? {
        val raw = prefs.getString(KEY_LAST_FOLDER, null) ?: return null
        return Uri.parse(raw)
    }

    private companion object {
        const val KEY_RECENT_FILES = "recent_files_v1"
        const val KEY_LAST_FOLDER = "last_folder_uri"
    }
}
