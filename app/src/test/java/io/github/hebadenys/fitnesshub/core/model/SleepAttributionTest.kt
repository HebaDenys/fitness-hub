package io.github.hebadenys.fitnesshub.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Sleep attribution rules: a session belongs to the day the sleeper wakes up,
 * inside the noon-to-noon window `[D - 1 12:00, D 12:00)`.
 */
class SleepAttributionTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    private fun at(date: LocalDate, time: LocalTime): Instant =
        LocalDateTime.of(date, time).atZone(rome).toInstant()

    private fun session(id: String, start: Instant, end: Instant) =
        SleepInterval(id, start, end, "com.example.wearable")

    @Test
    @DisplayName("a session waking before noon belongs to that same calendar day")
    fun wakesBeforeNoon_sameDay() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(day, SleepAttribution.wakeDate(at(day, LocalTime.of(7, 15)), rome))
    }

    @Test
    @DisplayName("a session waking after noon belongs to the following day")
    fun wakesAfterNoon_nextDay() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(day.plusDays(1), SleepAttribution.wakeDate(at(day, LocalTime.of(13, 0)), rome))
    }

    @Test
    @DisplayName("exactly noon starts the next wake day, because the window end is exclusive")
    fun wakesExactlyAtNoon_nextDay() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(day.plusDays(1), SleepAttribution.wakeDate(at(day, LocalTime.NOON), rome))
    }

    @Test
    @DisplayName("just before noon still belongs to the same day")
    fun wakesJustBeforeNoon_sameDay() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(day, SleepAttribution.wakeDate(at(day, LocalTime.of(11, 59, 59)), rome))
    }

    @Test
    @DisplayName("a night crossing midnight is counted once, on the wake day, never on both")
    fun crossingMidnight_countedOnce() {
        val night = LocalDate.of(2026, 3, 10)
        val sessions = listOf(
            session("a", at(night, LocalTime.of(23, 0)), at(night.plusDays(1), LocalTime.of(7, 0)))
        )

        val byDay = SleepAttribution.minutesByWakeDay(sessions, rome)

        assertEquals(setOf(night.plusDays(1)), byDay.keys)
        assertEquals(480L, byDay[night.plusDays(1)])
        assertTrue(byDay[night] == null, "the calendar night itself must not carry the session")
    }

    @Test
    @DisplayName("several sessions on one wake day are summed into that single day")
    fun multipleSessionsOnSameWakeDay_summed() {
        val day = LocalDate.of(2026, 3, 10)
        val sessions = listOf(
            session("a", at(day, LocalTime.of(23, 0)), at(day.plusDays(1), LocalTime.of(2, 0))),
            session("b", at(day.plusDays(1), LocalTime.of(2, 30)), at(day.plusDays(1), LocalTime.of(7, 0)))
        )

        val byDay = SleepAttribution.minutesByWakeDay(sessions, rome)

        assertEquals(mapOf(day.plusDays(1) to 450L), byDay)
    }

    @Test
    @DisplayName("a day with no session is absent from the map rather than zero")
    fun dayWithoutSession_absentNotZero() {
        val day = LocalDate.of(2026, 3, 10)
        val sessions = listOf(
            session("a", at(day, LocalTime.of(23, 0)), at(day.plusDays(1), LocalTime.of(7, 0)))
        )

        val byDay = SleepAttribution.minutesByWakeDay(sessions, rome)

        assertTrue(byDay[day.plusDays(2)] == null, "an unrecorded night must not be reported as 0 minutes")
    }

    @Test
    @DisplayName("a spring-forward night is measured by elapsed time, not by wall clock")
    fun daylightSavingSpringForward_usesElapsedDuration() {
        // Europe/Rome springs forward on 2026-03-29: 02:00 becomes 03:00, so a
        // 00:00-05:00 window is only four real hours despite spanning five clock hours.
        val transitionDay = LocalDate.of(2026, 3, 29)
        val start = at(transitionDay, LocalTime.of(0, 0))
        val end = at(transitionDay, LocalTime.of(5, 0))

        val minutes = SleepAttribution.minutesByWakeDay(listOf(session("dst", start, end)), rome)

        assertEquals(Duration.between(start, end).toMinutes(), minutes[transitionDay])
        assertEquals(240L, minutes[transitionDay])
    }

    @Test
    @DisplayName("a fall-back night is measured by elapsed time, not by wall clock")
    fun daylightSavingFallBack_usesElapsedDuration() {
        // Europe/Rome falls back on 2026-10-25: 03:00 becomes 02:00, so a
        // 00:00-05:00 window is six real hours despite spanning five clock hours.
        val transitionDay = LocalDate.of(2026, 10, 25)
        val start = at(transitionDay, LocalTime.of(0, 0))
        val end = at(transitionDay, LocalTime.of(5, 0))

        val minutes = SleepAttribution.minutesByWakeDay(listOf(session("dst", start, end)), rome)

        assertEquals(Duration.between(start, end).toMinutes(), minutes[transitionDay])
        assertEquals(360L, minutes[transitionDay])
    }

    @Test
    @DisplayName("attribution follows the given zone, not a hardcoded offset")
    fun attributionRespectsZone() {
        // 06:00 UTC is still the previous calendar day in Tokyo (UTC+9).
        val end = Instant.parse("2026-03-10T06:00:00Z")
        val utcDay = LocalDate.of(2026, 3, 10)

        assertEquals(utcDay, SleepAttribution.wakeDate(end, ZoneOffset.UTC))
        assertEquals(utcDay.plusDays(1), SleepAttribution.wakeDate(end, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    @DisplayName("an empty input yields no days at all")
    fun emptyInput_noDays() {
        assertTrue(SleepAttribution.minutesByWakeDay(emptyList(), rome).isEmpty())
    }
}
