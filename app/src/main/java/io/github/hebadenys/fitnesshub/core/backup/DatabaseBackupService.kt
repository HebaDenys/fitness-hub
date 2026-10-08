package io.github.hebadenys.fitnesshub.core.backup

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiArchiveIntegrity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Version 2 portable archive of registered domain tables/columns, preserving primary keys.
 * No SQL from an archive is executed: names/order/types must match the compiled local schema.
 * Credentials, preferences, media and operational cursors are intentionally excluded.
 * Restore is conservative merge: conflicting primary keys roll back the entire batch.
 */
class DatabaseBackupService @Inject constructor(private val database: HealthDatabase) {
    data class RestoreResult(val totalRows: Int, val identicalRows: Int = 0, val legacyPartial: Boolean = false)
    class ArchiveException(val reason: String) : Exception(reason)

    suspend fun createBackup(passphrase: CharArray): String = withContext(Dispatchers.IO) {
        try {
            val payload = database.withTransaction { export(database.openHelper.writableDatabase) }
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            try {
                if (bytes.size > MAX_BYTES) fail("archive_too_large")
                BackupCrypto.encryptToBase64(bytes, passphrase)
            } finally { bytes.fill(0) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: ArchiveException) { throw error
        } catch (_: Exception) { fail("backup_failed")
        } finally { passphrase.fill('\u0000') }
    }

    suspend fun restoreBackup(encoded: String, passphrase: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        var plaintext: ByteArray? = null
        try {
            if (encoded.length > MAX_ENCODED_CHARACTERS) fail("archive_too_large")
            plaintext = BackupCrypto.decryptFromBase64(encoded, passphrase)
            if (plaintext.size > MAX_BYTES) fail("archive_too_large")
            val text = String(plaintext, Charsets.UTF_8)
            checkBackupJsonBounds(text)
            val root = JSONObject(text)
            when (exactInteger(root.get("payloadVersion"))) {
                1L -> database.withTransaction {
                    // The old adapter lacks reliable identity/upsert semantics: never merge into health data.
                    val sql = database.openHelper.writableDatabase
                    for (table in DOMAIN_TABLES.filterNot { it == "exercises" || it == "user_profile" }) {
                        sql.query("SELECT 1 FROM ${quote(table)} LIMIT 1").use {
                            if (it.moveToFirst()) fail("legacy_requires_empty_archive")
                        }
                    }
                    val legacy = BackupService(database).restoreBackup(encoded, passphrase.copyOf())
                    RestoreResult(legacy.totalRows, legacyPartial = true)
                }
                2L -> database.withTransaction { restore(root, database.openHelper.writableDatabase) }
                else -> fail("unsupported_archive_version")
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: ArchiveException) { throw error
        } catch (error: BackupCrypto.InvalidBackupException) { throw error
        } catch (_: Exception) { fail("invalid_or_incompatible_backup")
        } finally { plaintext?.fill(0); passphrase.fill('\u0000') }
    }

    private suspend fun export(db: SupportSQLiteDatabase): JSONObject {
        val schemas = schema(db)
        validateBackupRelationships(db)
        XiaomiArchiveIntegrity.validate(database)
        var cells = 0L
        var characters = 0L
        val tables = JSONArray()
        for (table in schemas) {
            currentCoroutineContext().ensureActive()
            val rows = JSONArray()
            val names = table.columns.joinToString(",") { quote(it.name) }
            db.query("SELECT $names FROM ${quote(table.name)} ORDER BY ${table.keys.joinToString(",") { quote(it.name) }}").use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val values = JSONArray()
                    for (index in table.columns.indices) {
                        val value = validateValue(cursor.valueAt(index), table.columns[index])
                        values.put(value ?: JSONObject.NULL)
                        characters += value?.toString()?.length ?: 4
                        if (++cells > MAX_CELLS || characters > MAX_BYTES) fail("archive_too_large")
                    }
                    rows.put(values)
                }
            }
            tables.put(JSONObject().put("name", table.name)
                .put("columns", JSONArray(table.columns.map { it.name })).put("rows", rows))
        }
        return JSONObject().put("payloadVersion", 2).put("databaseVersion", db.version)
            .put("coverage", "registered_database_rows")
            .put("excluded", JSONArray(listOf("xiaomi_checkpoints", "credentials", "preferences", "media")))
            .put("tables", tables)
    }

