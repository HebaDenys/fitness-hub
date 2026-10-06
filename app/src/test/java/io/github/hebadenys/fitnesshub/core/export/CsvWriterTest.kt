package io.github.hebadenys.fitnesshub.core.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Locale

class CsvWriterTest {

    @Test
    @DisplayName("absent values write an empty field, never zero")
    fun absentIsEmptyNotZero() {
        assertEquals("date,", CsvWriter.row(listOf("date", null)))
        assertEquals("", CsvWriter.row(listOf(null)))
        assertEquals(",", CsvWriter.row(listOf(null, null)))
        assertEquals(CsvWriter.NULL_MARKER, CsvWriter.row(listOf(CsvWriter.number(null, 2))))
        assertTrue(CsvWriter.number(0.0, 2) != CsvWriter.NULL_MARKER)
    }

    @Test
    @DisplayName("plain fields are written verbatim without quotes")
    fun plainFieldsUnquoted() {
        assertEquals("weight,calories", CsvWriter.row(listOf("weight", "calories")))
        assertEquals("70.50", CsvWriter.row(listOf("70.50")))
    }

    @Test
    @DisplayName("a field containing a delimiter is quoted")
    fun delimiterTriggersQuotes() {
        assertEquals("\"Mon, 6 Apr\"", CsvWriter.row(listOf("Mon, 6 Apr")))
    }

    @Test
    @DisplayName("a field containing a quote is quoted and the quote is doubled")
    fun embeddedQuoteIsDoubled() {
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.row(listOf("say \"hi\"")))
    }

    @Test
    @DisplayName("a field containing a line break is quoted")
    fun lineBreakTriggersQuotes() {
        assertEquals("\"line1\nline2\"", CsvWriter.row(listOf("line1\nline2")))
        assertEquals("\"line1\rline2\"", CsvWriter.row(listOf("line1\rline2")))
    }

    @Test
    @DisplayName("an empty string stays empty rather than becoming a quoted field")
    fun emptyStringStaysEmpty() {
        assertEquals("", CsvWriter.row(listOf("")))
    }

    @Test
    @DisplayName("decimal formatting uses a dot separator regardless of device locale")
    fun decimalsIgnoreDeviceLocale() {
        val commaLocale = Locale.forLanguageTag("it-IT")
        val original = Locale.getDefault()
        try {
            // An Italian default would otherwise render 1234.5 as "1234,5",
            // which a spreadsheet reads as text or as a thousands separator.
            Locale.setDefault(commaLocale)
            assertEquals("1234.50", CsvWriter.number(1234.5, 2))
            assertEquals("0.00", CsvWriter.number(0.0, 2))
            assertEquals("70.500", CsvWriter.number(70.5, 3))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    @DisplayName("number formatting honours the requested precision")
    fun decimalsHonourPrecision() {
        assertEquals("70.50", CsvWriter.number(70.5))
        assertEquals("71", CsvWriter.number(70.5, 0))
        assertEquals("70", CsvWriter.number(70.4, 0))
    }

    @Test
    @DisplayName("absent, NaN and infinite numbers all stay absent")
    fun nonFiniteNumbersStayAbsent() {
        assertNull(CsvWriter.number(null))
        assertNull(CsvWriter.number(Double.NaN))
        assertNull(CsvWriter.number(Double.POSITIVE_INFINITY))
        assertNull(CsvWriter.number(Double.NEGATIVE_INFINITY))
    }

    @Test
    @DisplayName("integers render without a decimal separator")
    fun integersRenderPlain() {
        assertEquals("42", CsvWriter.integer(42L))
        assertEquals("42", CsvWriter.integer(42))
        assertNull(CsvWriter.integer(null as Long?))
        assertNull(CsvWriter.integer(null as Int?))
    }

    @Test
    @DisplayName("a document starts with the header row and one line per record")
    fun documentStructure() {
        val document = CsvWriter.document(
            headers = listOf("date", "weightKg", "note"),
            rows = listOf(
                listOf("2026-04-01", "80.20", null),
                listOf("2026-04-02", null, "rest day")
            )
        )
        assertEquals("date,weightKg,note", document.lines()[0])
        assertEquals("2026-04-01,80.20,", document.lines()[1])
        assertEquals("2026-04-02,,rest day", document.lines()[2])
        assertEquals(3, document.lines().size)
    }

    @Test
    @DisplayName("a header-only document has no trailing newline")
    fun headerOnlyDocumentHasNoTrailingNewline() {
        val document = CsvWriter.document(listOf("a", "b"), emptyList())
        assertEquals("a,b", document)
        assertFalse(document.endsWith("\n"))
    }

    @Test
    @DisplayName("an empty document with no headers is an empty string")
    fun emptyDocumentIsEmpty() {
        assertEquals("", CsvWriter.document(emptyList(), emptyList()))
    }

    @Test
    @DisplayName("quoted fields survive a round trip through an independent RFC 4180 parser")
    fun quotedFieldsRoundTrip() {
        val rows = listOf(
            listOf("Mon, 6 Apr", "say \"hi\""),
            listOf("plain", "multi\nline"),
            listOf("trailing", "line\n\nbreak")
        )
        val document = CsvWriter.document(listOf("a", "b"), rows)
        val parsed = parseRecords(document)

        assertEquals(4, parsed.size)
        assertEquals(listOf("a", "b"), parsed[0])
        assertEquals(listOf("Mon, 6 Apr", "say \"hi\""), parsed[1])
        assertEquals(listOf("plain", "multi\nline"), parsed[2])
        assertEquals(listOf("trailing", "line\n\nbreak"), parsed[3])
    }

    @Test
    @DisplayName("a value containing a separator cannot forge an extra column")
    fun separatorInValueDoesNotForgeColumn() {
        val document = CsvWriter.document(
            headers = listOf("date", "note"),
            rows = listOf(listOf("2026-04-01", "a,b,c"))
        )
        assertEquals(2, parseRecords(document)[1].size)
    }

    /**
     * Independent RFC 4180 reader used to check the writer. It splits into
     * records while tracking quote state, so a value containing a line break
     * stays inside its own record instead of faking a new one.
     */
    private fun parseRecords(document: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < document.length) {
            val char = document[index]
            when {
                inQuotes && char == '"' && index + 1 < document.length && document[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                char == '\n' && !inQuotes -> {
                    fields.add(current.toString())
                    records.add(fields)
                    fields = mutableListOf()
                    current.setLength(0)
                }
                char != '\r' -> current.append(char)
            }
            index++
        }
        if (current.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(current.toString())
            records.add(fields)
        }
        return records
    }
}
