package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.backup.pojo.query.BackupProjectInfo
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
class BackupProjectDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.PROJECT_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        return Criteria.where(NAME).isEqualTo(context.currentProjectId)
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupProjectInfo).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupProjectInfo
        val existRecord = findExistProject(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistProject(record)
        } else {
            record.id = null
            mongoTemplate.save(record, BackupDataEnum.PROJECT_DATA.collectionName)
            logger.info("Create project ${record.name} success!")
        }
    }

    private fun findExistProject(record: BackupProjectInfo): BackupProjectInfo? {
        val existProjectQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existProjectQuery,
            BackupProjectInfo::class.java,
            BackupDataEnum.PROJECT_DATA.collectionName
        )
    }

    private fun updateExistProject(projectInfo: BackupProjectInfo) {
        val projectQuery = buildQuery(projectInfo)
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(BackupProjectInfo::lastModifiedBy.name, projectInfo.lastModifiedBy)
            .set(BackupProjectInfo::createdBy.name, projectInfo.createdBy)
            .set(BackupProjectInfo::createdDate.name, projectInfo.createdDate)
            .set(BackupProjectInfo::lastModifiedDate.name, projectInfo.lastModifiedDate)
            .set(BackupProjectInfo::displayName.name, projectInfo.displayName)
            .set(BackupProjectInfo::description.name, projectInfo.description)
            .set(BackupProjectInfo::metadata.name, projectInfo.metadata)
            .set(BackupProjectInfo::credentialsKey.name, projectInfo.credentialsKey)

        val updateResult = mongoTemplate.updateFirst(projectQuery, update, BackupDataEnum.PROJECT_DATA.collectionName)
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist project failed with name ${projectInfo.name} ")
        } else {
            logger.info("update exist project success with name ${projectInfo.name}")
        }
    }

    private fun buildQuery(projectInfo: BackupProjectInfo): Query {
        return Query(Criteria.where(NAME).isEqualTo(projectInfo.name))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupProjectDataHandler::class.java)
    }
}