package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.pojo.query.common.BackupConanMetadata
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
class BackupConanMetadataDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.CONAN_METADATA_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        return Criteria.where(PROJECT).isEqualTo(context.currentProjectId)
            .and(REPO_NAME).isEqualTo(context.currentRepoName)
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupConanMetadata).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupConanMetadata
        val existRecord = findExistConanMetadata(record)
        if (existRecord == null) {
            mongoTemplate.save(record, BackupDataEnum.CONAN_METADATA_DATA.collectionName)
            logger.info("Create metadata in ${record.projectId}|${record.repoName} success!")
        }
    }

    private fun findExistConanMetadata(record: BackupConanMetadata): BackupConanMetadata? {
        val existMetadataQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existMetadataQuery,
            BackupConanMetadata::class.java,
            BackupDataEnum.CONAN_METADATA_DATA.collectionName
        )
    }

    private fun buildQuery(conanMetadata: BackupConanMetadata): Query {
        return Query(
            Criteria.where(BackupConanMetadata::projectId.name).isEqualTo(conanMetadata.projectId)
                .and(BackupConanMetadata::repoName.name).isEqualTo(conanMetadata.repoName)
                .and(BackupConanMetadata::user.name).isEqualTo(conanMetadata.user)
                .and(BackupConanMetadata::name.name).isEqualTo(conanMetadata.name)
                .and(BackupConanMetadata::version.name).isEqualTo(conanMetadata.version)
                .and(BackupConanMetadata::channel.name).isEqualTo(conanMetadata.channel)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupConanMetadataDataHandler::class.java)
    }
}