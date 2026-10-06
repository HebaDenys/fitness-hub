package io.github.hebadenys.fitnesshub.core.sync

import org.junit.jupiter.api.Test

class OperationalLogLineTest {
    @Test fun validOperationalCountsAndModesRemainUseful() {
        val line = OperationalLogLine.format("ab12cd34", "sync_complete", mapOf("dailyRows" to 30, "durationMs" to 90L, "mode" to "full"))
        check(line == "ab12cd34 sync_complete dailyRows=30 durationMs=90 mode=full")
    }
    @Test fun healthValuesCredentialsAndUnknownKeysNeverAppear() {
        val line = OperationalLogLine.format("ab12cd34", "sync_failed", mapOf("weight" to 84.25, "token" to "PRIVATE_SENTINEL", "errorClass" to "PRIVATE_SENTINEL"))
        check(line == "ab12cd34 sync_failed errorClass=OtherError")
    }
    @Test fun untrustedEventAndCorrelationTextCannotInjectLogs() {
        val line = OperationalLogLine.format("PRIVATE_SENTINEL", "PRIVATE_SENTINEL\nforged", mapOf("dailyRows" to 10))
        check(line == "- event_filtered")
    }
    @Test fun arbitraryObjectToStringIsNeverInvokedEvenUnderAllowedKey() {
        val trap = object { override fun toString(): String = error("must never execute") }
        val line = OperationalLogLine.format(null, "changes_page", mapOf("changes" to trap, "errorClass" to trap))
        check(line == "- changes_page errorClass=OtherError")
    }
    @Test fun stringNumbersFloatsAndNegativeCountsAreNotAccepted() {
        val line = OperationalLogLine.format(null, "sync_complete", mapOf("dailyRows" to "84.25", "changes" to -1, "durationMs" to 84.25))
        check(line == "- sync_complete")
    }
    @Test fun knownErrorClassIsNotReplacedByExceptionMessage() {
        check(OperationalLogLine.format(null, "sync_failed", mapOf("errorClass" to "IOException")) == "- sync_failed errorClass=IOException")
    }
}
