package com.tencent.bkrepo.job.backup.service

import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import org.springframework.data.mongodb.core.query.Criteria
import java.nio.file.Paths

interface BackupDataHandler {

    fun dataType(): BackupDataEnum

    fun buildQueryCriteria(context: BackupContext): Criteria

    fun getCollectionName(backupDataEnum: BackupDataEnum, context: BackupContext): String {
        return backupDataEnum.collectionName
    }

    fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {}

    fun postBackupDataHandler(context: BackupContext) {}

    fun <T> returnLastId(data: T): String

    fun preRestoreDataHandler(backupDataEnum: BackupDataEnum, context: BackupContext) {
        context.currentFile = Paths.get(context.currentPath.toString(), backupDataEnum.fileName).toString()
    }

    fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext)

    fun getSpecialDataEnum(context: BackupContext): List<BackupDataEnum>? {
        return null
    }
}