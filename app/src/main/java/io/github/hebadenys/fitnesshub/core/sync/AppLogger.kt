package io.github.hebadenys.fitnesshub.core.sync

import android.util.Log
import java.util.UUID

/**
 * Structured operational logger. Every sync run gets a random correlation id.
 *
 * Allowed payload values are counts, durations (ms), sync modes and error class
 * names only. Health values (steps, weight, heart rate, sleep, ...) must never
 * appear in any logged field or message.
 */
class AppLogger(private val writer: (String) -> Unit = { line -> Log.i(TAG, line) }) {

    fun newSyncId(): String = UUID.randomUUID().toString().take(8)

    fun log(syncId: String?, event: String, fields: Map<String, Any> = emptyMap()) {
        val details = fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
        val line = if (details.isEmpty()) "$syncId $event" else "$syncId $event $details"
        writer(line)
    }

    companion object {
        private const val TAG = "FitnessHub"
    }
}
