package io.github.hebadenys.fitnesshub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 -> v8: make the nutrient basis explicit.
 *
 * Existing foods keep the exact legacy multiplication behavior via LEGACY; no
 * historical row is silently reinterpreted as per-100g or per-serving.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `food_items` ADD COLUMN `nutrientBasis` TEXT NOT NULL DEFAULT 'LEGACY'"
        )
    }
}
