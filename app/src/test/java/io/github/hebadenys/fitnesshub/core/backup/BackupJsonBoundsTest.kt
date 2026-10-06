package io.github.hebadenys.fitnesshub.core.backup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BackupJsonBoundsTest {
    @Test fun bracketsInsideStringsDoNotCountAsNesting() {
        checkBackupJsonBounds("""{"data":"[[[[{\\\"}"}""")
    }
    @Test fun nestingBombIsRejectedBeforeJsonDecoding() {
        assertThrows(DatabaseBackupService.ArchiveException::class.java) { checkBackupJsonBounds("[".repeat(33) + "]".repeat(33)) }
    }
    @Test fun unterminatedStringIsRejected() {
        assertThrows(DatabaseBackupService.ArchiveException::class.java) { checkBackupJsonBounds("\"unfinished") }
    }
}
