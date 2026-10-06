package io.github.hebadenys.fitnesshub.core.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SessionWithSets(
    val sessionId: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val title: String?,
    val notes: String?,
    val setId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val equipment: String,
    val setNumber: Int,
    val reps: Int?,
    val loadKg: Double?,
    val rpe: Double?,
    val repsInReserve: Int?,
    val isWarmUp: Boolean,
    val loggedAt: Long
)

@Dao
interface WorkoutDao {

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name")
    fun observeExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE isArchived = 0 AND (name LIKE '%' || :query || '%') ORDER BY name LIMIT :limit")
    suspend fun searchExercises(query: String, limit: Int = 50): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exercise(id: Long): WorkoutExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(exercise: WorkoutExerciseEntity): Long

    @Query("UPDATE exercises SET isArchived = 1 WHERE id = :id AND isBuiltIn = 0")
    suspend fun archiveExercise(id: Long)

    @Query("SELECT * FROM workout_sessions ORDER BY startedAtMillis DESC LIMIT :limit")
    fun observeSessions(limit: Int = 50): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun session(id: Long): WorkoutSessionEntity?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET exportedRecordId = :recordId WHERE id = :id")
    suspend fun markExported(id: Long, recordId: String)

    @Query(
        "SELECT s.id AS sessionId, s.startedAtMillis AS startedAtMillis, s.endedAtMillis AS endedAtMillis, " +
            "s.title AS title, s.notes AS notes, ws.id AS setId, ws.exerciseId AS exerciseId, " +
            "e.name AS exerciseName, e.muscleGroup AS muscleGroup, e.equipment AS equipment, " +
            "ws.setNumber AS setNumber, ws.reps AS reps, ws.loadKg AS loadKg, ws.rpe AS rpe, " +
            "ws.repsInReserve AS repsInReserve, ws.isWarmUp AS isWarmUp, ws.loggedAt AS loggedAt " +
            "FROM workout_sets ws " +
            "INNER JOIN workout_sessions s ON s.id = ws.sessionId " +
            "INNER JOIN exercises e ON e.id = ws.exerciseId " +
            "WHERE s.id = :sessionId ORDER BY e.name, ws.setNumber"
    )
    fun observeSessionSets(sessionId: Long): Flow<List<SessionWithSets>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY setNumber")
    suspend fun setsForSession(sessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets ORDER BY loggedAt")
    suspend fun setsForAllExercises(): List<WorkoutSetEntity>

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT MAX(setNumber) FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun maxSetNumber(sessionId: Long, exerciseId: Long): Int?

    @Query("SELECT * FROM workout_templates ORDER BY name")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Insert
    suspend fun insertTemplateExercises(items: List<WorkoutTemplateExerciseEntity>)

    @Query("SELECT * FROM workout_template_exercises WHERE templateId = :templateId ORDER BY position")
    suspend fun templateExercises(templateId: Long): List<WorkoutTemplateExerciseEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAtMillis")
    suspend fun dumpSessions(): List<WorkoutSessionEntity>

    @Insert
    suspend fun restoreSessions(sessions: List<WorkoutSessionEntity>): List<Long>

    @Query(
        "SELECT e.name AS exerciseName, s.id AS sessionId, s.startedAtMillis AS startedAtMillis, " +
            "ws.setNumber AS setNumber, ws.reps AS reps, ws.loadKg AS loadKg, ws.rpe AS rpe, " +
            "ws.repsInReserve AS repsInReserve, ws.isWarmUp AS isWarmUp " +
            "FROM workout_sets ws " +
            "INNER JOIN workout_sessions s ON s.id = ws.sessionId " +
            "INNER JOIN exercises e ON e.id = ws.exerciseId " +
            "ORDER BY s.startedAtMillis, ws.setNumber"
    )
    suspend fun dumpSets(): List<DumpedSet>

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>): List<Long>

    @Query("SELECT id, name FROM exercises WHERE name IN (:names)")
    suspend fun findExerciseIdsByNames(names: List<String>): List<ExerciseIdRow>

    @Query(
        "SELECT CAST(strftime('%Y-%m-%d', ws.loggedAt / 1000, 'unixepoch', 'localtime') AS TEXT) AS date, " +
            "SUM(ws.loadKg * ws.reps) AS volume " +
            "FROM workout_sets ws WHERE ws.loadKg IS NOT NULL AND ws.reps IS NOT NULL " +
            "GROUP BY date ORDER BY date"
    )
    fun observeDailyVolume(): Flow<List<DailyVolumeRow>>
}

data class DailyVolumeRow(val date: String, val volume: Double?)

data class ExerciseIdRow(val id: Long, val name: String)

/**
 * A set as exported for backup: addressed by exercise name and by the position
 * of its session, because the numeric ids are local to the device the archive
 * came from and mean nothing on another one.
 */
data class DumpedSet(
    val exerciseName: String,
    val sessionId: Long,
    val startedAtMillis: Long,
    val setNumber: Int,
    val reps: Int?,
    val loadKg: Double?,
    val rpe: Double?,
    val repsInReserve: Int?,
    val isWarmUp: Boolean
)
