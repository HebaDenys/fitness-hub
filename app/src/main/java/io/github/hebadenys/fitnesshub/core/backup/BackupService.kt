package io.github.hebadenys.fitnesshub.core.backup

import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises the local analytical store into a JSON payload and seals it with
 * [BackupCrypto].
 *
 * The payload is plain JSON rather than a raw file copy so it stays readable and
 * forward-compatible: a table added in a later version simply appears in the next
 * backup, and an older app ignores tables it does not recognise.
 */
class BackupService(private val database: HealthDatabase) {

    suspend fun createBackup(passphrase: CharArray): String = withContext(Dispatchers.IO) {
        val payload = serialize()
        val sealed = BackupCrypto.encrypt(payload.toString().toByteArray(Charsets.UTF_8), passphrase)
        passphrase.fill('\u0000')
        java.util.Base64.getEncoder().encodeToString(sealed)
    }

    /**
     * Opens a backup and restores it.
     *
     * Decryption and its integrity check happen first, so a wrong passphrase or a
     * damaged archive is refused before any table is touched. The passphrase is
     * wiped regardless of the outcome.
     */
    suspend fun restoreBackup(encoded: String, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val plaintext = try {
            BackupCrypto.decryptFromBase64(encoded, passphrase)
        } finally {
            passphrase.fill('\u0000')
        }
        val root = JSONObject(String(plaintext, Charsets.UTF_8))
        val result = RestoreResult()

        root.optJSONArray(TABLE_DAILY)?.let { applyDaily(it, result) }
        val sessionIds = root.optJSONArray(TABLE_WORKOUT_SESSIONS)
            ?.let { applyWorkoutSessions(it, result) }
            .orEmpty()
        root.optJSONArray(TABLE_WORKOUT_SETS)?.let { applyWorkoutSets(it, sessionIds, result) }
        root.optJSONArray(TABLE_SCALE)?.let { applyScale(it, result) }
        root.optJSONArray(TABLE_NUTRITION_DAILY)?.let { applyNutritionDaily(it, result) }
        root.optJSONArray(TABLE_FOOD_ITEMS)?.let { applyFoodItems(it, result) }
        result
    }

    private suspend fun serialize(): JSONObject = withContext(Dispatchers.IO) {
        val dao = database.healthDao()
        val root = JSONObject()
        root.put(PAYLOAD_VERSION, 1)

        val daily = JSONArray()
        dao.dumpDaily().forEach { entity ->
            daily.put(
                JSONObject().apply {
                    put("date", entity.date)
                    put("steps", entity.steps)
                    put("distanceMeters", entity.distanceMeters)
                    put("activeCalories", entity.activeCalories)
                    put("totalCalories", entity.totalCalories)
                    put("sleepMinutes", entity.sleepMinutes)
                    put("restingHeartRate", entity.restingHeartRate)
                    put("oxygenSaturation", entity.oxygenSaturation)
                    put("weightKg", entity.weightKg)
                    put("bodyFatPercent", entity.bodyFatPercent)
                    put("syncedAt", entity.syncedAt)
                    put("dataOrigins", entity.dataOrigins)
                    put("provenance", entity.provenance)
                    put("algorithm", entity.algorithm ?: JSONObject.NULL)
                }
            )
        }
        root.put(TABLE_DAILY, daily)

        val foodItems = JSONArray()
        database.nutritionDao().dumpFoods().forEach { food ->
            foodItems.put(
                JSONObject().apply {
                    put("barcode", food.barcode ?: JSONObject.NULL)
                    put("name", food.name)
                    put("brand", food.brand ?: JSONObject.NULL)
                    put("servingSizeGrams", food.servingSizeGrams ?: JSONObject.NULL)
                    put("energyKcal", food.energyKcal ?: JSONObject.NULL)
                    put("proteinGrams", food.proteinGrams ?: JSONObject.NULL)
                    put("carbsGrams", food.carbsGrams ?: JSONObject.NULL)
                    put("fatGrams", food.fatGrams ?: JSONObject.NULL)
                    put("sugarGrams", food.sugarGrams ?: JSONObject.NULL)
                    put("fiberGrams", food.fiberGrams ?: JSONObject.NULL)
                    put("saltGrams", food.saltGrams ?: JSONObject.NULL)
                    put("provenance", food.provenance)
                    put("algorithm", food.algorithm ?: JSONObject.NULL)
                    put("createdAt", food.createdAt)
                }
            )
        }
        root.put(TABLE_FOOD_ITEMS, foodItems)

        val nutritionDaily = JSONArray()
        database.nutritionDao().dumpDaily().forEach { totals ->
            nutritionDaily.put(
                JSONObject().apply {
                    put("date", totals.date)
                    put("energyKcal", totals.energyKcal ?: JSONObject.NULL)
                    put("proteinGrams", totals.proteinGrams ?: JSONObject.NULL)
                    put("carbsGrams", totals.carbsGrams ?: JSONObject.NULL)
                    put("fatGrams", totals.fatGrams ?: JSONObject.NULL)
                    put("sugarGrams", totals.sugarGrams ?: JSONObject.NULL)
                    put("entryCount", totals.entryCount)
                    put("updatedAt", totals.updatedAt)
                }
            )
        }
        root.put(TABLE_NUTRITION_DAILY, nutritionDaily)

        val scale = JSONArray()
        database.scaleDao().dumpAll().forEach { measurement ->
            scale.put(
                JSONObject().apply {
                    put("deviceAddress", measurement.deviceAddress)
                    put("measuredAtMillis", measurement.measuredAtMillis)
                    put("weightKg", measurement.weightKg ?: JSONObject.NULL)
                    put("impedanceOhms", measurement.impedanceOhms ?: JSONObject.NULL)
                    put("heartRateBpm", measurement.heartRateBpm ?: JSONObject.NULL)
                    put("profileSlot", measurement.profileSlot ?: JSONObject.NULL)
                }
            )
        }
        root.put(TABLE_SCALE, scale)

        val sessions = JSONArray()
        database.workoutDao().dumpSessions().forEach { session ->
            sessions.put(
                JSONObject().apply {
                    put("startedAtMillis", session.startedAtMillis)
                    put("endedAtMillis", session.endedAtMillis ?: JSONObject.NULL)
                    put("title", session.title ?: JSONObject.NULL)
                    put("notes", session.notes ?: JSONObject.NULL)
                }
            )
        }
        root.put(TABLE_WORKOUT_SESSIONS, sessions)

        val sets = JSONArray()
        database.workoutDao().dumpSets().forEach { set ->
            sets.put(
                JSONObject().apply {
                    put("exerciseName", set.exerciseName)
                    put("sessionStartedAt", set.startedAtMillis)
                    put("setNumber", set.setNumber)
                    put("reps", set.reps ?: JSONObject.NULL)
                    put("loadKg", set.loadKg ?: JSONObject.NULL)
                    put("rpe", set.rpe ?: JSONObject.NULL)
                    put("repsInReserve", set.repsInReserve ?: JSONObject.NULL)
                    put("isWarmUp", set.isWarmUp)
                }
            )
        }
        root.put(TABLE_WORKOUT_SETS, sets)

        root
    }

