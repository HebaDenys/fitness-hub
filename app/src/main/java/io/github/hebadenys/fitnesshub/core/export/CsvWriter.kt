package io.github.hebadenys.fitnesshub.core.export

/**
 * Minimal RFC 4180 CSV writer.
 *
 * Values that are absent are written as an empty field rather than `0`, so a
 * spreadsheet cannot mistake "not measured" for "measured zero". Numeric
 * formatting uses a plain dot decimal separator so the file opens correctly
 * regardless of the device locale.
 */
object CsvWriter {

    const val NULL_MARKER = ""

    fun row(values: List<String?>): String =
        values.joinToString(",") { escape(it) }

    fun document(headers: List<String>, rows: List<List<String?>>): String {
        val builder = StringBuilder()
        builder.append(row(headers))
        for (dataRow in rows) {
            builder.append('\n')
            builder.append(row(dataRow))
        }
        return builder.toString()
    }

    /** Formats a number for CSV: absent stays absent, otherwise a dot decimal. */
    fun number(value: Double?, decimals: Int = 2): String? {
        if (value == null) return null
        if (value.isNaN() || value.isInfinite()) return null
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
    }

    fun integer(value: Long?): String? = value?.toString()

    fun integer(value: Int?): String? = value?.toString()

    /**
     * Quotes a field when it contains a delimiter, a quote or a line break, and
     * doubles any embedded quote, per RFC 4180.
     */
    private fun escape(value: String?): String {
        val raw = value ?: return NULL_MARKER
        val needsQuotes = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }
}
