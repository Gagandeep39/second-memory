package com.secondmemory.ui.screen.record

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies cursor-aware transcript insertion used by the recording screen.
 */
class RecordThoughtScreenTest {
    @Test
    fun appendTranscript_insertsAtSelectionAndMovesCursor() {
        val result = appendTranscript(
            baseValue = TextFieldValue("Keep this note", TextRange(5, 9)),
            transcript = "that",
        )

        assertEquals("Keep that note", result.text)
        assertEquals(9, result.selection.start)
        assertEquals(result.selection.start, result.selection.end)
    }

    @Test
    fun appendTranscript_addsSpacingAroundExistingText() {
        val result = appendTranscript(
            baseValue = TextFieldValue("hello world", TextRange(5)),
            transcript = "brave",
        )

        assertEquals("hello brave world", result.text)
    }

    @Test
    fun appendTranscript_ignoresBlankTranscript() {
        val baseValue = TextFieldValue("existing", TextRange(3))

        assertEquals(baseValue, appendTranscript(baseValue, "  "))
    }
}
