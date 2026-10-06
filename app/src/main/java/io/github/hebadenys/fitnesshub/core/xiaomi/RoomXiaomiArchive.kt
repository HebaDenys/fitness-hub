package io.github.hebadenys.fitnesshub.core.xiaomi

import androidx.room.withTransaction
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.SourceIdentityEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiBindingEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiCheckpointEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Clock
import java.util.UUID

internal enum class ArchiveFailure {
    BINDING_REQUIRED, BINDING_CONFLICT, INVALID_IDENTITY, AMBIGUOUS_IDENTITY,
    SCOPE_MISMATCH, INVALID_PAGE, STALE_GENERATION, STALE_CURSOR, HASH_CONFLICT
}
internal class XiaomiArchiveException(val reason: ArchiveFailure) : Exception(reason.name)
internal data class XiaomiImportTicket(val scope: XiaomiScope, val generation: String, val cursor: Long) : PrivateXiaomiValue()

/**
 * Durable implementation of XiaomiPageCommitter. Network is always outside transactions.
 * This first binding is deliberately immutable: changing person/account/device requires a later
 * explicit migration flow. A new connection ID is NOT a way to silently switch the archive owner.
 */
internal class RoomXiaomiArchive(private val database: HealthDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao get() = database.xiaomiArchiveDao()

    suspend fun confirmBinding(scope: XiaomiScope, subject: XiaomiSubject, deviceId: String) = database.withTransaction {
        if (!validId(subject.uid) || !validId(subject.accountId) || deviceId.isBlank() ||
            deviceId.length > 128 || deviceId.any { it < ' ' }) fail(ArchiveFailure.INVALID_IDENTITY)
        val existing = dao.binding()
        if (existing != null) {
            if (!matches(existing, scope) || existing.subjectUid != subject.uid ||
                existing.subjectAccountId != subject.accountId || existing.deviceId != deviceId) {
                fail(ArchiveFailure.BINDING_CONFLICT)
            }
            return@withTransaction existing
        }
        if (dao.identity() == null) dao.insertIdentity(SourceIdentityEntity(profileId = UUID.randomUUID().toString(), createdAtMillis = clock.millis()))
        val binding = XiaomiBindingEntity(scope.connectionId, 1, scope.region.wireName, scope.model,
            scope.loginUid, subject.uid, subject.accountId, deviceId, clock.millis())
        dao.insertBinding(binding)
        binding
    }

    /** Resume an incomplete pass; only a completed pass starts a new generation. */
    suspend fun startOrResume(scope: XiaomiScope, beforeMillis: Long): XiaomiImportTicket = database.withTransaction {
        XiaomiScaleRequest.history(scope, beforeMillis) // validate cursor without network
        val binding = dao.binding() ?: fail(ArchiveFailure.BINDING_REQUIRED)
        if (!matches(binding, scope)) fail(ArchiveFailure.SCOPE_MISMATCH)
        val previous = dao.checkpoint(scope.connectionId)
        if (previous?.nextBeforeMillis != null) {
            return@withTransaction XiaomiImportTicket(scope, previous.generation, previous.nextBeforeMillis)
        }
        val generation = UUID.randomUUID().toString()
        dao.saveCheckpoint(XiaomiCheckpointEntity(scope.connectionId, generation, beforeMillis, beforeMillis,
            null, null, 0, 0, 0, clock.millis()))
        XiaomiImportTicket(scope, generation, beforeMillis)
    }

    fun committer(ticket: XiaomiImportTicket): XiaomiPageCommitter = XiaomiPageCommitter { scope, before, page ->
        commit(ticket, scope, before, page)
    }

    suspend fun read(source: XiaomiPageSource, scope: XiaomiScope, beforeMillis: Long, maxPages: Int = 100): XiaomiHistoryResult {
        val ticket = startOrResume(scope, beforeMillis)
        return XiaomiHistoryReader(source, committer(ticket), XiaomiScaleProtocol(clock)).read(scope, ticket.cursor, maxPages)
    }

    private suspend fun commit(ticket: XiaomiImportTicket, scope: XiaomiScope, before: Long, page: XiaomiScalePage) {
        if (ticket.scope != scope) fail(ArchiveFailure.SCOPE_MISMATCH)
        if (page.records.size > XiaomiScaleProtocol.PAGE_SIZE ||
            (page.nextBeforeMillis == null) != page.terminalByShortPage ||
            page.nextBeforeMillis?.let { it <= 0 || it >= before } == true ||
            page.records.any { it.scope != scope || it.createTimeMillis > before }) fail(ArchiveFailure.INVALID_PAGE)
        val digest = XiaomiSnapshotCodec.pageHash(scope, before, page)
        database.withTransaction {
            val binding = dao.binding() ?: fail(ArchiveFailure.BINDING_REQUIRED)
            if (!matches(binding, scope)) fail(ArchiveFailure.SCOPE_MISMATCH)
            val checkpoint = dao.checkpoint(scope.connectionId) ?: fail(ArchiveFailure.STALE_GENERATION)
            if (checkpoint.generation != ticket.generation) fail(ArchiveFailure.STALE_GENERATION)
            if (checkpoint.lastRequestedBeforeMillis == before && checkpoint.lastPageHash == digest) return@withTransaction
            if (checkpoint.nextBeforeMillis != before) fail(ArchiveFailure.STALE_CURSOR)
            var selected = 0L
            var other = 0L
            for (record in page.records) {
                currentCoroutineContext().ensureActive()
                if (record.model != binding.model) fail(ArchiveFailure.SCOPE_MISMATCH)
                // Unknown ownership/device stops the page: never silently advance past unreadable data.
                val subject = record.subject ?: fail(ArchiveFailure.AMBIGUOUS_IDENTITY)
                val device = record.deviceId ?: fail(ArchiveFailure.AMBIGUOUS_IDENTITY)
                if (subject.uid != binding.subjectUid || subject.accountId != binding.subjectAccountId || device != binding.deviceId) {
                    other++
                    continue
                }
                val snapshot = XiaomiSnapshotCodec.encode(record, clock.millis())
                if (dao.insertSnapshot(snapshot) == -1L &&
                    dao.snapshot(scope.connectionId, snapshot.contentHash)?.snapshotJson != snapshot.snapshotJson) {
                    fail(ArchiveFailure.HASH_CONFLICT)
                }
                selected++
            }
            currentCoroutineContext().ensureActive()
            dao.saveCheckpoint(checkpoint.copy(nextBeforeMillis = page.nextBeforeMillis,
                lastRequestedBeforeMillis = before, lastPageHash = digest,
                committedPages = checkpoint.committedPages + 1,
                selectedRows = checkpoint.selectedRows + selected, otherRows = checkpoint.otherRows + other,
                updatedAtMillis = clock.millis()))
        }
    }

    private fun matches(binding: XiaomiBindingEntity, scope: XiaomiScope) =
        binding.connectionId == scope.connectionId && binding.region == scope.region.wireName &&
            binding.model == scope.model && binding.loginUid == scope.loginUid
    private fun validId(value: String) = value.toLongOrNull()?.let { it >= 0 && it.toString() == value } == true
    private fun fail(reason: ArchiveFailure): Nothing = throw XiaomiArchiveException(reason)
}
