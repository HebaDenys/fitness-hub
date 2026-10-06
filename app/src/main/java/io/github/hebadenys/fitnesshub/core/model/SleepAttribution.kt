package io.github.hebadenys.fitnesshub.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Attributes sleep sessions to the day the sleeper wakes up.
 *
 * A session belongs to wake day D when its local END time falls inside the
 * noon-to-noon window `[D - 1 12:00, D 12:00)` in the given system zone.
 * Each session is attributed to exactly one wake day, so nights crossing
 * midnight are never counted on both sides.
 */
object SleepAttribution {

    /** Wake day for a session ending at [endInstant], using noon-to-noon windows. */
    fun wakeDate(endInstant: Instant, zone: ZoneId): LocalDate {
        val endLocal = endInstant.atZone(zone).toLocalDateTime()
        return if (endLocal.toLocalTime() >= LocalTime.NOON) {
            endLocal.toLocalDate().plusDays(1)
        } else {
            endLocal.toLocalDate()
        }
    }

    /** Total sleep minutes per wake day. Sessions absent from the input are absent from the map (null, never zero). */
    fun minutesByWakeDay(sessions: List<SleepInterval>, zone: ZoneId): Map<LocalDate, Long> =
        sessions
            .groupBy { wakeDate(it.end, zone) }
            .mapValues { (_, night) -> night.sumOf { Duration.between(it.start, it.end).toMinutes() } }
}
