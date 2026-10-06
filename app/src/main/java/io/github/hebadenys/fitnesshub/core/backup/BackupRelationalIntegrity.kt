package io.github.hebadenys.fitnesshub.core.backup

import androidx.sqlite.db.SupportSQLiteDatabase

/** Older domain tables do not declare every foreign key. Validate their intended links explicitly. */
internal fun validateBackupRelationships(database: SupportSQLiteDatabase) {
    val links = listOf(
        Triple("nutrition_entries", "foodId", "food_items"),
        Triple("workout_sets", "sessionId", "workout_sessions"),
        Triple("workout_sets", "exerciseId", "exercises"),
        Triple("workout_template_exercises", "templateId", "workout_templates"),
        Triple("workout_template_exercises", "exerciseId", "exercises")
    )
    for ((child, column, parent) in links) {
        database.query("SELECT 1 FROM `$child` AS child LEFT JOIN `$parent` AS parent ON child.`$column` = parent.id WHERE parent.id IS NULL LIMIT 1").use {
            if (it.moveToFirst()) throw DatabaseBackupService.ArchiveException("invalid_relationship")
        }
    }
}