    private suspend fun restore(root: JSONObject, db: SupportSQLiteDatabase): RestoreResult {
        if (exactInteger(root.get("databaseVersion")) != db.version.toLong()) fail("incompatible_database_version")
        val schemas = schema(db)
        val input = root.getJSONArray("tables")
        if (input.length() != schemas.size) fail("incomplete_archive")
        var cellCount = 0L
        val batches = schemas.mapIndexed { index, table ->
            val source = input.getJSONObject(index)
            if (source.getString("name") != table.name) fail("invalid_table")
            val columns = source.getJSONArray("columns")
            if (columns.length() != table.columns.size || table.columns.indices.any {
                    columns.getString(it) != table.columns[it].name
                }) fail("invalid_columns")
            val array = source.getJSONArray("rows")
            val rows = (0 until array.length()).map { rowIndex ->
                val row = array.getJSONArray(rowIndex)
                if (row.length() != table.columns.size) fail("invalid_row")
                table.columns.mapIndexed { columnIndex, column ->
                    if (++cellCount > MAX_CELLS) fail("archive_too_large")
                    validateValue(row.get(columnIndex).takeUnless { it === JSONObject.NULL }, column)
                }
            }
            table to rows
        }
        var inserted = 0
        var identical = 0
        for ((table, rows) in batches) {
            val keyIndices = table.keys.map { table.columns.indexOf(it) }
            val projection = table.columns.joinToString(",") { quote(it.name) }
            val where = table.keys.joinToString(" AND ") { "${quote(it.name)} IS ?" }
            val insert = "INSERT INTO ${quote(table.name)} ($projection) VALUES (${table.columns.joinToString(",") { "?" }})"
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val arguments = keyIndices.map { row[it] }.toTypedArray()
                val existing = db.query(SimpleSQLiteQuery("SELECT $projection FROM ${quote(table.name)} WHERE $where", arguments)).use { cursor ->
                    if (!cursor.moveToFirst()) null else table.columns.mapIndexed { index, column ->
                        validateValue(cursor.valueAt(index), column)
                    }
                }
                if (existing != null) {
                    if (existing != row) fail("backup_conflict")
                    identical++
                } else {
                    db.execSQL(insert, row.toTypedArray())
                    inserted++
                }
            }
        }
        db.query("PRAGMA foreign_key_check").use { if (it.moveToFirst()) fail("invalid_relationship") }
        validateBackupRelationships(db)
        XiaomiArchiveIntegrity.validate(database)
        database.xiaomiArchiveDao().resetCheckpoints()
        return RestoreResult(inserted, identical)
    }

    private data class Column(val name: String, val type: String, val required: Boolean, val keyOrder: Int)
    private data class Table(val name: String, val columns: List<Column>) {
        val keys get() = columns.filter { it.keyOrder > 0 }.sortedBy { it.keyOrder }
    }
    private fun schema(db: SupportSQLiteDatabase): List<Table> {
        val actual = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) actual += cursor.getString(0)
        }
        if ((actual - SYSTEM_TABLES - DOMAIN_TABLES.toSet()).any { !it.startsWith("sqlite_") }) fail("unregistered_database_table")
        if (!actual.containsAll(DOMAIN_TABLES)) fail("missing_database_table")
        return DOMAIN_TABLES.map { name ->
            val columns = mutableListOf<Column>()
            db.query("PRAGMA table_info(${quote(name)})").use { cursor ->
                while (cursor.moveToNext()) columns += Column(cursor.getString(1), cursor.getString(2), cursor.getInt(3) != 0, cursor.getInt(5))
            }
            // Physical order may differ after ALTER TABLE; canonical order is portable.
            Table(name, columns.sortedBy { it.name }).also { if (it.keys.isEmpty()) fail("missing_primary_key") }
        }
    }
    private fun validateValue(value: Any?, column: Column): Any? {
        if (value == null) {
            if (column.required || column.keyOrder > 0) fail("missing_required_value")
            return null
        }
        return when (column.type.uppercase(java.util.Locale.ROOT)) {
            "TEXT" -> (value as? String)?.takeIf { it.length <= MAX_CELL_CHARACTERS } ?: fail("invalid_text")
            "INTEGER" -> exactInteger(value)
            "REAL" -> (value as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: fail("invalid_number")
            else -> fail("unsupported_column_type")
        }
    }
    private fun exactInteger(value: Any): Long = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        else -> fail("invalid_integer")
    }
    private fun Cursor.valueAt(index: Int): Any? = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
        Cursor.FIELD_TYPE_STRING -> getString(index)
        else -> fail("unsupported_cell_type")
    }
    private fun quote(value: String) = "`" + value.replace("`", "``") + "`"
    private fun fail(reason: String): Nothing = throw ArchiveException(reason)

    companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        const val MAX_ENCODED_CHARACTERS = 45 * 1024 * 1024
        private const val MAX_CELLS = 2_000_000L
        private const val MAX_CELL_CHARACTERS = 2 * 1024 * 1024
        val DOMAIN_TABLES = listOf(
            "daily_health", "exercise_sessions", "heart_rate_samples", "oxygen_saturation_readings", "resting_heart_rate_readings",
            "food_items", "nutrition_entries", "nutrition_daily", "scale_measurements", "body_composition_estimates", "user_profile",
            "exercises", "workout_sessions", "workout_sets", "workout_templates", "workout_template_exercises",
            "source_identity", "xiaomi_bindings", "xiaomi_snapshots",
            "hc_weight_samples", "hc_body_fat_samples", "manual_body_measurements"
        )
        private val SYSTEM_TABLES = setOf("android_metadata", "room_master_table", "sqlite_sequence", "xiaomi_checkpoints")
    }
}
