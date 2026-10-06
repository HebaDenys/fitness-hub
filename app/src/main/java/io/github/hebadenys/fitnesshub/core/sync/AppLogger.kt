package io.github.hebadenys.fitnesshub.core.sync

import android.util.Log
import java.util.UUID

/** Operational-only logger. Field names, types and string values are allowlisted. */
class AppLogger(private val writer: (String) -> Unit = { line -> Log.i(TAG, line) }) {
    fun newSyncId(): String = UUID.randomUUID().toString().take(8)

    fun log(syncId: String?, event: String, fields: Map<String, Any> = emptyMap()) {
        writer(OperationalLogLine.format(syncId, event, fields))
    }

    companion object {
        private const val TAG = "FitnessHub"
    }
}
