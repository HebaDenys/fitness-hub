package io.github.hebadenys.fitnesshub.core.xiaomi

import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Verify local archive identity/integrity after a restore, within its surrounding Room transaction. */
internal object XiaomiArchiveIntegrity {
    suspend fun validate(database: HealthDatabase) {
        val sql = database.openHelper.writableDatabase
        sql.query("SELECT id FROM source_identity").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                if (++count > 1 || cursor.getInt(0) != 1) invalid()
            }
        }
        sql.query("SELECT ownerId FROM xiaomi_bindings").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                if (++count > 1 || cursor.getInt(0) != 1) invalid()
            }
        }
        val dao = database.xiaomiArchiveDao()
        val binding = dao.binding() ?: return
        val scope = XiaomiScope(binding.connectionId, XiaomiRegion.entries.singleOrNull { it.wireName == binding.region } ?: invalid(), binding.model, binding.loginUid)
        for (id in listOf(binding.subjectUid, binding.subjectAccountId)) {
            if (id.toLongOrNull()?.let { it >= 0 && it.toString() == id } != true) invalid()
        }
        if (binding.deviceId.isBlank() || binding.deviceId.length > 128 || binding.deviceId.any { it < ' ' }) invalid()
        val projection = "contentHash,eventKey,createTimeMillis,measuredAtMillis,parserVersion,acquisitionMethod,snapshotJson"
        sql.query("SELECT $projection FROM xiaomi_snapshots WHERE connectionId = ?", arrayOf(scope.connectionId)).use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val encoded = cursor.getString(6)
                val root = XiaomiJsonReader(encoded).read() as? XiaomiJson.Object ?: invalid()
                val fields = root.fields
                if (XiaomiSnapshotCodec.sha256(encoded) != cursor.getString(0) || root.encode() != encoded) invalid()
                if (fields["formatVersion"] != XiaomiJson.Number("1") ||
                    fields["acquisitionMethod"] != XiaomiJson.Text("XIAOMI_CLOUD") ||
                    cursor.getString(5) != "XIAOMI_CLOUD" ||
                    fields["parserVersion"] != XiaomiJson.Text(cursor.getString(4))) invalid()
                val expectedScope = XiaomiJson.Object(mapOf(
                    "region" to XiaomiJson.Text(scope.region.wireName), "loginUid" to XiaomiJson.Text(scope.loginUid),
                    "model" to XiaomiJson.Text(scope.model)))
                val expectedSubject = XiaomiJson.Object(mapOf(
                    "uid" to XiaomiJson.Text(binding.subjectUid), "accountId" to XiaomiJson.Text(binding.subjectAccountId)))
                if (fields["scope"] != expectedScope || fields["subject"] != expectedSubject ||
                    fields["deviceId"] != XiaomiJson.Text(binding.deviceId) || fields["model"] != XiaomiJson.Text(scope.model)) invalid()
                if (fields["createTimeMillis"] != XiaomiJson.Number(cursor.getLong(2).toString())) invalid()
                val measured = if (cursor.isNull(3)) XiaomiJson.Null else XiaomiJson.Number(cursor.getLong(3).toString())
                if (fields["measuredAtMillis"] != measured) invalid()
                val identityKeys = setOf("scope", "subject", "deviceId", "serial", "model", "fromSource", "createTimeMillis", "measuredAtMillis")
                if (!fields.keys.containsAll(identityKeys)) invalid()
                val identity = XiaomiJson.Object(fields.filterKeys { it in identityKeys })
                if (XiaomiSnapshotCodec.sha256(identity.encode()) != cursor.getString(1)) invalid()
                val metrics = fields["metrics"] as? XiaomiJson.Object ?: invalid()
                for (entry in metrics.fields.values) {
                    val metric = entry as? XiaomiJson.Object ?: invalid()
                    if (metric.fields["method"] !in XiaomiMethod.entries.map { XiaomiJson.Text(it.name) } ||
                        metric.fields["unit"] !in XiaomiUnit.entries.map { XiaomiJson.Text(it.name) }) invalid()
                    val value = metric.fields["value"] ?: invalid()
                    if (value != XiaomiJson.Null && (value as? XiaomiJson.Number)?.literal?.toDoubleOrNull()?.isFinite() != true) invalid()
                }
            }
        }
    }
    private fun invalid(): Nothing = throw IllegalArgumentException("Invalid Xiaomi archive identity or integrity")
}
