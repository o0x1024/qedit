package com.qedit.app.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.qedit.app.model.FileType
import com.qedit.app.model.SearchMatch
import com.qedit.app.model.SyntaxDiagnostic
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.MarkedYAMLException

data class SyntaxColorScheme(
    val comment: Color,
    val key: Color,
    val stringLiteral: Color,
    val numberLiteral: Color,
    val keyword: Color,
    val punctuation: Color,
    val heading: Color,
    val searchHighlightBg: Color,
    val activeSearchBg: Color
)

object SyntaxPalette {
    val Dark = SyntaxColorScheme(
        comment = Color(0xFF64748B),
        key = Color(0xFF38BDF8),
        stringLiteral = Color(0xFF4ADE80),
        numberLiteral = Color(0xFFFBBF24),
        keyword = Color(0xFFC084FC),
        punctuation = Color(0xFF94A3B8),
        heading = Color(0xFF38BDF8),
        searchHighlightBg = Color(0x66F59E0B),
        activeSearchBg = Color(0xCCF59E0B)
    )

    val Light = SyntaxColorScheme(
        comment = Color(0xFF64748B),
        key = Color(0xFF0369A1),
        stringLiteral = Color(0xFF15803D),
        numberLiteral = Color(0xFFB45309),
        keyword = Color(0xFF7E22CE),
        punctuation = Color(0xFF475569),
        heading = Color(0xFF0369A1),
        searchHighlightBg = Color(0x4DF59E0B),
        activeSearchBg = Color(0x99F59E0B)
    )
}

