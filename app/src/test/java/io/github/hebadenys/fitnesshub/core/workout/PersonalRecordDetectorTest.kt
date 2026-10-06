package io.github.hebadenys.fitnesshub.core.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PersonalRecordDetectorTest {

    private fun set(
        loadKg: Double,
        reps: Int,
        exerciseId: Long = 1,
        at: Long = 1_000,
        warmUp: Boolean = false
    ) = PersonalRecordDetector.CandidateSet(exerciseId, loadKg, reps, at, warmUp)

    private fun record(kind: PersonalRecordDetector.RecordKind, loadKg: Double, reps: Int, exerciseId: Long = 1) =
        PersonalRecordDetector.Record(kind, exerciseId, loadKg, reps, 0, null)

    @Test
    @DisplayName("an empty history produces records, because the first entry is always a record")
    fun firstEntries_areRecords() {
        val records = PersonalRecordDetector.detect(listOf(set(100.0, 5)), emptyList())

        assertTrue(records.any { it.kind == PersonalRecordDetector.RecordKind.SET_VOLUME })
        // Five reps is not a three-rep set, so no estimated three-rep max exists yet.
        assertFalse(records.any { it.kind == PersonalRecordDetector.RecordKind.ESTIMATED_THREE_REP_MAX })
    }

    @Test
    @DisplayName("a heavy single is recorded as a measured one-rep max with no formula attached")
    fun singleRep_isMeasuredRecord() {
        val records = PersonalRecordDetector.detect(listOf(set(140.0, 1)), emptyList())

        val oneRm = records.first { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX }
        assertEquals(140.0, oneRm.loadKg, 0.0001)
        assertEquals(null, oneRm.formula)
    }

    @Test
    @DisplayName("a three-rep set produces an estimated three-rep max naming its formula")
    fun threeRep_isEstimatedRecord() {
        val records = PersonalRecordDetector.detect(listOf(set(120.0, 3)), emptyList())

        val threeRm = records.first { it.kind == PersonalRecordDetector.RecordKind.ESTIMATED_THREE_REP_MAX }
        assertEquals(OneRepMaxCalculator.epley(120.0, 3)!!, threeRm.loadKg, 0.0001)
        assertEquals(OneRepMaxCalculator.EPLEY, threeRm.formula)
    }

    @Test
    @DisplayName("warm-up sets never beat a real record")
    fun warmUps_doNotCount() {
        val records = PersonalRecordDetector.detect(listOf(set(20.0, 10, warmUp = true)), emptyList())

        assertFalse(records.any { it.loadKg == 20.0 })
    }

    @Test
    @DisplayName("a warm-up heavier than nothing still does not claim the one-rep record")
    fun warmUpDoesNotClaimOneRepMax() {
        val records = PersonalRecordDetector.detect(listOf(set(60.0, 1, warmUp = true)), emptyList())

        assertFalse(records.any { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX })
    }

    @Test
    @DisplayName("beating a stored record yields a new record; matching it yields none")
    fun comparesAgainstPreviousRecords() {
        val previous = listOf(record(PersonalRecordDetector.RecordKind.ONE_REP_MAX, 140.0, 1))

        val improved = PersonalRecordDetector.detect(listOf(set(145.0, 1)), previous)
        assertTrue(improved.any { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX && it.loadKg == 145.0 })

        val unchanged = PersonalRecordDetector.detect(listOf(set(140.0, 1)), previous)
        assertFalse(unchanged.any { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX })
    }

    @Test
    @DisplayName("a worse result than the stored record yields nothing")
    fun worseResult_yieldsNothing() {
        val previous = listOf(record(PersonalRecordDetector.RecordKind.ONE_REP_MAX, 140.0, 1))

        val records = PersonalRecordDetector.detect(listOf(set(130.0, 1)), previous)

        assertFalse(records.any { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX })
    }

    @Test
    @DisplayName("records are tracked per exercise, so one lift never sets another's record")
    fun recordsAreScopedPerExercise() {
        val sets = listOf(
            set(100.0, 1, exerciseId = 1),
            set(200.0, 1, exerciseId = 2)
        )

        val records = PersonalRecordDetector.detect(sets, emptyList())

        assertEquals(2, records.count { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX })
        assertEquals(setOf(1L, 2L), records.filter { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX }.map { it.exerciseId }.toSet())
    }

    @Test
    @DisplayName("volume picks the set with the highest load times reps, not the heaviest load")
    fun volumeRecord_usesHighestSingleSet() {
        val records = PersonalRecordDetector.detect(
            listOf(set(100.0, 5), set(60.0, 12)),
            emptyList()
        )

        // 60 x 12 = 720 beats 100 x 5 = 500 even though 100 kg is the heavier lift.
        val volume = records.first { it.kind == PersonalRecordDetector.RecordKind.SET_VOLUME }
        assertEquals(60.0, volume.loadKg, 0.0001)
        assertEquals(12, volume.reps)
    }

    @Test
    @DisplayName("sets with no load or no reps are ignored instead of counting as zero")
    fun incompleteSets_areIgnored() {
        val records = PersonalRecordDetector.detect(
            listOf(
                PersonalRecordDetector.CandidateSet(1, 0.0, 10, 1),
                PersonalRecordDetector.CandidateSet(1, 100.0, 0, 1)
            ),
            emptyList()
        )

        assertTrue(records.isEmpty())
    }

    @Test
    @DisplayName("the heaviest single within a session wins when several are logged")
    fun heaviestSingleWins() {
        val records = PersonalRecordDetector.detect(
            listOf(set(100.0, 1), set(125.0, 1), set(90.0, 1)),
            emptyList()
        )

        val oneRm = records.first { it.kind == PersonalRecordDetector.RecordKind.ONE_REP_MAX }
        assertEquals(125.0, oneRm.loadKg, 0.0001)
    }

    @Test
    @DisplayName("a single without any three-rep work produces no three-rep record")
    fun singleOnly_hasNoThreeRepRecord() {
        val records = PersonalRecordDetector.detect(listOf(set(100.0, 1)), emptyList())

        assertFalse(records.any { it.kind == PersonalRecordDetector.RecordKind.ESTIMATED_THREE_REP_MAX })
    }
}
