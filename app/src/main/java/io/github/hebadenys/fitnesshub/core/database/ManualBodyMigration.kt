package io.github.hebadenys.fitnesshub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v8 -> v9: local manual weight/body-fat observations with exact timestamps. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `manual_body_measurements` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`measuredAtMillis` INTEGER NOT NULL, `weightKg` REAL, " +
                "`bodyFatPercent` REAL, `createdAtMillis` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_manual_body_measurements_measuredAtMillis` " +
                "ON `manual_body_measurements` (`measuredAtMillis`)"
        )
    }
}
