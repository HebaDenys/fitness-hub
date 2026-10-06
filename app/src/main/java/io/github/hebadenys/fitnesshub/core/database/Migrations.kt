package io.github.hebadenys.fitnesshub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: nullable metrics, provenance columns on existing tables, and the
 * three vitals series tables. Zero-valued v1 aggregates become null because v1
 * used zero for both a recorded zero and missing data, so their provenance is
 * unknowable.
 *
 * SQLite cannot alter column nullability in place, so daily_health is rebuilt;
 * exercise_sessions only gains columns and is altered in place.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_health_v2` (" +
                "`date` TEXT NOT NULL, " +
                "`steps` INTEGER, `distanceMeters` REAL, `activeCalories` REAL, `totalCalories` REAL, " +
                "`sleepMinutes` INTEGER, `restingHeartRate` INTEGER, `oxygenSaturation` REAL, " +
                "`weightKg` REAL, `bodyFatPercent` REAL, " +
                "`syncedAt` INTEGER NOT NULL, `dataOrigins` TEXT NOT NULL, " +
                "`provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT, " +
                "PRIMARY KEY(`date`))"
        )
        db.execSQL(
            "INSERT INTO `daily_health_v2` " +
                "(`date`, `steps`, `distanceMeters`, `activeCalories`, `totalCalories`, " +
                "`sleepMinutes`, `restingHeartRate`, `oxygenSaturation`, `weightKg`, `bodyFatPercent`, " +
                "`syncedAt`, `dataOrigins`, `provenance`, `algorithm`) " +
                 "SELECT `date`, CASE WHEN `steps` = 0 THEN NULL ELSE `steps` END, " +
                 "CASE WHEN `distanceMeters` = 0 THEN NULL ELSE `distanceMeters` END, " +
                 "CASE WHEN `activeCalories` = 0 THEN NULL ELSE `activeCalories` END, " +
                 "CASE WHEN `totalCalories` = 0 THEN NULL ELSE `totalCalories` END, " +
                 "CASE WHEN `sleepMinutes` = 0 THEN NULL ELSE `sleepMinutes` END, " +
                 "`restingHeartRate`, `oxygenSaturation`, `weightKg`, `bodyFatPercent`, " +
                "`syncedAt`, '', 'MEASURED', NULL FROM `daily_health`"
        )
        db.execSQL("DROP TABLE `daily_health`")
        db.execSQL("ALTER TABLE `daily_health_v2` RENAME TO `daily_health`")

        db.execSQL("ALTER TABLE `exercise_sessions` ADD COLUMN `provenance` TEXT NOT NULL DEFAULT 'MEASURED'")
        db.execSQL("ALTER TABLE `exercise_sessions` ADD COLUMN `algorithm` TEXT")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `heart_rate_samples` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `timeEpochMillis` INTEGER NOT NULL, `bpm` INTEGER NOT NULL, " +
                "`dataOrigin` TEXT NOT NULL, `provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_heart_rate_samples_time_origin` " +
                "ON `heart_rate_samples` (`timeEpochMillis`, `dataOrigin`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `oxygen_saturation_readings` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `timeEpochMillis` INTEGER NOT NULL, `percentage` REAL NOT NULL, " +
                "`dataOrigin` TEXT NOT NULL, `provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_oxygen_saturation_readings_time_origin` " +
                "ON `oxygen_saturation_readings` (`timeEpochMillis`, `dataOrigin`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `resting_heart_rate_readings` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `timeEpochMillis` INTEGER NOT NULL, `bpm` INTEGER NOT NULL, " +
                "`dataOrigin` TEXT NOT NULL, `provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_resting_heart_rate_readings_time_origin` " +
                "ON `resting_heart_rate_readings` (`timeEpochMillis`, `dataOrigin`)"
        )
    }
}

