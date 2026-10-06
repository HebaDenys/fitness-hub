package io.github.hebadenys.fitnesshub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: legacy health, nutrition, scale and workout rows are left untouched. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `source_identity` (`id` INTEGER NOT NULL, `profileId` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `xiaomi_bindings` (`connectionId` TEXT NOT NULL, `ownerId` INTEGER NOT NULL, `region` TEXT NOT NULL, `model` TEXT NOT NULL, `loginUid` TEXT NOT NULL, `subjectUid` TEXT NOT NULL, `subjectAccountId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `confirmedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`connectionId`), FOREIGN KEY(`ownerId`) REFERENCES `source_identity`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_xiaomi_bindings_ownerId` ON `xiaomi_bindings` (`ownerId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `xiaomi_snapshots` (`connectionId` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `eventKey` TEXT NOT NULL, `createTimeMillis` INTEGER NOT NULL, `measuredAtMillis` INTEGER, `receivedAtMillis` INTEGER NOT NULL, `parserVersion` TEXT NOT NULL, `acquisitionMethod` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, PRIMARY KEY(`connectionId`, `contentHash`), FOREIGN KEY(`connectionId`) REFERENCES `xiaomi_bindings`(`connectionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_xiaomi_snapshots_connectionId_measuredAtMillis` ON `xiaomi_snapshots` (`connectionId`, `measuredAtMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_xiaomi_snapshots_connectionId_eventKey` ON `xiaomi_snapshots` (`connectionId`, `eventKey`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `xiaomi_checkpoints` (`connectionId` TEXT NOT NULL, `generation` TEXT NOT NULL, `initialBeforeMillis` INTEGER NOT NULL, `nextBeforeMillis` INTEGER, `lastRequestedBeforeMillis` INTEGER, `lastPageHash` TEXT, `committedPages` INTEGER NOT NULL, `selectedRows` INTEGER NOT NULL, `otherRows` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`connectionId`), FOREIGN KEY(`connectionId`) REFERENCES `xiaomi_bindings`(`connectionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
    }
}
