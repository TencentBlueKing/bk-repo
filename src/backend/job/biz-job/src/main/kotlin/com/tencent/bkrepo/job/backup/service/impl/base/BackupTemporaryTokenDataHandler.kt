package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.backup.pojo.query.common.BackupTemporaryToken
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class BackupTemporaryTokenDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.TEMPORARY_TOKEN_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria()
        if (context.incrementDate != null) {
            criteria.and(BackupTemporaryToken::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupTemporaryToken).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupTemporaryToken
        val existedRecord = findExistTemporaryToken(record)
        if (existedRecord == null) {
            mongoTemplate.save(record, BackupDataEnum.TEMPORARY_TOKEN_DATA.collectionName)
            logger.info("Create token ${record.id} success!")
        }
    }


    private fun findExistTemporaryToken(record: BackupTemporaryToken): BackupTemporaryToken? {
        val existTokenQuery = Query(Criteria.where(BackupTemporaryToken::id.name).isEqualTo(record.id))
        return mongoTemplate.findOne(
            existTokenQuery,
            BackupTemporaryToken::class.java,
            BackupDataEnum.TEMPORARY_TOKEN_DATA.collectionName
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupTemporaryTokenDataHandler::class.java)
    }
}