package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupUser
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupConflictStrategy
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class BackupUserDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.USER_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria()
        if (context.incrementDate != null) {
            criteria.and(BackupUser::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupUser).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupUser
        val existRecord = findExistUser(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistUser(record, existRecord)
        } else {
            mongoTemplate.save(record, BackupDataEnum.USER_DATA.collectionName)
            logger.info("Create user ${record.name} success!")
        }
    }

    private fun findExistUser(record: BackupUser): BackupUser? {
        val existUserQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existUserQuery,
            BackupUser::class.java,
            BackupDataEnum.USER_DATA.collectionName
        )
    }

    private fun updateExistUser(userInfo: BackupUser, existRecord: BackupUser) {
        // TODO 部分字段更新可能会导致原有数据不能使用
        val roleQuery = buildQuery(userInfo)
        val mergedRoles = merge(userInfo.roles, existRecord.roles)
        val mergedAsstUsers = merge(userInfo.asstUsers, existRecord.asstUsers)
        val mergedAccounts = merge(userInfo.accounts.orEmpty(), existRecord.accounts.orEmpty())
        val mergedTokens = mergeTokens(userInfo.tokens, existRecord.tokens)

        val update = Update()
            .set(BackupUser::name.name, userInfo.name)
            .set(BackupUser::admin.name, userInfo.admin)
            .set(BackupUser::pwd.name, userInfo.pwd)
            .set(BackupUser::locked.name, userInfo.locked)
            .set(BackupUser::group.name, userInfo.group)
            .set(BackupUser::pwd.name, userInfo.pwd)
            .set(BackupUser::email.name, userInfo.email)
            .set(BackupUser::pwd.name, userInfo.pwd)
            .set(BackupUser::phone.name, userInfo.phone)
            .set(BackupUser::source.name, userInfo.source)
            .set(BackupUser::tokens.name, mergedTokens)
            .set(BackupUser::roles.name, mergedRoles)
            .set(BackupUser::asstUsers.name, mergedAsstUsers)
            .set(BackupUser::accounts.name, mergedAccounts)
        mongoTemplate.updateFirst(roleQuery, update, BackupDataEnum.USER_DATA.collectionName)
        logger.info("update exist user success with name ${userInfo.userId}")
    }

    private fun buildQuery(userInfo: BackupUser): Query {
        return Query(Criteria.where(BackupUser::userId.name).isEqualTo(userInfo.userId))
    }

    fun mergeTokens(oldData: List<Token>, newData: List<Token>): MutableList<Token> {
        val result = mutableListOf<Token>()
        result.addAll(oldData)
        newData.forEach { newToken ->
            val existed = result.firstOrNull { it.id == newToken.id }
            if (existed == null) {
                result.add(newToken)
            }
        }
        return result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupUserDataHandler::class.java)

        fun merge(oldData: List<String>, newData: List<String>): MutableList<String> {
            val result = mutableSetOf<String>()
            result.addAll(oldData)
            result.addAll(newData)
            return result.toMutableList()
        }
    }
}