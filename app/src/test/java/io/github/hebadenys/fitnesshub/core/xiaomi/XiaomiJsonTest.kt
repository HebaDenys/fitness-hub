package io.github.hebadenys.fitnesshub.core.xiaomi

import org.junit.jupiter.api.Test

class XiaomiJsonTest {
    private fun rejected(value: String, failure: XiaomiFailure = XiaomiFailure.MALFORMED_JSON) {
        val error = runCatching { XiaomiJsonReader(value).read() }.exceptionOrNull()
        check(error is XiaomiProtocolException && error.failure == failure)
    }

    @Test fun nestedArraysEscapesBooleansAndNumericRepresentationsRoundTrip() {
        val json = """{"s":"line\nquote\"\\","values":[null,true,false,-2,1.2e+3,"1.20"]}"""
        val parsed = XiaomiJsonReader(json).read()
        check(XiaomiJsonReader(parsed.encode()).read() == parsed)
        check("quote" !in parsed.toString())
    }

    @Test fun duplicateKeysIncludingEscapedAliasesAreRejected() {
        rejected("""{"accountId":1,"accountId":2}""")
        rejected("""{"user":{"accountId":1,"account\u0049d":2}}""")
    }

    @Test fun permissiveOrTrailingJsonIsNotAccepted() {
        listOf("{'weight':1}", "[1,]", "{a:1}", "{\"a\":1,}", "[]false", "[NaN]", "[01]", "[1.]", "[.5]", "[1e]", "[+1]", "[--1]", "/*comment*/[]", "\"bad\ntext\"")
            .forEach { rejected(it) }
    }

    @Test fun correctlyPairedUnicodeSurrogatesArePreserved() {
        check(XiaomiJsonReader("\"\\uD83D\\uDE00\"").read() == XiaomiJson.Text("😀"))
        rejected("\"\\uD83D\"")
        rejected("\"\\uDE00\"")
        rejected("\"\\uD83D\\u0041\"")
        rejected("\"\\u00g1\"")
    }

    @Test fun hostileDepthLengthAndNumberLimitsFailWithStaticErrors() {
        rejected("[".repeat(18) + "0" + "]".repeat(18), XiaomiFailure.LIMIT_EXCEEDED)
        rejected(" ".repeat(XiaomiJsonReader.MAX_CHARACTERS + 1), XiaomiFailure.LIMIT_EXCEEDED)
        rejected("\"" + "a".repeat(XiaomiJsonReader.MAX_STRING + 1) + "\"", XiaomiFailure.LIMIT_EXCEEDED)
        rejected("1".repeat(XiaomiJsonReader.MAX_NUMBER + 1), XiaomiFailure.LIMIT_EXCEEDED)
        rejected(List(XiaomiJsonReader.MAX_NODES) { "0" }.joinToString(",", "[", "]"), XiaomiFailure.LIMIT_EXCEEDED)
    }
}
