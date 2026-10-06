package io.github.hebadenys.fitnesshub.core.workout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MuscleGroup(val storedValue: String) {
    CHEST("CHEST"),
    BACK("BACK"),
    LEGS("LEGS"),
    SHOULDERS("SHOULDERS"),
    ARMS("ARMS"),
    CORE("CORE"),
    FULL_BODY("FULL_BODY"),
    CARDIO("CARDIO");

    companion object {
        fun fromStored(value: String): MuscleGroup =
            entries.firstOrNull { it.storedValue == value } ?: FULL_BODY
    }
}

enum class Equipment(val storedValue: String) {
    BARBELL("BARBELL"),
    DUMBBELL("DUMBBELL"),
    MACHINE("MACHINE"),
    CABLE("CABLE"),
    BODYWEIGHT("BODYWEIGHT"),
    KETTLEBELL("KETTLEBELL"),
    BAND("BAND"),
    OTHER("OTHER");

    companion object {
        fun fromStored(value: String): Equipment =
            entries.firstOrNull { it.storedValue == value } ?: OTHER
    }
}

/**
 * An exercise the user can log.
 *
 * The built-in catalogue is seeded as rows marked [isBuiltIn]; user-created
 * movements live in the same table so a session can reference either without
 * branching, which is why the unique index is on the name rather than a flag.
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    @ColumnInfo(defaultValue = "0") val isBuiltIn: Boolean = false,
    val isArchived: Boolean = false
)

/** A logged training session. */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val title: String? = null,
    val notes: String? = null,
    val exportedRecordId: String? = null
)

/**
 * One performed set.
 *
 * [reps] and [loadKg] are nullable so a bodyweight or timed set can be logged
 * honestly instead of being forced into a numeric zero.
 */
@Entity(
    tableName = "workout_sets",
    indices = [Index(value = ["sessionId"]), Index(value = ["exerciseId"])]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val loadKg: Double? = null,
    val rpe: Double? = null,
    val repsInReserve: Int? = null,
    val isWarmUp: Boolean = false,
    val loggedAt: Long = System.currentTimeMillis()
)

/** A reusable routine that pre-fills a session's exercises. */
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workout_template_exercises",
    indices = [Index(value = ["templateId"])]
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetLoadKg: Double? = null,
    val restSeconds: Int? = null
)
