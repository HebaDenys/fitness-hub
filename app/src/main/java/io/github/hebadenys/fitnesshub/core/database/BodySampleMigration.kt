package io.github.hebadenys.fitnesshub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds exact Health Connect body records without changing legacy daily summaries. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hc_weight_samples` (" +
                "`recordId` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `kilograms` REAL NOT NULL, " +
                "`dataOrigin` TEXT NOT NULL, PRIMARY KEY(`recordId`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hc_weight_samples_timeEpochMillis_dataOrigin` " +
                "ON `hc_weight_samples` (`timeEpochMillis`, `dataOrigin`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hc_body_fat_samples` (" +
                "`recordId` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`timeEpochMillis` INTEGER NOT NULL, `percentage` REAL NOT NULL, " +
                "`dataOrigin` TEXT NOT NULL, PRIMARY KEY(`recordId`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hc_body_fat_samples_timeEpochMillis_dataOrigin` " +
                "ON `hc_body_fat_samples` (`timeEpochMillis`, `dataOrigin`)"
        )
    }
}
