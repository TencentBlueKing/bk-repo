package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.backup.pojo.query.common.BackupPermission
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
class BackupPermissionDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.PERMISSION_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria()
        if (context.incrementDate != null) {
            criteria.and(BackupPermission::updateAt.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupPermission).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupPermission
        val existRecord = findExistPermission(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistPermission(record, existRecord)
        } else {
            mongoTemplate.save(record, BackupDataEnum.PERMISSION_DATA.collectionName)
            logger.info("Create permission ${record.permName} success!")
        }
    }

    private fun findExistPermission(record: BackupPermission): BackupPermission? {
        val existPermissionQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existPermissionQuery,
            BackupPermission::class.java,
            BackupDataEnum.PERMISSION_DATA.collectionName
        )
    }

    private fun updateExistPermission(permissionInfo: BackupPermission, existRecord: BackupPermission) {
        val permissionQuery = buildQuery(permissionInfo)
        val mergedIncludePattern = BackupUserDataHandler.merge(
            permissionInfo.includePattern, existRecord.includePattern
        )
        val mergedExcludePattern = BackupUserDataHandler.merge(
            permissionInfo.excludePattern, existRecord.excludePattern
        )
        val mergedUsers = BackupUserDataHandler.merge(permissionInfo.users, existRecord.users)
        val mergedRoles = BackupUserDataHandler.merge(permissionInfo.roles, existRecord.roles)
        val mergedDepartments = BackupUserDataHandler.merge(permissionInfo.departments, existRecord.departments)
        val mergedActions = BackupUserDataHandler.merge(permissionInfo.actions, existRecord.actions)

        val update = Update()
            .set(BackupPermission::includePattern.name, mergedIncludePattern)
            .set(BackupPermission::excludePattern.name, mergedExcludePattern)
            .set(BackupPermission::updatedBy.name, permissionInfo.updatedBy)
            .set(BackupPermission::users.name, mergedUsers)
            .set(BackupPermission::roles.name, mergedRoles)
            .set(BackupPermission::departments.name, mergedDepartments)
            .set(BackupPermission::actions.name, mergedActions)
        mongoTemplate.updateFirst(
            permissionQuery,
            update,
            BackupDataEnum.PERMISSION_DATA.collectionName
        )
        logger.info("update exist permission success with id ${permissionInfo.id}")
    }

    private fun buildQuery(permissionInfo: BackupPermission): Query {
        return Query.query(
            Criteria.where(BackupPermission::permName.name).`is`(permissionInfo.permName)
                .and(BackupPermission::projectId.name).`is`(permissionInfo.projectId)
                .and(BackupPermission::repos.name).`is`(permissionInfo.repos)
                .and(BackupPermission::resourceType.name).`is`(permissionInfo.resourceType)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupPermissionDataHandler::class.java)
    }
}