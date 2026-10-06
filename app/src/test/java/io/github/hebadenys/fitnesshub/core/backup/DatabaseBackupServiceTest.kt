package io.github.hebadenys.fitnesshub.core.backup

import android.app.Application
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.database.databaseRows
import io.github.hebadenys.fitnesshub.core.database.seedLegacyTables
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.RoomXiaomiArchive
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiRegion
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiScaleProtocol
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiScope
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiSubject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseBackupServiceTest {
    private lateinit var source: HealthDatabase
    private lateinit var target: HealthDatabase
    private fun password() = "synthetic-test-passphrase".toCharArray()
    @Before fun setUp() { source = testDatabase(); target = testDatabase() }
    @After fun tearDown() { source.close(); target.close() }

    private suspend fun seedCloud(db: HealthDatabase, subjectId: String = "10") {
        val scope = XiaomiScope("fixture-connection", XiaomiRegion.DE, "yunmai.scales.ms104", "10001")
        val clock = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC)
        val store = RoomXiaomiArchive(db, clock)
        store.confirmBinding(scope, XiaomiSubject("10001", subjectId), "fixture-scale")
        val ticket = store.startOrResume(scope, clock.millis())
        val data = """[{"model":"yunmai.scales.ms104","uid":10001,"accountId":$subjectId,"did":"fixture-scale","createTime":1767254400000,"fromSource":1,"data":{"weight":75.5,"bfp":22.5,"vendorExtension":7}}]"""
        store.committer(ticket).commit(scope, ticket.cursor, XiaomiScaleProtocol(clock).parse(data, scope, ticket.cursor))
    }

    @Test fun allDomainColumnsAndRelationsSurviveEncryptedRoundTrip() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        seedCloud(source)
        val expected = databaseRows(source.openHelper.writableDatabase)
        assertTrue(expected.values.all { it.isNotEmpty() })
        val key = password()
        val encoded = DatabaseBackupService(source).createBackup(key)
        assertTrue(key.all { it == '\u0000' })
        val restoredKey = password()
        val result = DatabaseBackupService(target).restoreBackup(encoded, restoredKey)
        assertTrue(restoredKey.all { it == '\u0000' })
        assertEquals(expected.values.sumOf { it.size }, result.totalRows)
        assertFalse(result.legacyPartial)
        assertEquals(expected, databaseRows(target.openHelper.writableDatabase))
        assertNull(target.xiaomiArchiveDao().checkpoint("fixture-connection"))
        val repeated = DatabaseBackupService(target).restoreBackup(encoded, password())
        assertEquals(0, repeated.totalRows)
        assertEquals(result.totalRows, repeated.identicalRows)
        assertEquals(expected, databaseRows(target.openHelper.writableDatabase))
    }

    @Test @Config(sdk = [35]) fun currentAndroidUsesTheSameCompleteSnapshotPath() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        val encoded = DatabaseBackupService(source).createBackup(password())
        DatabaseBackupService(target).restoreBackup(encoded, password())
        assertEquals(databaseRows(source.openHelper.writableDatabase), databaseRows(target.openHelper.writableDatabase))
    }

    @Test fun latePrimaryKeyConflictRollsBackEarlierInsertedTables() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        target.openHelper.writableDatabase.execSQL("INSERT INTO workout_templates (id,name,createdAt) VALUES (1,'different synthetic template',1)")
        val previous = databaseRows(target.openHelper.writableDatabase)
        val encoded = DatabaseBackupService(source).createBackup(password())
        expect("backup_conflict") { DatabaseBackupService(target).restoreBackup(encoded, password()) }
        assertEquals(previous, databaseRows(target.openHelper.writableDatabase))
    }

    @Test fun differentBoundOwnerCannotBeSilentlyMerged() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        seedCloud(source)
        seedCloud(target, "11")
        val previous = databaseRows(target.openHelper.writableDatabase)
        val encoded = DatabaseBackupService(source).createBackup(password())
        expect("backup_conflict") { DatabaseBackupService(target).restoreBackup(encoded, password()) }
        assertEquals(previous, databaseRows(target.openHelper.writableDatabase))
    }

    @Test fun malformedLaterTableIsRejectedBeforeWriting() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        val root = decode(DatabaseBackupService(source).createBackup(password()))
        val tables = root.getJSONArray("tables")
        tables.getJSONObject(15).getJSONArray("rows").getJSONArray(0).put(0, "not an integer")
        expect("invalid_integer") { DatabaseBackupService(target).restoreBackup(encode(root), password()) }
        assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
    }

    @Test fun unknownTablesAndMissingTablesAreNotSilentlyIgnored() = runBlocking {
        val root = decode(DatabaseBackupService(source).createBackup(password()))
        root.getJSONArray("tables").getJSONObject(0).put("name", "daily_health`; DROP TABLE food_items; --")
        expect("invalid_table") { DatabaseBackupService(target).restoreBackup(encode(root), password()) }
        assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
        root.getJSONArray("tables").remove(0)
        expect("incomplete_archive") { DatabaseBackupService(target).restoreBackup(encode(root), password()) }
    }

    @Test fun crossVersionArchiveIsRejectedWithoutChangingData() = runBlocking {
        val root = decode(DatabaseBackupService(source).createBackup(password())).put("databaseVersion", 1000)
        expect("incompatible_database_version") { DatabaseBackupService(target).restoreBackup(encode(root), password()) }
        assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
    }

    @Test fun wrongPassphraseAndTamperingDoNotWriteAndWipeInputKey() = runBlocking {
        seedLegacyTables(source.openHelper.writableDatabase)
        val encoded = DatabaseBackupService(source).createBackup(password())
        val key = "wrong synthetic password".toCharArray()
        try { DatabaseBackupService(target).restoreBackup(encoded, key); fail("Expected authentication failure") }
        catch (_: BackupCrypto.InvalidBackupException) { }
        assertTrue(key.all { it == '\u0000' })
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        try { DatabaseBackupService(target).restoreBackup(java.util.Base64.getEncoder().encodeToString(bytes), password()); fail("Expected tamper failure") }
        catch (_: BackupCrypto.InvalidBackupException) { }
        assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
    }

    @Test fun newUnregisteredTableMakesBackupFailRatherThanLoseItsData() = runBlocking {
        source.openHelper.writableDatabase.execSQL("CREATE TABLE unregistered_feature (id INTEGER PRIMARY KEY)")
        expect("unregistered_database_table") { DatabaseBackupService(source).createBackup(password()) }
    }

    @Test fun legacyArchiveRemainsExplicitlyPartial() = runBlocking {
        val root = JSONObject("""{"payloadVersion":1,"dailyHealth":[{"date":"2026-01-02","weightKg":75.5,"syncedAt":1}]}""")
        val result = DatabaseBackupService(target).restoreBackup(encode(root), password())
        assertTrue(result.legacyPartial)
        assertEquals(1, result.totalRows)
        assertEquals(75.5, target.healthDao().dumpDaily().single().weightKg!!, 0.0)
    }

    private fun decode(encoded: String) = JSONObject(String(BackupCrypto.decryptFromBase64(encoded, password()), Charsets.UTF_8))
    private fun encode(root: JSONObject) = BackupCrypto.encryptToBase64(root.toString().toByteArray(Charsets.UTF_8), password())
    private suspend fun expect(reason: String, block: suspend () -> Unit) {
        try { block(); fail("Expected archive failure") } catch (error: DatabaseBackupService.ArchiveException) { assertEquals(reason, error.reason) }
    }
}
