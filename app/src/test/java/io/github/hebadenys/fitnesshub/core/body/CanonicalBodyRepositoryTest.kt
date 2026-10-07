package io.github.hebadenys.fitnesshub.core.body

import android.app.Application
import androidx.room.Room
import io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.SourceIdentityEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiBindingEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiSnapshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CanonicalBodyRepositoryTest {
    private lateinit var db: HealthDatabase
    private lateinit var repository: CanonicalBodyRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            HealthDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = CanonicalBodyRepository(
            db.healthDao(), db.scaleDao(), db.xiaomiArchiveDao(),
            CanonicalBodyMetricResolver(), ZoneId.of("UTC")
        )
    }

    @After fun close() { db.close() }

    @Test fun newerHealthConnectDayBeatsOlderXiaomiRegardlessOfSourcePriority() = runBlocking {
        db.healthDao().upsertDaily(listOf(DailyHealthEntity(date = "2026-10-10", weightKg = 95.0)))
        insertXiaomiSnapshot("old", "event-old", "2026-10-01T10:00:00Z", 100.0)

        val data = repository.observe().first()

        assertEquals(95.0, data.latestWeight!!.observation.value, 0.001)
        assertEquals(CanonicalBodyMetricResolver.Source.HEALTH_CONNECT, data.latestWeight!!.observation.source)
    }

    @Test fun twoScaleWeighInsOnSameDayRemainOriginalEventsAndEveningIsDailyPoint() = runBlocking {
        val morning = Instant.parse("2026-10-06T08:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-10-06T18:00:00Z").toEpochMilli()
        db.scaleDao().insertMeasurement(ScaleMeasurementEntity(
            deviceAddress = "fixture-scale", measuredAtMillis = morning, weightKg = 100.0,
            impedanceOhms = null, heartRateBpm = null, profileSlot = null
        ))
        db.scaleDao().insertMeasurement(ScaleMeasurementEntity(
            deviceAddress = "fixture-scale", measuredAtMillis = evening, weightKg = 99.4,
            impedanceOhms = null, heartRateBpm = null, profileSlot = null
        ))

        val data = repository.observe().first()

        assertEquals(2, data.weightTimeline.size)
        assertEquals(99.4, data.latestWeight!!.observation.value, 0.001)
        assertEquals(99.4, data.days.single().weight!!.value, 0.001)
        assertEquals(2, db.scaleDao().dumpAll().size)
    }

    @Test fun sameDayExactScaleReadingWinsOverDayPrecisionHealthCacheWithoutDeletingEither() = runBlocking {
        db.healthDao().upsertDaily(listOf(DailyHealthEntity(date = "2026-10-06", weightKg = 100.2)))
        db.scaleDao().insertMeasurement(ScaleMeasurementEntity(
            deviceAddress = "fixture-scale",
            measuredAtMillis = Instant.parse("2026-10-06T18:00:00Z").toEpochMilli(),
            weightKg = 99.8, impedanceOhms = null, heartRateBpm = null, profileSlot = null
        ))

        val data = repository.observe().first()

        assertEquals(99.8, data.latestWeight!!.observation.value, 0.001)
        assertEquals(2, data.weightTimeline.size)
        assertEquals(100.2, db.healthDao().dumpDaily().single().weightKg!!, 0.001)
        assertEquals(99.8, db.scaleDao().dumpAll().single().weightKg!!, 0.001)
    }

    @Test fun twoXiaomiSnapshotsForExplicitSameEventCollapseInViewButStayInArchive() = runBlocking {
        insertXiaomiSnapshot("version-a", "same-event", "2026-10-06T10:00:00Z", 100.0)
        insertXiaomiSnapshot("version-b", "same-event", "2026-10-06T10:00:00Z", 99.9)

        val data = repository.observe().first()

        assertEquals(1, data.weightTimeline.size)
        assertEquals(2, db.xiaomiArchiveDao().snapshotCount())
    }

    @Test fun bodyFatAndWeightKeepIndependentLatestDates() = runBlocking {
        db.healthDao().upsertDaily(listOf(
            DailyHealthEntity(date = "2026-10-05", bodyFatPercent = 20.0),
            DailyHealthEntity(date = "2026-10-07", weightKg = 98.0)
        ))

        val data = repository.observe().first()

        assertEquals("2026-10-07", data.latestWeight!!.observation.measuredAt.atZone(ZoneId.of("UTC")).toLocalDate().toString())
        assertEquals("2026-10-05", data.latestBodyFat!!.observation.measuredAt.atZone(ZoneId.of("UTC")).toLocalDate().toString())
    }

    private suspend fun insertXiaomiSnapshot(hash: String, eventKey: String, time: String, weight: Double) {
        if (db.xiaomiArchiveDao().identity() == null) {
            db.xiaomiArchiveDao().insertIdentity(SourceIdentityEntity(profileId = "local", createdAtMillis = 1))
            db.xiaomiArchiveDao().insertBinding(XiaomiBindingEntity(
                connectionId = "fixture-connection", ownerId = 1, region = "de",
                model = "yunmai.scales.ms103", loginUid = "10001", subjectUid = "10001",
                subjectAccountId = "11", deviceId = "fixture-scale", confirmedAtMillis = 1
            ))
        }
        val millis = Instant.parse(time).toEpochMilli()
        val json = """{"metrics":{"weight":{"value":$weight,"unit":"KG","method":"VENDOR_REPORTED_WEIGHT"}}}"""
        db.xiaomiArchiveDao().insertSnapshot(XiaomiSnapshotEntity(
            connectionId = "fixture-connection", contentHash = hash, eventKey = eventKey,
            createTimeMillis = millis, measuredAtMillis = millis, receivedAtMillis = millis,
            parserVersion = "fixture", snapshotJson = json
        ))
    }
}
