package io.github.hebadenys.fitnesshub.core.backup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class BackupDocumentIoTest {
    @Test fun exactLimitIsReadable() { assertEquals("abcd", readLimitedBackupText(StringReader("abcd"), 4)) }
    @Test fun emptyDocumentIsReadable() { assertEquals("", readLimitedBackupText(StringReader(""), 0)) }
    @Test fun oversizeDocumentFailsWithoutIncludingItsContent() {
        val error = assertThrows(DatabaseBackupService.ArchiveException::class.java) { readLimitedBackupText(StringReader("private_fixture"), 2) }
        assertEquals("archive_too_large", error.reason)
        assertFalse(error.toString().contains("private_fixture"))
    }
}
