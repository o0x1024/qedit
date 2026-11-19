package com.qedit.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.qedit.app.editor.SyntaxEngine
import com.qedit.app.editor.TextEditingActions
import com.qedit.app.model.FileType
import com.qedit.app.model.LineEnding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEngineTest {

    @Test
    fun yamlAutoIndentAfterColon() {
        val prev = TextFieldValue("services:", TextRange(9))
        val incoming = TextFieldValue("services:\n", TextRange(10))
        val result = TextEditingActions.applySmartNewlineIfTriggered(prev, incoming, FileType.YAML)
        assertEquals("services:\n  ", result.text)
        assertEquals(12, result.selection.start)
    }

    @Test
    fun indentAndOutdentSingleLine() {
        val initial = TextFieldValue("key: val", TextRange(0))
        val indented = TextEditingActions.indent(initial, 2)
        assertEquals("  key: val", indented.text)

        val outdented = TextEditingActions.outdent(indented, 2)
        assertEquals("key: val", outdented.text)
    }

    @Test
    fun wrapSelectionWithQuotes() {
        val initial = TextFieldValue("hello world", TextRange(0, 5))
        val wrapped = TextEditingActions.insertSymbol(initial, "\"")
        assertEquals("\"hello\" world", wrapped.text)
    }

    @Test
    fun validateValidAndInvalidYaml() {
        val valid = SyntaxEngine.validate("app:\n  name: QEdit\n", FileType.YAML)
        assertNotNull(valid)
        assertFalse(valid!!.isError)

        val invalid = SyntaxEngine.validate("app:\n  name: [unclosed\n", FileType.YAML)
        assertNotNull(invalid)
        assertTrue(invalid!!.isError)
    }

    @Test
    fun detectLineEnding() {
        assertEquals(LineEnding.CRLF, LineEnding.detect("a\r\nb\r\n"))
        assertEquals(LineEnding.LF, LineEnding.detect("a\nb\n"))
    }
}
