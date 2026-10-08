package io.github.hebadenys.fitnesshub.feature.dashboard

import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class DashboardStepWindowTest {
    private val end = LocalDate.of(2026, 10, 8)

    @Test fun sevenDaysMeansCalendarDaysRatherThanSevenSparseRecords() {
        val result = dashboardStepPoints(
            listOf(
                DailySummary(end.minusDays(40), steps = 9999),
                DailySummary(end.minusDays(6), steps = 1200),
                DailySummary(end.minusDays(2), steps = 0),
                DailySummary(end, steps = 4200, dataOrigins = setOf("fixture.source")),
                DailySummary(end.plusDays(1), steps = 8888)
            ), TimeRange.SEVEN_DAYS, end
        )
        assertEquals(7, result.size)
        assertEquals(end.minusDays(6), result.first().date)
        assertEquals(end, result.last().date)
        assertEquals(5400.0, result.mapNotNull { it.value }.sum())
        assertEquals(0.0, result[4].value)
        assertNull(result[1].value)
        assertEquals("fixture.source", result.last().sourceLabel)
    }

    @Test fun missingMetricRemainsMissingEvenWhenDayHasOtherData() {
        val result = dashboardStepPoints(
            listOf(DailySummary(end, sleepMinutes = 400)), TimeRange.THIRTY_DAYS, end
        )
        assertEquals(30, result.size)
        assertTrue(result.all { it.value == null })
    }

    @Test fun emptyAndNinetyDayWindowsKeepEveryCalendarSlotAcrossYearBoundary() {
        val january = LocalDate.of(2026, 1, 2)
        val result = dashboardStepPoints(emptyList(), TimeRange.NINETY_DAYS, january)
        assertEquals(90, result.size)
        assertEquals(january.minusDays(89), result.first().date)
        assertEquals(january, result.last().date)
        assertTrue(result.all { it.value == null })
    }
}
