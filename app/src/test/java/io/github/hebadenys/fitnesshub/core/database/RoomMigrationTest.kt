package io.github.hebadenys.fitnesshub.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID

/** Executes generated Room migration validation against native SQLite, not mocked DAOs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomMigrationTest {
    @Test fun migrate1To6() { verify(1) }
    @Test fun migrate2To6() { verify(2) }
    @Test fun migrate3To6() { verify(3) }
    @Test fun migrate4To6() { verify(4) }
    @Test fun migrate5To6() { verify(5) }

    private fun verify(version: Int) {
        val name = UUID.randomUUID().toString()
        val path = RuntimeEnvironment.getApplication().getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val root = File(System.getProperty("fitnesshub.schemas"), "io.github.hebadenys.fitnesshub.core.database.HealthDatabase/$version.json")
        val schema = JSONObject(root.readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val originals = linkedMapOf<String, Map<String, String?>>()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                // Older checked-in schema exports omit `indices` for a table with none.
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) raw.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                val fields = entity.getJSONArray("fields")
                val columns = (0 until fields.length()).map { fields.getJSONObject(it) }
                val values: List<Any> = columns.map { field ->
                    when (field.getString("affinity")) {
                        "INTEGER" -> 1L
                        "REAL" -> 1.25
                        "TEXT" -> if (field.getString("columnName") == "date") "2026-01-02" else "synthetic_${field.getString("columnName")}"
                        else -> error("Unsupported fixture affinity")
                    }
                }
                raw.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`${it.getString("columnName")}`" }}) VALUES (${columns.joinToString(",") { "?" }})", values.toTypedArray())
                raw.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    originals[table] = cursor.columnNames.indices.associate { i -> cursor.getColumnName(i) to if (cursor.isNull(i)) null else cursor.getString(i) }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) raw.execSQL(setup.getString(index))
            raw.version = version
        }
        val upgraded = testDatabase(name)
        try {
            val sql = upgraded.openHelper.writableDatabase
            assertEquals(6, sql.version)
            for ((table, values) in originals) {
                sql.query("SELECT * FROM `$table`").use { cursor ->
                    assertTrue("Missing migrated row in $table", cursor.moveToFirst())
                    for ((column, value) in values) {
                        if (version == 1 && column == "source") continue
                        val index = cursor.getColumnIndex(column)
                        assertTrue("Missing column $table.$column", index >= 0)
                        assertEquals("Changed sentinel in $table.$column", value, if (cursor.isNull(index)) null else cursor.getString(index))
                    }
                    assertFalse(cursor.moveToNext())
                }
            }
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { upgraded.close() }
    }
}