/**
 * v2 -> v3: the local nutrition store. Foods, log entries and derived daily
 * totals. A nullable `barcode` cannot be part of a unique index in SQLite, so
 * uniqueness of known barcodes is enforced by a partial index instead.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `barcode` TEXT, " +
                "`name` TEXT NOT NULL, `brand` TEXT, `servingSizeGrams` REAL, `servingLabel` TEXT, " +
                "`energyKcal` REAL, `proteinGrams` REAL, `carbsGrams` REAL, `fatGrams` REAL, " +
                "`sugarGrams` REAL, `fiberGrams` REAL, `saltGrams` REAL, " +
                "`provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_items_barcode` " +
                "ON `food_items` (`barcode`) WHERE `barcode` IS NOT NULL"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nutrition_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foodId` INTEGER NOT NULL, " +
                "`date` TEXT NOT NULL, `mealType` TEXT NOT NULL, `servings` REAL NOT NULL, " +
                "`servingGrams` REAL, `loggedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_nutrition_entries_date_mealType` " +
                "ON `nutrition_entries` (`date`, `mealType`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_nutrition_entries_foodId` " +
                "ON `nutrition_entries` (`foodId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nutrition_daily` (" +
                "`date` TEXT NOT NULL, `energyKcal` REAL, `proteinGrams` REAL, `carbsGrams` REAL, " +
                "`fatGrams` REAL, `sugarGrams` REAL, `fiberGrams` REAL, `saltGrams` REAL, " +
                "`entryCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
        )
    }
}

/**
 * v3 -> v4: the local scale store. Raw weigh-ins captured passively over BLE,
 * the derived body composition estimates, and the user profile the estimates
 * depend on. The unique index on (device, timestamp) is what makes a repeated
 * broadcast of the same weigh-in a no-op.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scale_measurements` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceAddress` TEXT NOT NULL, " +
                "`measuredAtMillis` INTEGER NOT NULL, `weightKg` REAL, `impedanceOhms` REAL, " +
                "`heartRateBpm` INTEGER, `profileSlot` INTEGER, " +
                "`provenance` TEXT NOT NULL DEFAULT 'MEASURED', `algorithm` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_scale_measurements_device_measuredAtMillis` " +
                "ON `scale_measurements` (`deviceAddress`, `measuredAtMillis`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_composition_estimates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `measuredAtMillis` INTEGER NOT NULL, " +
                "`bodyFatPercent` REAL, `leanMassKg` REAL, `bodyWaterPercent` REAL, " +
                "`basalMetabolicRateKcal` REAL, `visceralFatIndex` REAL, " +
                "`provenance` TEXT NOT NULL DEFAULT 'ESTIMATE', `algorithm` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_composition_estimates_measuredAtMillis` " +
                "ON `body_composition_estimates` (`measuredAtMillis`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_profile` (" +
                "`id` INTEGER NOT NULL, `heightCm` REAL, `ageYears` INTEGER, `sex` TEXT, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v4 -> v5: the local workout store. The exercise catalogue (built-in and
 * user-created in one table), logged sessions and sets, plus reusable
 * templates.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`muscleGroup` TEXT NOT NULL, `equipment` TEXT NOT NULL, " +
                "`isBuiltIn` INTEGER NOT NULL DEFAULT 0, `isArchived` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAtMillis` INTEGER NOT NULL, " +
                "`endedAtMillis` INTEGER, `title` TEXT, `notes` TEXT, `exportedRecordId` TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`exerciseId` INTEGER NOT NULL, `setNumber` INTEGER NOT NULL, `reps` INTEGER, " +
                "`loadKg` REAL, `rpe` REAL, `repsInReserve` INTEGER, " +
                "`isWarmUp` INTEGER NOT NULL DEFAULT 0, `loggedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sets_sessionId` ON `workout_sets` (`sessionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sets_exerciseId` ON `workout_sets` (`exerciseId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_template_exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, " +
                "`exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetSets` INTEGER, " +
                "`targetReps` INTEGER, `targetLoadKg` REAL, `restSeconds` INTEGER)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_template_exercises_templateId` " +
                "ON `workout_template_exercises` (`templateId`)"
        )
    }
}
