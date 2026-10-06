package io.github.hebadenys.fitnesshub.core.workout

/**
 * Personal record detection over logged sets.
 *
 * A record is only ever claimed from a set that was actually performed, and a
 * single is always a real observation while a multi-rep maximum is an estimate,
 * so the two are never conflated when reporting.
 */
object PersonalRecordDetector {

    enum class RecordKind {
        /** Heaviest single-rep set. A measurement, not an estimate. */
        ONE_REP_MAX,

        /** Heaviest estimated three-rep max. */
        ESTIMATED_THREE_REP_MAX,

        /** Highest total volume (load x reps) in one set. */
        SET_VOLUME
    }

    data class Record(
        val kind: RecordKind,
        val exerciseId: Long,
        val loadKg: Double,
        val reps: Int,
        val achievedAt: Long,
        val formula: String?
    )

    data class CandidateSet(
        val exerciseId: Long,
        val loadKg: Double,
        val reps: Int,
        val achievedAt: Long,
        val isWarmUp: Boolean = false
    )

    /**
     * Returns the records broken by [sets], or an empty list when it beats nothing.
     *
     * Warm-up sets are excluded: a light warm-up is not a stronger result, and
     * counting it would overwrite real records with meaningless ones.
     */
    fun detect(sets: List<CandidateSet>, previous: List<Record>): List<Record> {
        // Warm-ups are excluded outright: a light warm-up is not a stronger
        // result, and letting one through would overwrite real records with
        // meaningless ones.
        val working = sets.filterNot { it.isWarmUp }
            .filter { it.loadKg > 0.0 && it.reps > 0 }
        if (working.isEmpty()) return emptyList()

        val broken = mutableListOf<Record>()
        val existing = previous.associateBy { it.kind to it.exerciseId }

        for (exerciseId in working.map { it.exerciseId }.distinct()) {
            val forExercise = working.filter { it.exerciseId == exerciseId }

            val bestSingle = forExercise.filter { it.reps == 1 }.maxByOrNull { it.loadKg }
            if (bestSingle != null) {
                val current = existing[RecordKind.ONE_REP_MAX to exerciseId]
                if (current == null || bestSingle.loadKg > current.loadKg) {
                    broken += Record(
                        kind = RecordKind.ONE_REP_MAX,
                        exerciseId = exerciseId,
                        loadKg = bestSingle.loadKg,
                        reps = 1,
                        achievedAt = bestSingle.achievedAt,
                        formula = null
                    )
                }
            }

            val threeRepSets = forExercise.filter { it.reps == 3 }
            val bestThree = threeRepSets
                .mapNotNull { set ->
                    OneRepMaxCalculator.epley(set.loadKg, set.reps)?.let { it to set }
                }
                .maxByOrNull { it.first }
            if (bestThree != null) {
                val current = existing[RecordKind.ESTIMATED_THREE_REP_MAX to exerciseId]
                if (current == null || bestThree.first > current.loadKg) {
                    broken += Record(
                        kind = RecordKind.ESTIMATED_THREE_REP_MAX,
                        exerciseId = exerciseId,
                        loadKg = bestThree.first,
                        reps = 3,
                        achievedAt = bestThree.second.achievedAt,
                        formula = OneRepMaxCalculator.EPLEY
                    )
                }
            }

            val bestVolume = forExercise.maxByOrNull { it.loadKg * it.reps }
            if (bestVolume != null) {
                val volume = bestVolume.loadKg * bestVolume.reps
                val current = existing[RecordKind.SET_VOLUME to exerciseId]
                if (current == null || volume > current.loadKg * current.reps) {
                    broken += Record(
                        kind = RecordKind.SET_VOLUME,
                        exerciseId = exerciseId,
                        loadKg = bestVolume.loadKg,
                        reps = bestVolume.reps,
                        achievedAt = bestVolume.achievedAt,
                        formula = null
                    )
                }
            }
        }
        return broken
    }
}
