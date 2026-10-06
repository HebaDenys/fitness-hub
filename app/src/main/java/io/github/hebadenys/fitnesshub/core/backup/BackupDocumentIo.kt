package io.github.hebadenys.fitnesshub.core.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Reader

/** Called by the document picker, with bounded reads and no I/O on the Compose thread. */
internal class BackupDocumentIo(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            readLimitedBackupText(it, DatabaseBackupService.MAX_ENCODED_CHARACTERS)
        } ?: throw java.io.IOException("Document could not be opened")
    }
    suspend fun write(uri: Uri, value: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(value) }
            ?: throw java.io.IOException("Document could not be written")
    }
}

internal fun readLimitedBackupText(reader: Reader, limit: Int): String {
    require(limit >= 0)
    val output = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val read = reader.read(buffer)
        if (read < 0) return output.toString()
        if (output.length.toLong() + read > limit) throw DatabaseBackupService.ArchiveException("archive_too_large")
        output.append(buffer, 0, read)
    }
}
