package com.qedit.app.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.qedit.app.model.FileType

/**
 * Pure text-manipulation helpers for mobile plain-text & structured-config editing.
 */
object TextEditingActions {

    fun applySmartNewlineIfTriggered(
        previous: TextFieldValue,
        incoming: TextFieldValue,
        fileType: FileType
    ): TextFieldValue {
        val prevText = previous.text
        val newText = incoming.text
        if (newText.length != prevText.length + 1) return incoming

        val cursor = incoming.selection.start
        if (cursor <= 0 || cursor > newText.length || newText[cursor - 1] != '\n') {
            return incoming
        }

        // Find the line immediately before the inserted newline.
        val lineStart = newText.lastIndexOf('\n', startIndex = cursor - 2).let {
            if (it == -1) 0 else it + 1
        }
        val prevLine = newText.substring(lineStart, cursor - 1)
        val baseIndent = prevLine.takeWhile { it == ' ' || it == '\t' }

        val extraIndent = if (fileType == FileType.YAML) {
            val trimmed = prevLine.trimEnd()
            val indentUnit = " ".repeat(fileType.defaultIndentSize)
            when {
                trimmed.endsWith(":") && !trimmed.startsWith("#") -> indentUnit
                trimmed.startsWith("- ") && trimmed.length > 2 -> "  "
                else -> ""
            }
        } else {
            ""
        }

        val insertion = baseIndent + extraIndent
        if (insertion.isEmpty()) return incoming

        val updatedText = buildString(newText.length + insertion.length) {
            append(newText, 0, cursor)
            append(insertion)
            append(newText, cursor, newText.length)
        }
        val newCursor = cursor + insertion.length
        return incoming.copy(
            text = updatedText,
            selection = TextRange(newCursor, newCursor)
        )
    }

    fun indent(value: TextFieldValue, indentSize: Int): TextFieldValue {
        val spaces = " ".repeat(indentSize)
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(0, text.length)

        if (start == end) {
            val updated = text.substring(0, start) + spaces + text.substring(start)
            val pos = start + spaces.length
            return value.copy(text = updated, selection = TextRange(pos, pos))
        }

        val lineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0))
            .let { if (it == -1 || start == 0) 0 else it + 1 }
        val selectedBlock = text.substring(lineStart, end)
        val lines = selectedBlock.split('\n')
        val indentedBlock = lines.joinToString("\n") { spaces + it }
        val updated = text.substring(0, lineStart) + indentedBlock + text.substring(end)
        val addedTotal = spaces.length * lines.size
        return value.copy(
            text = updated,
            selection = TextRange(start + spaces.length, end + addedTotal)
        )
    }

    fun outdent(value: TextFieldValue, indentSize: Int): TextFieldValue {
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0))
            .let { if (it == -1 || start == 0) 0 else it + 1 }
        val blockEnd = if (start == end) {
            text.indexOf('\n', startIndex = end).let { if (it == -1) text.length else it }
        } else {
            end
        }

        val block = text.substring(lineStart, blockEnd)
        val lines = block.split('\n')
        var firstLineRemoved = 0
        var totalRemoved = 0
        val outdentedLines = lines.mapIndexed { idx, line ->
            val removed = when {
                line.startsWith("\t") -> 1
                else -> line.take(indentSize).takeWhile { it == ' ' }.length
            }
            if (idx == 0) firstLineRemoved = removed
            totalRemoved += removed
            line.drop(removed)
        }

        if (totalRemoved == 0) return value

        val updated = text.substring(0, lineStart) + outdentedLines.joinToString("\n") + text.substring(blockEnd)
        val newStart = (start - firstLineRemoved).coerceAtLeast(lineStart)
        val newEnd = (end - totalRemoved).coerceAtLeast(newStart)
        return value.copy(
            text = updated,
            selection = TextRange(newStart, newEnd)
        )
    }

    fun insertSymbol(value: TextFieldValue, symbol: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(0, text.length)

        val pairClosing = when (symbol) {
            "\"" -> "\""
            "'" -> "'"
            "{" -> "}"
            "[" -> "]"
            "(" -> ")"
            else -> null
        }

        if (start != end && pairClosing != null) {
            val selected = text.substring(start, end)
            val updated = text.substring(0, start) + symbol + selected + pairClosing + text.substring(end)
            return value.copy(
                text = updated,
                selection = TextRange(start + 1, end + 1)
            )
        }

        val updated = text.substring(0, start) + symbol + text.substring(end)
        val newPos = start + symbol.length
        return value.copy(
            text = updated,
            selection = TextRange(newPos, newPos)
        )
    }

    fun moveCursor(value: TextFieldValue, delta: Int): TextFieldValue {
        val target = (value.selection.start + delta).coerceIn(0, value.text.length)
        return value.copy(selection = TextRange(target, target))
    }
}
