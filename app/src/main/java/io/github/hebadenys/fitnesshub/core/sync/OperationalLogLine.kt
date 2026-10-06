package io.github.hebadenys.fitnesshub.core.sync

/** Allowlist, not arbitrary string interpolation. Unknown objects are never stringified. */
internal object OperationalLogLine {
    private val events = setOf("sync_start", "sync_complete", "sync_failed", "changes_page")
    private val counts = setOf("dailyRows", "exerciseRows", "heartRateSamples", "oxygenSamples", "restingSamples", "durationMs", "changes")
    private val errorClasses = setOf(
        "SecurityException", "IllegalStateException", "IllegalArgumentException", "IOException",
        "SocketTimeoutException", "SQLiteException", "SQLiteConstraintException", "XiaomiProtocolException"
    )

    fun format(syncId: String?, event: String, fields: Map<String, Any>): String {
        val id = syncId?.takeIf { it.matches(Regex("[0-9a-f]{8}")) } ?: "-"
        val safeEvent = event.takeIf { it in events } ?: "event_filtered"
        val safeFields = if (event !in events) emptyList() else fields.entries.mapNotNull { (key, value) ->
            when {
                key in counts -> when (value) {
                    is Int -> value.takeIf { it >= 0 }?.let { "$key=$it" }
                    is Long -> value.takeIf { it >= 0 }?.let { "$key=$it" }
                    else -> null
                }
                key == "mode" && value is String && value in setOf("full", "incremental") -> "mode=$value"
                key == "errorClass" -> "errorClass=" + if (value is String && value in errorClasses) value else "OtherError"
                else -> null
            }
        }
        return (listOf(id, safeEvent) + safeFields.sorted()).joinToString(" ")
    }
}
