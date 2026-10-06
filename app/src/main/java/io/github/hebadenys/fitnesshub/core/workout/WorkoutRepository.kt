package io.github.hebadenys.fitnesshub.core.workout

import kotlinx.coroutines.flow.Flow

/**
 * Workout logging over the local catalogue.
 *
 * Everything here works offline: the catalogue is seeded into Room on first
 * use, sessions and sets are stored locally, and strength estimates come from
 * published formulas rather than any remote calculation.
 */
class WorkoutRepository(
    private val dao: WorkoutDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun observeExercises(): Flow<List<WorkoutExerciseEntity>> = dao.observeExercises()

    fun observeSessions(limit: Int = 50): Flow<List<WorkoutSessionEntity>> = dao.observeSessions(limit)

    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>> = dao.observeTemplates()

    fun observeSessionSets(sessionId: Long): Flow<List<SessionWithSets>> = dao.observeSessionSets(sessionId)

    suspend fun searchExercises(query: String): List<WorkoutExerciseEntity> = dao.searchExercises(query)

    suspend fun exercise(id: Long): WorkoutExerciseEntity? = dao.exercise(id)

    /**
     * Seeds the built-in catalogue once. Uses IGNORE inserts keyed on the unique
     * exercise name, so re-running it can never duplicate or overwrite a row,
     * and it never touches user-created exercises.
     */
    suspend fun seedBuiltInExercises() {
        if (dao.exerciseCount() > 0) return
        dao.insertExercises(BUILT_IN_EXERCISES)
    }

    /** Returns the new exercise id, or null when the name is already taken. */
    suspend fun createExercise(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment
    ): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            dao.insertExercise(
                WorkoutExerciseEntity(
                    name = trimmed,
                    muscleGroup = muscleGroup.storedValue,
                    equipment = equipment.storedValue,
                    isBuiltIn = false
                )
            )
        }.getOrNull()
    }

    suspend fun archiveExercise(id: Long) = dao.archiveExercise(id)

    suspend fun startSession(title: String? = null): Long =
        dao.insertSession(WorkoutSessionEntity(startedAtMillis = clock(), title = title))

    suspend fun endSession(sessionId: Long, notes: String? = null) {
        val session = dao.session(sessionId) ?: return
        if (session.endedAtMillis != null) return
        dao.updateSession(session.copy(endedAtMillis = clock(), notes = notes))
    }

    suspend fun session(sessionId: Long): WorkoutSessionEntity? = dao.session(sessionId)

    /**
     * Appends a set. Reps and load stay nullable so a bodyweight or timed set
     * is recorded as what it is rather than as a zero.
     */
    suspend fun logSet(
        sessionId: Long,
        exerciseId: Long,
        reps: Int?,
        loadKg: Double?,
        rpe: Double?,
        repsInReserve: Int?,
        isWarmUp: Boolean = false
    ): Long {
        val setNumber = (dao.maxSetNumber(sessionId, exerciseId) ?: 0) + 1
        return dao.insertSet(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                reps = reps?.takeIf { it > 0 },
                loadKg = loadKg?.takeIf { it > 0.0 },
                rpe = rpe?.takeIf { it in 1.0..10.0 },
                repsInReserve = repsInReserve?.takeIf { it >= 0 },
                isWarmUp = isWarmUp
            )
        )
    }

    suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)

    suspend fun setsForSession(sessionId: Long): List<WorkoutSetEntity> = dao.setsForSession(sessionId)

    /** Personal records broken across every historical set, excluding warm-ups. */
    suspend fun currentRecords(): List<PersonalRecordDetector.Record> {
        val sets = dao.setsForAllExercises().mapNotNull { set ->
            val load = set.loadKg ?: return@mapNotNull null
            val reps = set.reps ?: return@mapNotNull null
            PersonalRecordDetector.CandidateSet(set.exerciseId, load, reps, set.loggedAt, set.isWarmUp)
        }
        return PersonalRecordDetector.detect(sets, emptyList())
    }

    /** Estimated one-rep max for an exercise, or null when nothing usable is logged. */
    suspend fun estimatedOneRepMax(exerciseId: Long): OneRepMaxCalculator.Estimate? {
        val sets = dao.setsForAllExercises().filter { it.exerciseId == exerciseId }
        return sets.mapNotNull { set ->
            val reps = set.reps ?: return@mapNotNull null
            val load = set.loadKg ?: return@mapNotNull null
            OneRepMaxCalculator.best(load, reps)
        }.maxByOrNull { it.oneRepMaxKg }
    }

    suspend fun allEstimatedOneRepMax(): Map<Long, OneRepMaxCalculator.Estimate> =
        dao.setsForAllExercises()
            .filter { it.loadKg != null && it.reps != null }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val best = sets.mapNotNull { set ->
                    OneRepMaxCalculator.best(set.loadKg!!, set.reps!!)
                }.maxByOrNull { it.oneRepMaxKg }
                best?.let { exerciseId to it }
            }
            .toMap()

    suspend fun saveTemplate(name: String, exerciseIds: List<Long>): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || exerciseIds.isEmpty()) return null
        val templateId = dao.insertTemplate(WorkoutTemplateEntity(name = trimmed))
        dao.insertTemplateExercises(
            exerciseIds.mapIndexed { index, exerciseId ->
                WorkoutTemplateExerciseEntity(templateId = templateId, exerciseId = exerciseId, position = index)
            }
        )
        return templateId
    }

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    suspend fun templateExercises(templateId: Long): List<WorkoutTemplateExerciseEntity> =
        dao.templateExercises(templateId)

    suspend fun markExported(sessionId: Long, recordId: String) = dao.markExported(sessionId, recordId)

    companion object {
        val BUILT_IN_EXERCISES: List<WorkoutExerciseEntity> = listOf(
            WorkoutExerciseEntity(name = "Barbell Back Squat", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Barbell Bench Press", muscleGroup = MuscleGroup.CHEST.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Barbell Deadlift", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Barbell Bent-Over Row", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Barbell Overhead Press", muscleGroup = MuscleGroup.SHOULDERS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Front Squat", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Romanian Deadlift", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Walking Lunge", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Bulgarian Split Squat", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Leg Press", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Leg Extension", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Lying Leg Curl", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Standing Calf Raise", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Dumbbell Bench Press", muscleGroup = MuscleGroup.CHEST.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Dumbbell Incline Press", muscleGroup = MuscleGroup.CHEST.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Dumbbell Fly", muscleGroup = MuscleGroup.CHEST.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Push-Up", muscleGroup = MuscleGroup.CHEST.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Pull-Up", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Chin-Up", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Lat Pulldown", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Seated Cable Row", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Straight-Arm Pulldown", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Dumbbell Row", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "T-Bar Row", muscleGroup = MuscleGroup.BACK.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Face Pull", muscleGroup = MuscleGroup.SHOULDERS.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Rear Delt Fly", muscleGroup = MuscleGroup.SHOULDERS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Arnold Press", muscleGroup = MuscleGroup.SHOULDERS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Barbell Curl", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "EZ-Bar Curl", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Hammer Curl", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Preacher Curl", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.MACHINE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Cable Triceps Extension", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Triceps Pushdown", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Close-Grip Bench Press", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Dip", muscleGroup = MuscleGroup.ARMS.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Hanging Leg Raise", muscleGroup = MuscleGroup.CORE.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Cable Crunch", muscleGroup = MuscleGroup.CORE.storedValue, equipment = Equipment.CABLE.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Plank", muscleGroup = MuscleGroup.CORE.storedValue, equipment = Equipment.BODYWEIGHT.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Kettlebell Swing", muscleGroup = MuscleGroup.FULL_BODY.storedValue, equipment = Equipment.KETTLEBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Romanian Deadlift (Dumbbell)", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.DUMBBELL.storedValue, isBuiltIn = true),
            WorkoutExerciseEntity(name = "Hip Thrust", muscleGroup = MuscleGroup.LEGS.storedValue, equipment = Equipment.BARBELL.storedValue, isBuiltIn = true)
        )
    }
}
