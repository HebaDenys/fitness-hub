package io.github.hebadenys.fitnesshub.core.backup

import android.app.Application
import io.github.hebadenys.fitnesshub.core.database.databaseRows
import io.github.hebadenys.fitnesshub.core.database.seedLegacyTables
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LegacyBackupSafetyTest {
    @Test fun oldPartialRestoreCannotOverwriteExistingHealthArchive() = runBlocking {
        val database = testDatabase()
        try {
            seedLegacyTables(database.openHelper.writableDatabase)
            val before = databaseRows(database.openHelper.writableDatabase)
            val text = """{"payloadVersion":1,"dailyHealth":[{"date":"2026-01-02","weightKg":99.5,"syncedAt":1}]}"""
            val encoded = BackupCrypto.encryptToBase64(text.toByteArray(Charsets.UTF_8), "fixture".toCharArray())
            try { DatabaseBackupService(database).restoreBackup(encoded, "fixture".toCharArray()); fail("Expected refusal") }
            catch (error: DatabaseBackupService.ArchiveException) { assertEquals("legacy_requires_empty_archive", error.reason) }
            assertEquals(before, databaseRows(database.openHelper.writableDatabase))
        } finally { database.close() }
    }

    @Test fun sqliteStatisticsAreNotMistakenForAnUnregisteredDomainTable() = runBlocking {
        val database = testDatabase()
        try {
            seedLegacyTables(database.openHelper.writableDatabase)
            database.openHelper.writableDatabase.execSQL("ANALYZE")
            val encoded = DatabaseBackupService(database).createBackup("fixture".toCharArray())
            assertTrue(encoded.isNotEmpty())
        } finally { database.close() }
    }
}
