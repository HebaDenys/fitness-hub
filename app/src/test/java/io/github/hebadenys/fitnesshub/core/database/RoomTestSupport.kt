package io.github.hebadenys.fitnesshub.core.database

import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.hebadenys.fitnesshub.core.backup.DatabaseBackupService
import org.robolectric.RuntimeEnvironment
import java.util.UUID

internal fun testDatabase(name: String = UUID.randomUUID().toString()): HealthDatabase =
    Room.databaseBuilder(RuntimeEnvironment.getApplication(), HealthDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .allowMainThreadQueries() // Test helper only. Production still uses Room's suspend transactions.
        .build().also { it.openHelper.writableDatabase }

/** Seed synthetic values in every legacy domain table, including optional columns and relations. */
internal fun seedLegacyTables(db: SupportSQLiteDatabase) {
    for (table in DatabaseBackupService.DOMAIN_TABLES.take(16)) {
        val columns = mutableListOf<Pair<String, String>>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(1) to cursor.getString(2)
        }
        val values = columns.map { (name, type) ->
            when (type) {
                "INTEGER" -> 1L
                "REAL" -> 12.5
                "TEXT" -> if (name == "date") "2026-01-02" else "synthetic_$name"
                else -> error("Unexpected fixture column")
            }
        }
        db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`${it.first}`" }}) VALUES (${columns.joinToString(",") { "?" }})", values.toTypedArray())
    }
}

internal fun databaseRows(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
    DatabaseBackupService.DOMAIN_TABLES.associateWith { table ->
        val rows = mutableListOf<List<String?>>()
        db.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) rows += (0 until cursor.columnCount).map { index ->
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> "i:${cursor.getLong(index)}"
                    Cursor.FIELD_TYPE_FLOAT -> "r:${cursor.getDouble(index)}"
                    else -> "t:${cursor.getString(index)}"
                }
            }
        }
        rows.sortedBy { it.joinToString("|") }
    }
