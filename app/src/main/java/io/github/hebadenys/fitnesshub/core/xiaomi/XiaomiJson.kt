package io.github.hebadenys.fitnesshub.core.xiaomi

/** Bounded strict JSON at the vendor boundary. No Android/Room/network dependency. */
internal sealed class XiaomiJson {
    final override fun toString(): String = "XiaomiJson(<redacted>)"
    data class Object(val fields: Map<String, XiaomiJson>) : XiaomiJson()
    data class Array(val elements: List<XiaomiJson>) : XiaomiJson()
    data class Text(val value: String) : XiaomiJson()
    data class Number(val literal: String) : XiaomiJson()
    data class Bool(val value: Boolean) : XiaomiJson()
    data object Null : XiaomiJson()

    /** Only call for an explicitly selected local payload, never from logging. */
    fun encode(): String = when (this) {
        is Object -> fields.entries.sortedBy { it.key }.joinToString(",", "{", "}") {
            Text(it.key).encode() + ":" + it.value.encode()
        }
        is Array -> elements.joinToString(",", "[", "]") { it.encode() }
        is Text -> buildString {
            append('"')
            for (ch in value) when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
            append('"')
        }
        is Number -> literal
        is Bool -> value.toString()
        Null -> "null"
    }
}

internal enum class XiaomiFailure {
    MALFORMED_JSON, LIMIT_EXCEEDED, UNEXPECTED_RESPONSE, VENDOR_ERROR,
    INVALID_SCOPE, INVALID_CURSOR, UNEXPECTED_PAGE_SIZE, INVALID_TIMESTAMP,
    UNORDERED_PAGE, STALLED_CURSOR, FETCH_FAILED, COMMIT_FAILED
}

/** Static message and no chained payload-bearing exceptions. */
internal class XiaomiProtocolException(val failure: XiaomiFailure) : Exception(failure.name)

internal class XiaomiJsonReader(private val input: String) {
    private var position = 0
    private var nodes = 0

    fun read(): XiaomiJson {
        if (input.length > MAX_CHARACTERS) fail(XiaomiFailure.LIMIT_EXCEEDED)
        val value = value(0)
        whitespace()
        if (position != input.length) fail()
        return value
    }

    private fun value(depth: Int): XiaomiJson {
        if (depth > MAX_DEPTH || ++nodes > MAX_NODES) fail(XiaomiFailure.LIMIT_EXCEEDED)
        whitespace()
        return when (peek()) {
            '{' -> objectValue(depth)
            '[' -> arrayValue(depth)
            '"' -> XiaomiJson.Text(text())
            't' -> { literal("true"); XiaomiJson.Bool(true) }
            'f' -> { literal("false"); XiaomiJson.Bool(false) }
            'n' -> { literal("null"); XiaomiJson.Null }
            '-', in '0'..'9' -> number()
            else -> fail()
        }
    }

    private fun objectValue(depth: Int): XiaomiJson.Object {
        take('{')
        whitespace()
        val fields = linkedMapOf<String, XiaomiJson>()
        if (consume('}')) return XiaomiJson.Object(fields)
        while (true) {
            whitespace()
            val key = text()
            if (key in fields) fail() // Duplicate subject/metric keys must not silently win.
            whitespace(); take(':')
            fields[key] = value(depth + 1)
            whitespace()
            if (consume('}')) return XiaomiJson.Object(fields.toMap())
            take(',')
        }
    }

    private fun arrayValue(depth: Int): XiaomiJson.Array {
        take('[')
        whitespace()
        val elements = mutableListOf<XiaomiJson>()
        if (consume(']')) return XiaomiJson.Array(elements)
        while (true) {
            elements += value(depth + 1)
            whitespace()
            if (consume(']')) return XiaomiJson.Array(elements.toList())
            take(',')
        }
    }

    private fun text(): String {
        take('"')
        val out = StringBuilder()
        while (position < input.length) {
            val ch = input[position++]
            when {
                ch == '"' -> {
                    // Reject unpaired surrogate code units in both literal and escaped input.
                    var i = 0
                    while (i < out.length) {
                        val current = out[i++]
                        if (Character.isHighSurrogate(current)) {
                            if (i == out.length || !Character.isLowSurrogate(out[i++])) fail()
                        } else if (Character.isLowSurrogate(current)) fail()
                    }
                    return out.toString()
                }
                ch == '\\' -> {
                    if (position >= input.length) fail()
                    out.append(when (val escaped = input[position++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            if (position + 4 > input.length) fail()
                            val hex = input.substring(position, position + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail()
                            position += 4
                            hex.toInt(16).toChar()
                        }
                        else -> fail()
                    })
                }
                ch < ' ' -> fail()
                else -> out.append(ch)
            }
            if (out.length > MAX_STRING) fail(XiaomiFailure.LIMIT_EXCEEDED)
        }
        fail()
    }

    private fun number(): XiaomiJson.Number {
        val start = position
        consume('-')
        if (consume('0')) {
            if (peek() in '0'..'9') fail()
        } else {
            if (peek() !in '1'..'9') fail()
            while (peek() in '0'..'9') position++
        }
        if (consume('.')) digits()
        if (consume('e') || consume('E')) { if (!consume('+')) consume('-'); digits() }
        if (position - start > MAX_NUMBER) fail(XiaomiFailure.LIMIT_EXCEEDED)
        return XiaomiJson.Number(input.substring(start, position))
    }

    private fun digits() { if (peek() !in '0'..'9') fail(); while (peek() in '0'..'9') position++ }
    private fun literal(value: String) { if (!input.startsWith(value, position)) fail(); position += value.length }
    private fun whitespace() { while (peek() in listOf(' ', '\t', '\r', '\n')) position++ }
    private fun peek(): Char = input.getOrNull(position) ?: '\u0000'
    private fun consume(ch: Char): Boolean = (peek() == ch).also { if (it) position++ }
    private fun take(ch: Char) { if (!consume(ch)) fail() }
    private fun fail(reason: XiaomiFailure = XiaomiFailure.MALFORMED_JSON): Nothing = throw XiaomiProtocolException(reason)

    companion object {
        const val MAX_CHARACTERS = 1_048_576
        const val MAX_STRING = 65_536
        const val MAX_DEPTH = 16
        const val MAX_NODES = 20_000
        const val MAX_NUMBER = 64
    }
}
