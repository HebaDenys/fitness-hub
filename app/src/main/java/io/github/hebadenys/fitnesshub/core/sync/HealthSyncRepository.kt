package io.github.hebadenys.fitnesshub.core.sync

import io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity
import io.github.hebadenys.fitnesshub.core.database.DailySummaryMapper
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Counts written by one refresh pass; all values are record counts, safe to log. */
data class RefreshCounts(
    val dailyRows: Int,
    val exerciseRows: Int,
    val heartRateSamples: Int,
    val oxygenSamples: Int,
    val restingHeartRateSamples: Int,
    val weightSamples: Int = 0,
    val bodyFatSamples: Int = 0
)

data class SyncResult(val mode: String, val counts: RefreshCounts, val durationMs: Long)

/**
 * Coordinates idempotent ingestion from Health Connect.
 *
 * - Full sync: fixed window (30 days, or 365 with history access), then mints a Changes token.
 * - Incremental sync: consumes the persisted Changes token; expired/missing token falls back
 *   to a full-range sync. Dates outside the granted window are clamped out.
 * - Idempotency: daily rows REPLACE on date, exercises filtered + IGNORE on externalId,
 *   series rows REPLACE on (time, dataOrigin); a full refresh clears series in range first.
 */
class HealthSyncRepository(private val env: SyncEnvironment, private val dao: HealthDao) {

    fun observeDaily(): Flow<List<DailyHealthEntity>> = dao.observeDaily()

