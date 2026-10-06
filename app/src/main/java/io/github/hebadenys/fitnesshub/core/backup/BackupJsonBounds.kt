package io.github.hebadenys.fitnesshub.core.backup

/** Bounds nesting before the general JSON decoder is called on a decrypted, user-supplied archive. */
internal fun checkBackupJsonBounds(input: String) {
    var depth = 0
    var quoted = false
    var escaped = false
    for (ch in input) {
        if (quoted) {
            when {
                escaped -> escaped = false
                ch == '\\' -> escaped = true
                ch == '"' -> quoted = false
            }
        } else {
            when (ch) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 32) throw DatabaseBackupService.ArchiveException("archive_nesting_limit")
                '}', ']' -> if (--depth < 0) throw DatabaseBackupService.ArchiveException("invalid_archive_json")
            }
        }
    }
    if (quoted || depth != 0) throw DatabaseBackupService.ArchiveException("invalid_archive_json")
}
