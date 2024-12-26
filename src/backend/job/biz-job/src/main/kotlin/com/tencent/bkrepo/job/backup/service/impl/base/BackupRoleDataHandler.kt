package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.backup.pojo.query.common.BackupRole
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
class BackupRoleDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.ROLE_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        return Criteria()
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupRole).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupRole
        val existRecord = findExistRole(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistRole(record)
        } else {
            mongoTemplate.save(record, BackupDataEnum.ROLE_DATA.collectionName)
            logger.info("Create role ${record.name} success!")
        }
    }

    private fun findExistRole(record: BackupRole): BackupRole? {
        val existRoleQuery = buildQuery(record)
        return mongoTemplate.findOne(existRoleQuery, BackupRole::class.java, BackupDataEnum.ROLE_DATA.collectionName)
    }

    private fun updateExistRole(roleInfo: BackupRole) {
        val roleQuery = buildQuery(roleInfo)
        val update = Update()
            .set(BackupRole::name.name, roleInfo.name)
            .set(BackupRole::admin.name, roleInfo.admin)
            .set(BackupRole::description.name, roleInfo.description)
        mongoTemplate.updateFirst(roleQuery, update, BackupDataEnum.ROLE_DATA.collectionName)
        logger.info("update exist role success with name ${roleInfo.name}")
    }

    private fun buildQuery(roleInfo: BackupRole): Query {
        return Query(
            Criteria.where(BackupRole::roleId.name).isEqualTo(roleInfo.roleId)
                .and(BackupRole::projectId.name).isEqualTo(roleInfo.projectId)
                .and(BackupRole::repoName.name).isEqualTo(roleInfo.repoName)
                .and(BackupRole::type.name).isEqualTo(roleInfo.type)
                .and(BackupRole::source.name).isEqualTo(roleInfo.source)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupRoleDataHandler::class.java)
    }
}