package io.github.hebadenys.fitnesshub.core.scale

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.ZoneId

class ScaleHistoryCsvImporterTest {

    private val dao = mock<ScaleDao>()
    private val importer = ScaleHistoryCsvImporter(dao, ZoneId.of("UTC"))

    @Test
    fun multipleUsersRequireExplicitFilter() = runTest {
        val csv = """
            Date,Weight,BMI,BodyFat,BodyWater,BoneMass,MetabolicAge,MuscleMass,PhysiqueRating,ProteinMass,VisceralFat,BasalMetabolism,HeartRate,SkeletalMuscleMass,User,Source
            2026-10-01 08:00:00,100.00,,30.00,50.00,,,,,,12,1900,70,,Denys,scale-a
            2026-10-01 09:00:00,60.00,,25.00,52.00,,,,,,8,1400,65,,Dahiana,scale-a
        """.trimIndent()

        val result = importer.import(csv)

        val multiple = assertInstanceOf(ScaleHistoryImportResult.MultipleUsers::class.java, result)
        assertEquals(listOf("Dahiana", "Denys"), multiple.users)
        verify(dao, never()).insertMeasurement(any())
    }

    @Test
    fun selectedUserImportsOnlyTheirRowsAndComposition() = runTest {
        whenever(dao.insertMeasurement(any())).thenReturn(42L)
        val csv = """
            Date,Weight,BMI,BodyFat,BodyWater,BoneMass,MetabolicAge,MuscleMass,PhysiqueRating,ProteinMass,VisceralFat,BasalMetabolism,HeartRate,SkeletalMuscleMass,User,Source
            2026-10-01 08:00:00,100.00,,30.00,50.00,,,,,,12,1900,70,,Denys,scale-a
            2026-10-01 09:00:00,60.00,,25.00,52.00,,,,,,8,1400,65,,Dahiana,scale-a
        """.trimIndent()

        val result = importer.import(csv, "Denys")

        val success = assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result)
        assertEquals(1, success.importedRows)
        assertEquals(0, success.duplicateRows)
        assertEquals(0, success.skippedRows)
        verify(dao).insertMeasurement(
            org.mockito.kotlin.check {
                assertEquals(100.0, it.weightKg)
                assertEquals(70L, it.heartRateBpm)
                assertEquals(ScaleMeasurementEntity.PROVENANCE_IMPORTED, it.provenance)
            }
        )
        verify(dao).upsertEstimate(
            org.mockito.kotlin.check {
                assertEquals(30.0, it.bodyFatPercent)
                assertEquals(50.0, it.bodyWaterPercent)
                assertEquals(12.0, it.visceralFatIndex)
                assertEquals(1900.0, it.basalMetabolicRateKcal)
                assertEquals(ScaleMeasurementEntity.PROVENANCE_IMPORTED, it.provenance)
            }
        )
    }

    @Test
    fun zeroWeightRowsAreSkipped() = runTest {
        val csv = """
            Date,Weight,User,Source
            2026-10-01 08:00:00,0,Denys,scale-a
        """.trimIndent()

        val result = importer.import(csv, "Denys")

        val success = assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result)
        assertEquals(0, success.importedRows)
        assertEquals(1, success.skippedRows)
        verify(dao, never()).insertMeasurement(any())
    }
}
