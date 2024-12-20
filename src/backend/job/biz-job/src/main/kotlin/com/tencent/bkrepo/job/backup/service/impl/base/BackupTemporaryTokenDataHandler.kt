package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.backup.pojo.query.common.BackupTemporaryToken
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

@Component
class BackupTemporaryTokenDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.TEMPORARY_TOKEN_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        return Criteria()
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupTemporaryToken).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupTemporaryToken
        record.id = null
        mongoTemplate.save(record, BackupDataEnum.TEMPORARY_TOKEN_DATA.collectionName)
        logger.info("Create token ${record.id} success!")
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BackupTemporaryTokenDataHandler::class.java)
    }
}