    data class RestoreResult(
        var dailyRows: Int = 0,
        var foodRows: Int = 0,
        var nutritionRows: Int = 0,
        var scaleRows: Int = 0,
        var sessionRows: Int = 0,
        var setRows: Int = 0
    ) {
        val totalRows: Int get() = dailyRows + foodRows + nutritionRows + scaleRows + sessionRows + setRows
    }

    private suspend fun applyDaily(array: JSONArray, result: RestoreResult) {
        val rows = array.mapObjects { obj ->
            io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity(
                date = obj.getString("date"),
                steps = obj.optDoubleOrNull("steps")?.toLong(),
                distanceMeters = obj.optDoubleOrNull("distanceMeters"),
                activeCalories = obj.optDoubleOrNull("activeCalories"),
                totalCalories = obj.optDoubleOrNull("totalCalories"),
                sleepMinutes = obj.optDoubleOrNull("sleepMinutes")?.toLong(),
                restingHeartRate = obj.optDoubleOrNull("restingHeartRate")?.toLong(),
                oxygenSaturation = obj.optDoubleOrNull("oxygenSaturation"),
                weightKg = obj.optDoubleOrNull("weightKg"),
                bodyFatPercent = obj.optDoubleOrNull("bodyFatPercent"),
                syncedAt = obj.optLong("syncedAt"),
                dataOrigins = obj.optString("dataOrigins", ""),
                provenance = obj.optString("provenance", "MEASURED"),
                algorithm = obj.optStringOrNull("algorithm")
            )
        }
        database.healthDao().upsertDaily(rows)
        result.dailyRows = rows.size
    }

    private suspend fun applyFoodItems(array: JSONArray, result: RestoreResult) {
        val rows = array.mapObjects { obj ->
            io.github.hebadenys.fitnesshub.core.nutrition.FoodEntity(
                barcode = obj.optStringOrNull("barcode"),
                name = obj.getString("name"),
                brand = obj.optStringOrNull("brand"),
                servingSizeGrams = obj.optDoubleOrNull("servingSizeGrams"),
                energyKcal = obj.optDoubleOrNull("energyKcal"),
                proteinGrams = obj.optDoubleOrNull("proteinGrams"),
                carbsGrams = obj.optDoubleOrNull("carbsGrams"),
                fatGrams = obj.optDoubleOrNull("fatGrams"),
                sugarGrams = obj.optDoubleOrNull("sugarGrams"),
                fiberGrams = obj.optDoubleOrNull("fiberGrams"),
                saltGrams = obj.optDoubleOrNull("saltGrams"),
                provenance = obj.optString("provenance", "MEASURED"),
                algorithm = obj.optStringOrNull("algorithm"),
                createdAt = obj.optLong("createdAt")
            )
        }
        val dao = database.nutritionDao()
        rows.forEach { dao.insertIgnoringExisting(it) }
        result.foodRows = rows.size
    }