class EditorVisualTransformation(
    private val fileType: FileType,
    private val searchMatches: List<SearchMatch>,
    private val activeMatchIndex: Int,
    private val isDarkTheme: Boolean = true
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxEngine.highlight(
            raw = text.text,
            fileType = fileType,
            searchMatches = searchMatches,
            activeMatchIndex = activeMatchIndex,
            colors = if (isDarkTheme) SyntaxPalette.Dark else SyntaxPalette.Light
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

object SyntaxEngine {

    private val yamlCommentRegex = Regex("""(^|\s)#.*$""")
    private val yamlKeyRegex = Regex("""^(\s*(?:-\s+)?)([^#:\n][^:\n]*?)(\s*:)""")
    private val yamlListDashRegex = Regex("""^(\s*)(-)(\s+)""")
    private val stringRegex = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'""")
    private val numberRegex = Regex("""\b-?\d+(\.\d+)?([eE][+-]?\d+)?\b""")
    private val yamlKeywordRegex = Regex("""\b(true|false|null|yes|no|on|off)\b""", RegexOption.IGNORE_CASE)
    private val jsonKeyRegex = Regex(""""([^"\\]|\\.)*"(?=\s*:)""")
    private val jsonKeywordRegex = Regex("""\b(true|false|null)\b""")
    private val mdHeadingRegex = Regex("""^#{1,6}\s+.*$""")
    private val iniSectionRegex = Regex("""^\s*\[[^\]]+\]\s*$""")
    private val iniKeyRegex = Regex("""^(\s*)([^#;\s=][^=]*)(\s*=)""")

    fun highlight(
        raw: String,
        fileType: FileType,
        searchMatches: List<SearchMatch> = emptyList(),
        activeMatchIndex: Int = -1,
        colors: SyntaxColorScheme = SyntaxPalette.Dark
    ): AnnotatedString {
        return buildAnnotatedString {
            append(raw)
            // Keep highlighting fast on large files by capping full-token styling above 200k chars.
            if (raw.length <= 200_000) {
                when (fileType) {
                    FileType.YAML -> highlightYaml(raw, this, colors)
                    FileType.JSON -> highlightJson(raw, this, colors)
                    FileType.MARKDOWN -> highlightMarkdown(raw, this, colors)
                    FileType.CONFIG -> highlightConfig(raw, this, colors)
                    FileType.PLAIN -> Unit
                }
            }
            searchMatches.forEachIndexed { idx, match ->
                if (match.start >= 0 && match.end <= raw.length && match.start < match.end) {
                    addStyle(
                        SpanStyle(
                            background = if (idx == activeMatchIndex) {
                                colors.activeSearchBg
                            } else {
                                colors.searchHighlightBg
                            }
                        ),
                        match.start,
                        match.end
                    )
                }
            }
        }
    }

    private fun highlightYaml(raw: String, builder: AnnotatedString.Builder, colors: SyntaxColorScheme) {
        var lineStart = 0
        while (lineStart <= raw.length) {
            val lineEnd = raw.indexOf('\n', lineStart).let { if (it == -1) raw.length else it }
            val line = raw.substring(lineStart, lineEnd)

            if (line == "---" || line == "...") {
                builder.addStyle(SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold), lineStart, lineEnd)
            } else {
                yamlListDashRegex.find(line)?.let { match ->
                    val dashStart = lineStart + match.groups[2]!!.range.first
                    val dashEnd = lineStart + match.groups[2]!!.range.last + 1
                    builder.addStyle(SpanStyle(color = colors.punctuation, fontWeight = FontWeight.Bold), dashStart, dashEnd)
                }

                val commentMatch = yamlCommentRegex.find(line)
                val codeLimit = commentMatch?.range?.first ?: line.length
                val codePart = line.substring(0, codeLimit)

                yamlKeyRegex.find(codePart)?.let { match ->
                    val keyGroup = match.groups[2]!!
                    builder.addStyle(
                        SpanStyle(color = colors.key, fontWeight = FontWeight.Medium),
                        lineStart + keyGroup.range.first,
                        lineStart + keyGroup.range.last + 1
                    )
                    val colonGroup = match.groups[3]!!
                    builder.addStyle(
                        SpanStyle(color = colors.punctuation),
                        lineStart + colonGroup.range.first,
                        lineStart + colonGroup.range.last + 1
                    )
                }

                stringRegex.findAll(codePart).forEach { m ->
                    builder.addStyle(
                        SpanStyle(color = colors.stringLiteral),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                yamlKeywordRegex.findAll(codePart).forEach { m ->
                    builder.addStyle(
                        SpanStyle(color = colors.keyword),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                numberRegex.findAll(codePart).forEach { m ->
                    builder.addStyle(
                        SpanStyle(color = colors.numberLiteral),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                commentMatch?.let { m ->
                    val cStart = lineStart + m.range.first
                    builder.addStyle(SpanStyle(color = colors.comment), cStart, lineEnd)
                }
            }

            if (lineEnd == raw.length) break
            lineStart = lineEnd + 1
        }
    }

    private fun highlightJson(raw: String, builder: AnnotatedString.Builder, colors: SyntaxColorScheme) {
        stringRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = colors.stringLiteral), m.range.first, m.range.last + 1)
        }
        jsonKeyRegex.findAll(raw).forEach { m ->
            builder.addStyle(
                SpanStyle(color = colors.key, fontWeight = FontWeight.Medium),
                m.range.first,
                m.range.last + 1
            )
        }
        jsonKeywordRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = colors.keyword), m.range.first, m.range.last + 1)
        }
        numberRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = colors.numberLiteral), m.range.first, m.range.last + 1)
        }
    }

    private fun highlightMarkdown(raw: String, builder: AnnotatedString.Builder, colors: SyntaxColorScheme) {
        var lineStart = 0
        while (lineStart <= raw.length) {
            val lineEnd = raw.indexOf('\n', lineStart).let { if (it == -1) raw.length else it }
            val line = raw.substring(lineStart, lineEnd)
            if (mdHeadingRegex.matches(line)) {
                builder.addStyle(
                    SpanStyle(color = colors.heading, fontWeight = FontWeight.Bold),
                    lineStart,
                    lineEnd
                )
            }
            if (lineEnd == raw.length) break
            lineStart = lineEnd + 1
        }
    }

    private fun highlightConfig(raw: String, builder: AnnotatedString.Builder, colors: SyntaxColorScheme) {
        var lineStart = 0
        while (lineStart <= raw.length) {
            val lineEnd = raw.indexOf('\n', lineStart).let { if (it == -1) raw.length else it }
            val line = raw.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
                builder.addStyle(SpanStyle(color = colors.comment), lineStart, lineEnd)
            } else if (iniSectionRegex.matches(line)) {
                builder.addStyle(
                    SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold),
                    lineStart,
                    lineEnd
                )
            } else {
                iniKeyRegex.find(line)?.let { match ->
                    val k = match.groups[2]!!
                    builder.addStyle(
                        SpanStyle(color = colors.key),
                        lineStart + k.range.first,
                        lineStart + k.range.last + 1
                    )
                }
                stringRegex.findAll(line).forEach { m ->
                    builder.addStyle(
                        SpanStyle(color = colors.stringLiteral),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }
            }
            if (lineEnd == raw.length) break
            lineStart = lineEnd + 1
        }
    }

    fun validate(text: String, fileType: FileType): SyntaxDiagnostic? {
        if (text.isBlank()) return null
        return when (fileType) {
            FileType.YAML -> validateYaml(text)
            FileType.JSON -> validateJson(text)
            else -> null
        }
    }

    private fun validateYaml(text: String): SyntaxDiagnostic? {
        return try {
            val yaml = Yaml()
            for (doc in yaml.loadAll(text)) {
                // Exhaust iterator to validate multi-document YAML streams.
            }
            SyntaxDiagnostic(line = 1, column = 1, message = "Valid YAML", isError = false)
        } catch (e: MarkedYAMLException) {
            val mark = e.problemMark
            SyntaxDiagnostic(
                line = (mark?.line ?: 0) + 1,
                column = (mark?.column ?: 0) + 1,
                message = e.problem ?: e.message ?: "Invalid YAML",
                isError = true
            )
        } catch (e: Exception) {
            SyntaxDiagnostic(
                line = 1,
                column = 1,
                message = e.message ?: "Invalid YAML",
                isError = true
            )
        }
    }

    private fun validateJson(text: String): SyntaxDiagnostic? {
        val trimmed = text.trim()
        return try {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed)
            }
            SyntaxDiagnostic(line = 1, column = 1, message = "Valid JSON", isError = false)
        } catch (e: JSONException) {
            SyntaxDiagnostic(
                line = 1,
                column = 1,
                message = e.message ?: "Invalid JSON",
                isError = true
            )
        }
    }
}
