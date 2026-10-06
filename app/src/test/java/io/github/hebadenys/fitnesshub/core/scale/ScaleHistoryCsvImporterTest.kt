package io.github.hebadenys.fitnesshub.core.scale

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.ZoneId

class ScaleHistoryCsvImporterTest {
    private val dao = mock<ScaleDao>()
    private val importer = ScaleHistoryCsvImporter(dao, ZoneId.of("UTC"))

    @Test fun multipleUsersRequireSelectionBeforeAnyWrite() = runTest {
        val result = importer.import("Date,Weight,User\n2026-10-01 08:00:00,100,Denys\n2026-10-01 09:00:00,60,Dahiana")
        assertEquals(listOf("Dahiana", "Denys"), assertInstanceOf(ScaleHistoryImportResult.MultipleUsers::class.java, result).users)
        verifyNoInteractions(dao)
    }

    @Test fun selectedUserImportsOnlyOwnedRowsAndKeepsVendorValues() = runTest {
        whenever(dao.importHistoryRows(any())).thenReturn(1)
        val result = importer.import("Date,Weight,BodyFat,BodyWater,VisceralFat,BasalMetabolism,HeartRate,User,Source\n2026-10-01 08:00:00,100,30,50,12,1900,70,Denys,scale-a\n2026-10-01 09:00:00,60,25,52,8,1400,65,Dahiana,scale-a", "Denys")
        assertEquals(1, assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result).importedRows)
        val captor = argumentCaptor<List<ScaleHistoryRow>>()
        verify(dao).importHistoryRows(captor.capture())
        val row = captor.firstValue.single()
        assertEquals(100.0, row.measurement.weightKg)
        assertEquals(70L, row.measurement.heartRateBpm)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z").toEpochMilli(), row.measurement.measuredAtMillis)
        assertEquals(30.0, row.composition?.bodyFatPercent)
        assertEquals(50.0, row.composition?.bodyWaterPercent)
        assertEquals(12.0, row.composition?.visceralFatIndex)
        assertEquals(1900.0, row.composition?.basalMetabolicRateKcal)
        assertEquals(ScaleMeasurementEntity.PROVENANCE_IMPORTED, row.composition?.provenance)
        assertNull(row.composition?.leanMassKg)
    }

    @Test fun zeroWeightAndInvalidDatesAreSkippedNotInvented() = runTest {
        val result = importer.import("Date,Weight\n2026-10-01 08:00:00,0\n2026-02-30 08:00:00,100\n2026-10-01 08:00:00,NaN")
        val success = assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result)
        assertEquals(3, success.skippedRows)
        assertEquals(0, success.importedRows)
        verifyNoInteractions(dao)
    }

    @Test fun duplicateCountsComeFromTransactionalWriter() = runTest {
        whenever(dao.importHistoryRows(any())).thenReturn(0)
        val result = importer.import("Date,Weight\n2026-10-01 08:00:00,100")
        val success = assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result)
        assertEquals(0, success.importedRows)
        assertEquals(1, success.duplicateRows)
    }

    @Test fun unknownUserCannotProduceMisleadingSuccess() = runTest {
        val result = importer.import("Date,Weight,User\n2026-10-01 08:00:00,100,Denys", "Dahiana")
        assertEquals(ScaleHistoryImportResult.Failure("user_not_found"), result)
        verifyNoInteractions(dao)
    }

    @Test fun profileNamesAreCaseSensitiveAndUnownedRowsAreExcluded() = runTest {
        whenever(dao.importHistoryRows(any())).thenReturn(1)
        val result = importer.import("Date,Weight,User\n2026-10-01 08:00:00,100,Alex\n2026-10-01 09:00:00,60,alex\n2026-10-01 10:00:00,70,", "Alex")
        val captor = argumentCaptor<List<ScaleHistoryRow>>()
        verify(dao).importHistoryRows(captor.capture())
        assertEquals(100.0, captor.firstValue.single().measurement.weightKg)
        assertEquals(1, assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result).skippedRows)
    }

    @Test fun bomCrLfAndQuotedFieldsAreSupported() = runTest {
        whenever(dao.importHistoryRows(any())).thenReturn(1)
        val result = importer.import("\uFEFFDate,Weight,User,Source\r\n2026-10-01 08:00:00,\"100,25\",\"Denys, A\",\"scale\"\"A\"\r\n", "Denys, A")
        assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result)
        val captor = argumentCaptor<List<ScaleHistoryRow>>()
        verify(dao).importHistoryRows(captor.capture())
        assertEquals(100.25, captor.firstValue.single().measurement.weightKg)
        assertEquals("scale\"A", captor.firstValue.single().measurement.deviceAddress)
    }

    @Test fun malformedShapeAndQuotesFailBeforePersistence() = runTest {
        for (csv in listOf(
            "Date,Weight,User\n2026-10-01 08:00:00,100",
            "Date,Weight,Weight\n2026-10-01 08:00:00,100,110",
            "Date,Weight\n2026-10-01 08:00:00,\"100",
            "Date,Weight\n2026-10-01 08:00:00,\"100\"oops"
        )) assertEquals(ScaleHistoryImportResult.Failure("invalid_csv"), importer.import(csv))
        verifyNoInteractions(dao)
    }

    @Test fun offsetTimestampsAreNotReinterpretedInPhoneTimezone() = runTest {
        whenever(dao.importHistoryRows(any())).thenReturn(1)
        importer.import("Date,Weight\n2026-10-01T08:00:00-03:00,100")
        val captor = argumentCaptor<List<ScaleHistoryRow>>()
        verify(dao).importHistoryRows(captor.capture())
        assertEquals(Instant.parse("2026-10-01T11:00:00Z").toEpochMilli(), captor.firstValue.single().measurement.measuredAtMillis)
    }

    @Test fun ambiguousLocalTimesAreSkipped() = runTest {
        val local = ScaleHistoryCsvImporter(dao, ZoneId.of("America/New_York"))
        val result = local.import("Date,Weight\n2026-03-08 02:30:00,100\n2026-11-01 01:30:00,100")
        assertEquals(2, assertInstanceOf(ScaleHistoryImportResult.Success::class.java, result).skippedRows)
        verifyNoInteractions(dao)
    }

    @Test fun storageFailureReturnsSanitizedError() = runTest {
        whenever(dao.importHistoryRows(any())).thenThrow(IllegalStateException("private payload"))
        assertEquals(ScaleHistoryImportResult.Failure("storage_error"), importer.import("Date,Weight\n2026-10-01 08:00:00,100"))
    }

    @Test fun cancellationIsNotReportedAsSuccessOrStorageError() = runTest {
        whenever(dao.importHistoryRows(any())).thenThrow(CancellationException("cancelled"))
        try {
            importer.import("Date,Weight\n2026-10-01 08:00:00,100")
            fail<Unit>("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun publicFormatAndEmptyInputAreRejectedWithoutWrites() = runTest {
        assertEquals(ScaleHistoryImportResult.Failure("invalid_csv"), importer.import(""))
        assertEquals(ScaleHistoryImportResult.Failure("unsupported_format"), importer.import("Other,Value\na,b"))
        verifyNoInteractions(dao)
    }
}
