package io.github.hebadenys.fitnesshub.core.backup

import android.app.Application
import io.github.hebadenys.fitnesshub.core.database.databaseRows
import io.github.hebadenys.fitnesshub.core.database.seedLegacyTables
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupRelationshipsTest {
    @Test fun orphanInLegacyTableWithoutForeignKeyRejectsEntireRestore() = runBlocking {
        val source = testDatabase()
        val target = testDatabase()
        try {
            seedLegacyTables(source.openHelper.writableDatabase)
            val original = DatabaseBackupService(source).createBackup("fixture".toCharArray())
            val root = JSONObject(String(BackupCrypto.decryptFromBase64(original, "fixture".toCharArray()), Charsets.UTF_8))
            val tables = root.getJSONArray("tables")
            val entry = (0 until tables.length()).map { tables.getJSONObject(it) }.single { it.getString("name") == "nutrition_entries" }
            val columns = entry.getJSONArray("columns")
            val foodIndex = (0 until columns.length()).single { columns.getString(it) == "foodId" }
            entry.getJSONArray("rows").getJSONArray(0).put(foodIndex, 9999)
            val broken = BackupCrypto.encryptToBase64(root.toString().toByteArray(Charsets.UTF_8), "fixture".toCharArray())
            try { DatabaseBackupService(target).restoreBackup(broken, "fixture".toCharArray()); fail("Expected orphan rejection") }
            catch (error: DatabaseBackupService.ArchiveException) { assertEquals("invalid_relationship", error.reason) }
            assertTrue(databaseRows(target.openHelper.writableDatabase).values.all { it.isEmpty() })
        } finally { source.close(); target.close() }
    }
}