    suspend fun sync(): Result<SyncResult> {
        val syncId = env.logger.newSyncId()
        val startedAt = System.currentTimeMillis()
        env.logger.log(syncId, "sync_start")
        val outcome = try {
            Result.success(runSync(syncId, startedAt))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
        outcome.fold(
            onSuccess = { result ->
                env.logger.log(syncId, "sync_complete", mapOf(
                    "mode" to result.mode,
                    "dailyRows" to result.counts.dailyRows,
                    "exerciseRows" to result.counts.exerciseRows,
                    "heartRateSamples" to result.counts.heartRateSamples,
                    "oxygenSamples" to result.counts.oxygenSamples,
                    "restingSamples" to result.counts.restingHeartRateSamples,
                    "weightSamples" to result.counts.weightSamples,
                    "bodyFatSamples" to result.counts.bodyFatSamples,
                    "durationMs" to result.durationMs
                ))
            },
            onFailure = { error ->
                env.logger.log(syncId, "sync_failed", mapOf(
                    "errorClass" to error::class.java.simpleName,
                    "durationMs" to (System.currentTimeMillis() - startedAt)
                ))
            }
        )
        return outcome
    }

    private suspend fun runSync(syncId: String, startedAt: Long): SyncResult {
        val granted = env.source.grantedMetrics()
        require(granted.isNotEmpty()) { "Health Connect permissions missing" }
        val rangeDays = if (env.source.historyAccessGranted()) HISTORY_RANGE_DAYS else DEFAULT_RANGE_DAYS
        val zone = ZoneId.systemDefault()
        val run = SyncRun(syncId, startedAt, granted, rangeDays, env.source.historyAccessGranted(), zone)
        val stored = env.tokens.read()
        return if (stored != null && stored.rangeDays == rangeDays) {
            incremental(run, stored)
        } else {
            full(run)
        }
    }

    private suspend fun full(run: SyncRun): SyncResult {
        val windowStart = run.today.minusDays(run.rangeDays - 1L)
        val dates = (0 until run.rangeDays).map { windowStart.plusDays(it.toLong()) }
        val counts = refresh(run, dates, refreshSeries = true)
        env.tokens.write(SyncToken(env.source.createChangesToken(), run.rangeDays))
        return SyncResult("full", counts, elapsed(run))
    }

    private suspend fun incremental(run: SyncRun, stored: SyncToken): SyncResult {
        var nextToken = stored.value
        val affected = mutableSetOf<java.time.LocalDate>()
        val deleted = mutableListOf<String>()
        val upsertedExercises = mutableListOf<String>()
        var expired = false
        while (true) {
            val page = env.source.readChanges(nextToken)
            if (page.tokenExpired) {
                expired = true
                break
            }
            env.logger.log(run.syncId, "changes_page", mapOf("changes" to page.changeCount, "durationMs" to elapsed(run)))
            affected += page.affectedDates
            deleted += page.deletedRecordIds
            upsertedExercises += page.upsertedExerciseIds
            nextToken = page.nextToken
            if (!page.hasMore) break
        }
        if (expired) return full(run)
        if (deleted.isNotEmpty()) {
            val knownExercises = dao.exerciseIdsIn(deleted)
            dao.deleteExercisesById(deleted)
            if ((deleted.filterNot { it in knownExercises }).isNotEmpty()) return full(run)
        }
        if (upsertedExercises.isNotEmpty()) dao.deleteExercisesById(upsertedExercises)
        affected += run.today
        val windowStart = run.today.minusDays(run.rangeDays - 1L)
        val dates = affected.filter { !it.isBefore(windowStart) && !it.isAfter(run.today) }.sorted()
        val counts = refresh(run, dates, refreshSeries = false)
        env.tokens.write(SyncToken(nextToken, run.rangeDays))
        return SyncResult("incremental", counts, elapsed(run))
    }

    private suspend fun refresh(run: SyncRun, dates: List<java.time.LocalDate>, refreshSeries: Boolean): RefreshCounts {
        val zone = run.zone
        var rangeStart = dates.first().minusDays(1).atStartOfDay(zone).toInstant()
        if (!run.historyGranted) {
            rangeStart = maxOf(rangeStart, Instant.now().minus(Duration.ofDays(DEFAULT_RANGE_DAYS.toLong())))
        }
        val rangeEnd = maxOf(dates.last().plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
        val aggregates = dates.map { env.source.aggregateDay(it, run.granted) }
        val payload = env.source.readRange(rangeStart, rangeEnd, run.granted)
        val summaries = DailySummaryMapper.buildSummaries(aggregates, payload, zone)
        dao.upsertDaily(summaries.map { DailySummaryMapper.toEntity(it, System.currentTimeMillis()) })

        if (refreshSeries) {
            dao.deleteHeartRateSamplesBetween(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
            dao.deleteOxygenSamplesBetween(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
            dao.deleteRestingHeartRateSamplesBetween(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
            dao.deleteWeightSamplesBetween(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
            dao.deleteBodyFatSamplesBetween(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
        }
        val heartRate = DailySummaryMapper.heartRateEntities(payload.heartRateSamples, zone)
        val oxygen = DailySummaryMapper.oxygenEntities(payload.oxygenSamples, zone)
        val resting = DailySummaryMapper.restingHeartRateEntities(payload.restingHeartRateSamples, zone)
        dao.insertHeartRateSamples(heartRate)
        dao.insertOxygenSamples(oxygen)
        dao.insertRestingHeartRateSamples(resting)
        val weights = DailySummaryMapper.weightEntities(payload.weights, zone)
        val bodyFats = DailySummaryMapper.bodyFatEntities(payload.bodyFats, zone)
        dao.insertWeightSamples(weights)
        dao.insertBodyFatSamples(bodyFats)

        val exercises = DailySummaryMapper.toExerciseEntities(payload.exercises).distinctBy { it.externalId }
        val existing = dao.exerciseExternalIds(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
        val fresh = exercises.filter { it.externalId !in existing }
        dao.insertExercises(fresh)
        return RefreshCounts(
            dates.size, fresh.size, heartRate.size, oxygen.size, resting.size,
            weights.size, bodyFats.size
        )
    }

    private fun elapsed(run: SyncRun): Long = System.currentTimeMillis() - run.startedAtMillis

    private class SyncRun(
        val syncId: String,
        val startedAtMillis: Long,
        val granted: Set<String>,
        val rangeDays: Int,
        val historyGranted: Boolean,
        val zone: ZoneId
    ) {
        val today: java.time.LocalDate = java.time.LocalDate.now(zone)
    }

    companion object {
        const val DEFAULT_RANGE_DAYS = 30
        const val HISTORY_RANGE_DAYS = 365
    }
}
