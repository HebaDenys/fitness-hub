package io.github.hebadenys.fitnesshub.core.scale

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.*

/** Exercises DAO default-method logic. SQLite rollback itself still needs an on-device Room test. */
class ScaleDaoContractTest {
    private val dao = mock<ScaleDao>(defaultAnswer = Answers.CALLS_REAL_METHODS)
    private fun composition(id: Long = 0, provenance: String = "IMPORTED") = BodyCompositionEstimateEntity(
        id = id, measuredAtMillis = 1_000L, bodyFatPercent = 25.0, leanMassKg = null,
        bodyWaterPercent = 50.0, basalMetabolicRateKcal = null, visceralFatIndex = null,
        provenance = provenance, algorithm = "test_fixture"
    )
    private fun row() = ScaleHistoryRow(
        ScaleMeasurementEntity(deviceAddress = "fixture", measuredAtMillis = 1_000L,
            weightKg = 80.0, impedanceOhms = null, heartRateBpm = null, profileSlot = null,
            provenance = "IMPORTED"),
        composition()
    )

    @Test fun secondImportDoesNotInsertCompositionAgain() = runTest {
        whenever(dao.insertMeasurement(any())).thenReturn(1L, -1L)
        assertEquals(1, dao.importHistoryRows(listOf(row())))
        assertEquals(0, dao.importHistoryRows(listOf(row())))
        verify(dao, times(1)).insertImportedComposition(any())
    }

    @Test fun localEstimateCannotReplaceImportedComposition() = runTest {
        whenever(dao.compositionAt(any())).thenReturn(composition(id = 9))
        dao.upsertEstimate(composition(provenance = "ESTIMATE"))
        verify(dao, never()).saveEstimateRow(any())
    }

    @Test fun reestimateReusesPrimaryKeyInsteadOfViolatingTimestampIndex() = runTest {
        whenever(dao.compositionAt(any())).thenReturn(composition(id = 9, provenance = "ESTIMATE"))
        dao.upsertEstimate(composition(provenance = "ESTIMATE"))
        verify(dao).saveEstimateRow(check { assertEquals(9, it.id.toInt()) })
    }
}
