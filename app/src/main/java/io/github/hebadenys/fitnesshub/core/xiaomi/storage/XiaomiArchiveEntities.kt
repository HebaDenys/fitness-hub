package io.github.hebadenys.fitnesshub.core.xiaomi.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Never print health records or account identifiers via generated data-class toString(). */
abstract class PrivateArchiveValue {
    final override fun toString(): String = "${javaClass.simpleName}(<redacted>)"
}

@Entity(tableName = "source_identity")
data class SourceIdentityEntity(
    @PrimaryKey val id: Int = 1,
    val profileId: String,
    val createdAtMillis: Long
) : PrivateArchiveValue()

/** One explicitly selected Xiaomi subject/device per installation in this first increment. */
@Entity(
    tableName = "xiaomi_bindings",
    foreignKeys = [ForeignKey(entity = SourceIdentityEntity::class, parentColumns = ["id"], childColumns = ["ownerId"])],
    indices = [Index(value = ["ownerId"], unique = true)]
)
data class XiaomiBindingEntity(
    @PrimaryKey val connectionId: String,
    val ownerId: Int,
    val region: String,
    val model: String,
    val loginUid: String,
    val subjectUid: String,
    val subjectAccountId: String,
    val deviceId: String,
    val confirmedAtMillis: Long
) : PrivateArchiveValue()

/** Immutable source snapshots. eventKey is a candidate grouping, not a proven vendor revision. */
@Entity(
    tableName = "xiaomi_snapshots",
    primaryKeys = ["connectionId", "contentHash"],
    foreignKeys = [ForeignKey(entity = XiaomiBindingEntity::class, parentColumns = ["connectionId"], childColumns = ["connectionId"])],
    indices = [Index(value = ["connectionId", "measuredAtMillis"]), Index(value = ["connectionId", "eventKey"])]
)
data class XiaomiSnapshotEntity(
    val connectionId: String,
    val contentHash: String,
    val eventKey: String,
    val createTimeMillis: Long,
    val measuredAtMillis: Long?,
    val receivedAtMillis: Long,
    val parserVersion: String,
    val acquisitionMethod: String = "XIAOMI_CLOUD",
    val snapshotJson: String
) : PrivateArchiveValue()

@Entity(
    tableName = "xiaomi_checkpoints",
    foreignKeys = [ForeignKey(entity = XiaomiBindingEntity::class, parentColumns = ["connectionId"], childColumns = ["connectionId"])]
)
data class XiaomiCheckpointEntity(
    @PrimaryKey val connectionId: String,
    val generation: String,
    val initialBeforeMillis: Long,
    val nextBeforeMillis: Long?,
    val lastRequestedBeforeMillis: Long?,
    val lastPageHash: String?,
    val committedPages: Long,
    val selectedRows: Long,
    val otherRows: Long,
    val updatedAtMillis: Long
) : PrivateArchiveValue()

data class XiaomiArchiveRange(val oldest: Long?, val newest: Long?) : PrivateArchiveValue()

@Dao
interface XiaomiArchiveDao {
    @Query("SELECT * FROM source_identity WHERE id = 1")
    suspend fun identity(): SourceIdentityEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdentity(value: SourceIdentityEntity)
    @Query("SELECT * FROM xiaomi_bindings WHERE ownerId = 1")
    suspend fun binding(): XiaomiBindingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBinding(value: XiaomiBindingEntity)
    @Query("SELECT * FROM xiaomi_checkpoints WHERE connectionId = :connectionId")
    suspend fun checkpoint(connectionId: String): XiaomiCheckpointEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(value: XiaomiCheckpointEntity)
    @Query("DELETE FROM xiaomi_checkpoints")
    suspend fun resetCheckpoints()
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshot(value: XiaomiSnapshotEntity): Long
    @Query("SELECT * FROM xiaomi_snapshots WHERE connectionId = :connectionId AND contentHash = :hash")
    suspend fun snapshot(connectionId: String, hash: String): XiaomiSnapshotEntity?
    @Query("SELECT * FROM xiaomi_snapshots WHERE connectionId = :connectionId ORDER BY createTimeMillis, contentHash")
    suspend fun snapshots(connectionId: String): List<XiaomiSnapshotEntity>
    @Query("SELECT COUNT(*) FROM xiaomi_snapshots")
    suspend fun snapshotCount(): Int
    @Query("SELECT * FROM xiaomi_snapshots WHERE connectionId = :connectionId ORDER BY createTimeMillis DESC, contentHash LIMIT :limit OFFSET :offset")
    suspend fun snapshotPage(connectionId: String, limit: Int, offset: Int): List<XiaomiSnapshotEntity>
    @Query("SELECT MIN(measuredAtMillis) AS oldest, MAX(measuredAtMillis) AS newest FROM xiaomi_snapshots WHERE connectionId = :connectionId")
    suspend fun range(connectionId: String): XiaomiArchiveRange
}