    private suspend fun applyNutritionDaily(array: JSONArray, result: RestoreResult) {
        val rows = array.mapObjects { obj ->
            io.github.hebadenys.fitnesshub.core.nutrition.NutritionDailyEntity(
                date = obj.getString("date"),
                energyKcal = obj.optDoubleOrNull("energyKcal"),
                proteinGrams = obj.optDoubleOrNull("proteinGrams"),
                carbsGrams = obj.optDoubleOrNull("carbsGrams"),
                fatGrams = obj.optDoubleOrNull("fatGrams"),
                sugarGrams = obj.optDoubleOrNull("sugarGrams"),
                entryCount = obj.optInt("entryCount"),
                updatedAt = obj.optLong("updatedAt")
            )
        }
        database.nutritionDao().upsertDaily(rows)
        result.nutritionRows = rows.size
    }

    private suspend fun applyScale(array: JSONArray, result: RestoreResult) {
        val rows = array.mapObjects { obj ->
            io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity(
                deviceAddress = obj.getString("deviceAddress"),
                measuredAtMillis = obj.getLong("measuredAtMillis"),
                weightKg = obj.optDoubleOrNull("weightKg"),
                impedanceOhms = obj.optDoubleOrNull("impedanceOhms"),
                heartRateBpm = obj.optDoubleOrNull("heartRateBpm")?.toLong(),
                profileSlot = obj.optDoubleOrNull("profileSlot")?.toInt()
            )
        }
        database.scaleDao().restoreAll(rows)
        result.scaleRows = rows.size
    }

    private suspend fun applyWorkoutSessions(array: JSONArray, result: RestoreResult): Map<Long, Long> {
        val rows = array.mapObjects { obj ->
            io.github.hebadenys.fitnesshub.core.workout.WorkoutSessionEntity(
                startedAtMillis = obj.getLong("startedAtMillis"),
                endedAtMillis = if (obj.isNull("endedAtMillis")) null else obj.getLong("endedAtMillis"),
                title = obj.optStringOrNull("title"),
                notes = obj.optStringOrNull("notes")
            )
        }
        val dao = database.workoutDao()
        val inserted = dao.restoreSessions(rows)
        result.sessionRows = rows.size
        // The new ids differ from the originals, so sets are re-attached by start time.
        return rows.mapIndexed { index, row -> row.startedAtMillis to inserted[index] }.toMap()
    }

    private suspend fun applyWorkoutSets(
        array: JSONArray,
        sessionIds: Map<Long, Long>,
        result: RestoreResult
    ) {
        val parsed = array.mapObjects { obj ->
            RestoredSet(
                exerciseName = obj.getString("exerciseName"),
                sessionStartedAt = obj.optLong("sessionStartedAt"),
                setNumber = obj.optInt("setNumber"),
                reps = if (obj.isNull("reps")) null else obj.getInt("reps"),
                loadKg = obj.optDoubleOrNull("loadKg"),
                rpe = obj.optDoubleOrNull("rpe"),
                repsInReserve = if (obj.isNull("repsInReserve")) null else obj.getInt("repsInReserve"),
                isWarmUp = obj.optBoolean("isWarmUp")
            )
        }
        if (parsed.isEmpty()) {
            result.setRows = 0
            return
        }

        val dao = database.workoutDao()
        val exerciseIds = dao.findExerciseIdsByNames(parsed.map { it.exerciseName }.distinct())
            .associate { it.name to it.id }
        val rows = parsed.mapNotNull { set ->
            val exerciseId = exerciseIds[set.exerciseName] ?: return@mapNotNull null
            val sessionId = sessionIds[set.sessionStartedAt] ?: return@mapNotNull null
            io.github.hebadenys.fitnesshub.core.workout.WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = set.setNumber,
                reps = set.reps,
                loadKg = set.loadKg,
                rpe = set.rpe,
                repsInReserve = set.repsInReserve,
                isWarmUp = set.isWarmUp
            )
        }
        dao.insertSets(rows)
        result.setRows = rows.size
    }

    private data class RestoredSet(
        val exerciseName: String,
        val sessionStartedAt: Long,
        val setNumber: Int,
        val reps: Int?,
        val loadKg: Double?,
        val rpe: Double?,
        val repsInReserve: Int?,
        val isWarmUp: Boolean
    )

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { index -> optJSONObject(index) }.map(transform)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private companion object {
        const val PAYLOAD_VERSION = "payloadVersion"
        const val TABLE_DAILY = "dailyHealth"
        const val TABLE_FOOD_ITEMS = "foodItems"
        const val TABLE_NUTRITION_DAILY = "nutritionDaily"
        const val TABLE_SCALE = "scaleMeasurements"
        const val TABLE_WORKOUT_SESSIONS = "workoutSessions"
        const val TABLE_WORKOUT_SETS = "workoutSets"
    }
}
