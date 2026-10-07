package io.github.hebadenys.fitnesshub.core.backup

import android.app.Application
import io.github.hebadenys.fitnesshub.core.database.databaseRows
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
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
class DatabaseBackupIdentityTest {
    @Test fun forgedOwnerInsideSnapshotRollsBackWholeRestore() = runBlocking {
        val source = testDatabase()
        val target = testDatabase()
        try {
            val scope = XiaomiScope("synthetic", XiaomiRegion.DE, "yunmai.scales.ms104", "10001")
            val clock = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC)
            val archive = RoomXiaomiArchive(source, clock)
            archive.confirmBinding(scope, XiaomiSubject("10001", "10"), "synthetic-device")
            val ticket = archive.startOrResume(scope, clock.millis())
            val response = """[{"model":"yunmai.scales.ms104","uid":10001,"accountId":10,"did":"synthetic-device","createTime":1767254400000,"fromSource":1,"data":{"weight":75.5}}]"""
            archive.committer(ticket).commit(scope, ticket.cursor, XiaomiScaleProtocol(clock).parse(response, scope, ticket.cursor))
            val encoded = DatabaseBackupService(source).createBackup("test-only".toCharArray())
            val root = JSONObject(String(BackupCrypto.decryptFromBase64(encoded, "test-only".toCharArray()), Charsets.UTF_8))
            val tables = root.getJSONArray("tables")
            val snapshots = (0 until tables.length())
                .map { tables.getJSONObject(it) }
                .single { it.getString("name") == "xiaomi_snapshots" }
            val columns = snapshots.getJSONArray("columns")
            val payloadIndex = (0 until columns.length()).single { columns.getString(it) == "snapshotJson" }
            val row = snapshots.getJSONArray("rows").getJSONArray(0)
            row.put(payloadIndex, row.getString(payloadIndex).replace("\"accountId\":\"10\"", "\"accountId\":\"11\""))
            val tampered = BackupCrypto.encryptToBase64(root.toString().toByteArray(Charsets.UTF_8), "test-only".toCharArray())
            try { DatabaseBackupService(target).restoreBackup(tampered, "test-only".toCharArray()); fail("Expected rejection") }
            catch (error: DatabaseBackupService.ArchiveException) { assertEquals("invalid_or_incompatible_backup", error.reason) }
            assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
        } finally { source.close(); target.close() }
    }
}
