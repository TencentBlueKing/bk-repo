package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.pojo.query.BackupMavenMetadata
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
class BackupMavenMetadataDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.MAVEN_METADATA_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        return Criteria.where(PROJECT).isEqualTo(context.currentProjectId)
            .and(REPO_NAME).isEqualTo(context.currentRepoName)
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupMavenMetadata).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupMavenMetadata
        val existRecord = findExistMavenMetadata(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistMavenMetadata(record)
        } else {
            record.id = null
            mongoTemplate.save(record, BackupDataEnum.MAVEN_METADATA_DATA.collectionName)
            logger.info("Create metadata in ${record.projectId}|${record.repoName} success!")
        }
    }

    private fun findExistMavenMetadata(record: BackupMavenMetadata): BackupMavenMetadata? {
        val existMetadataQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existMetadataQuery,
            BackupMavenMetadata::class.java,
            BackupDataEnum.MAVEN_METADATA_DATA.collectionName
        )
    }

    private fun updateExistMavenMetadata(mavenMetadata: BackupMavenMetadata) {
        // TODO 记录更新时需要对比时间，保留最新的记录
        val metadataQuery = buildQuery(mavenMetadata)
        val update = Update()
            .set(BackupMavenMetadata::timestamp.name, mavenMetadata.timestamp)
            .set(BackupMavenMetadata::buildNo.name, mavenMetadata.buildNo)

        val updateResult = mongoTemplate.updateFirst(
            metadataQuery,
            update,
            BackupDataEnum.MAVEN_METADATA_DATA.collectionName
        )
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist metadata $mavenMetadata failed")
        } else {
            logger.info("update exist metadata $mavenMetadata success")
        }
    }

    private fun buildQuery(mavenMetadata: BackupMavenMetadata): Query {
        return Query(
            Criteria.where(BackupMavenMetadata::projectId.name).isEqualTo(mavenMetadata.projectId)
                .and(BackupMavenMetadata::repoName.name).isEqualTo(mavenMetadata.repoName)
                .and(BackupMavenMetadata::groupId.name).isEqualTo(mavenMetadata.groupId)
                .and(BackupMavenMetadata::artifactId.name).isEqualTo(mavenMetadata.artifactId)
                .and(BackupMavenMetadata::version.name).isEqualTo(mavenMetadata.version)
                .and(BackupMavenMetadata::classifier.name).isEqualTo(mavenMetadata.classifier)
                .and(BackupMavenMetadata::extension.name).isEqualTo(mavenMetadata.extension)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupMavenMetadataDataHandler::class.java)
    }
}