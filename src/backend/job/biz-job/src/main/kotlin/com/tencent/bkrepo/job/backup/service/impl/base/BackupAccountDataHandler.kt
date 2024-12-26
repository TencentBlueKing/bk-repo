package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.backup.pojo.query.common.BackupAccount
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupConflictStrategy
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class BackupAccountDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.ACCOUNT_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria()
        if (context.incrementDate != null) {
            criteria.and(BackupAccount::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupAccount).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupAccount
        val existRecord = findExistAccount(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistAccount(record)
        } else {
            mongoTemplate.save(record, BackupDataEnum.ACCOUNT_DATA.collectionName)
            logger.info("Create account ${record.appId} success!")
        }
    }

    private fun findExistAccount(record: BackupAccount): BackupAccount? {
        val existAccountQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existAccountQuery,
            BackupAccount::class.java,
            BackupDataEnum.ACCOUNT_DATA.collectionName
        )
    }

    private fun updateExistAccount(accountInfo: BackupAccount) {
        val accountQuery = buildQuery(accountInfo)
        val update = Update()
            .addToSet(BackupAccount::credentials.name, accountInfo.credentials)
            .addToSet(BackupAccount::authorizationGrantTypes.name, accountInfo.authorizationGrantTypes)
            .addToSet(BackupAccount::scope.name, accountInfo.scope)
            .addToSet(BackupAccount::scopeDesc.name, accountInfo.scopeDesc)
            .set(BackupAccount::homepageUrl.name, accountInfo.homepageUrl)
            .set(BackupAccount::redirectUri.name, accountInfo.redirectUri)
            .set(BackupAccount::avatarUrl.name, accountInfo.avatarUrl)
            .set(BackupAccount::locked.name, accountInfo.locked)
            .set(BackupAccount::description.name, accountInfo.description)

        val updateResult = mongoTemplate.updateFirst(accountQuery, update, BackupDataEnum.ACCOUNT_DATA.collectionName)
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist account failed with id ${accountInfo.appId} ")
        } else {
            logger.info("update exist account success with id ${accountInfo.appId}")
        }
    }

    private fun buildQuery(accountInfo: BackupAccount): Query {
        return Query.query(Criteria.where(BackupAccount::appId.name).`is`(accountInfo.appId))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupAccountDataHandler::class.java)
    }
}