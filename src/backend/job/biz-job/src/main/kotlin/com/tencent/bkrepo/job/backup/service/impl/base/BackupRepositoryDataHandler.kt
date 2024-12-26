package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.mongo.constant.ID_IDX
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupStorageCredentials
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupConflictStrategy
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import com.tencent.bkrepo.job.backup.service.impl.repo.BackupRepoSpecialMappings
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class BackupRepositoryDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.REPOSITORY_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria.where(PROJECT).isEqualTo(context.currentProjectId)
            .and(NAME).isEqualTo(context.currentRepoName)
        if (context.incrementDate != null) {
            criteria.and(BackupRepositoryInfo::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val repo = record as BackupRepositoryInfo
        context.currentRepositoryType = repo.type
        context.currentStorageCredentials = findStorageCredentials(repo.credentialsKey)
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupRepositoryInfo).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupRepositoryInfo
        val existRecord = findExistRepository(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            // TODO  仓库涉及存储, 不能简单更新
            updateExistRepo(record)
        } else {
            mongoTemplate.save(record, BackupDataEnum.REPOSITORY_DATA.collectionName)
            logger.info("Create repo ${record.projectId}|${record.name} success!")
        }
    }

    override fun getSpecialDataEnum(context: BackupContext): List<BackupDataEnum>? {
        return BackupRepoSpecialMappings.getRepoSpecialDataEnum(context)
    }

    private fun findStorageCredentials(currentCredentialsKey: String?): StorageCredentials? {
        val backupStorageCredentials = currentCredentialsKey?.let {
            mongoTemplate.findOne(
                Query(Criteria.where(ID_IDX).isEqualTo(it)),
                BackupStorageCredentials::class.java,
                STORAGE_CREDENTIALS_COLLECTION_NAME
            )
        }
        return backupStorageCredentials?.let { convert(backupStorageCredentials) }
    }

    private fun convert(credentials: BackupStorageCredentials): StorageCredentials {
        return credentials.credentials.readJsonString<StorageCredentials>().apply { this.key = credentials.id }
    }

    private fun findExistRepository(record: BackupRepositoryInfo): BackupRepositoryInfo? {
        val existRepoQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existRepoQuery,
            BackupRepositoryInfo::class.java,
            BackupDataEnum.REPOSITORY_DATA.collectionName
        )
    }

    private fun updateExistRepo(repoInfo: BackupRepositoryInfo) {
        val repoQuery = buildQuery(repoInfo)
        val update = Update()
            .set(BackupRepositoryInfo::lastModifiedBy.name, repoInfo.lastModifiedBy)
            .set(BackupRepositoryInfo::createdBy.name, repoInfo.createdBy)
            .set(BackupRepositoryInfo::createdDate.name, repoInfo.createdDate)
            .set(BackupRepositoryInfo::lastModifiedDate.name, repoInfo.lastModifiedDate)
            .set(BackupRepositoryInfo::type.name, repoInfo.type)
            .set(BackupRepositoryInfo::description.name, repoInfo.description)
            .set(BackupRepositoryInfo::category.name, repoInfo.category)
            .set(BackupRepositoryInfo::public.name, repoInfo.public)
            .set(BackupRepositoryInfo::configuration.name, repoInfo.configuration)
            .set(BackupRepositoryInfo::oldCredentialsKey.name, repoInfo.oldCredentialsKey)
            .set(BackupRepositoryInfo::display.name, repoInfo.display)
            .set(BackupRepositoryInfo::clusterNames.name, repoInfo.clusterNames)
            .set(BackupRepositoryInfo::credentialsKey.name, repoInfo.credentialsKey)
            .set(BackupRepositoryInfo::deleted.name, repoInfo.deleted)
            .set(BackupRepositoryInfo::quota.name, repoInfo.quota)
            .set(BackupRepositoryInfo::used.name, repoInfo.used)
        // TODO quote和used需要进行事实计算更新

        val updateResult = mongoTemplate.updateFirst(repoQuery, update, BackupDataEnum.REPOSITORY_DATA.collectionName)

        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist repo failed with name ${repoInfo.projectId}|${repoInfo.name}")
        } else {
            logger.info("update exist repo success with name ${repoInfo.projectId}|${repoInfo.name}")
        }
    }

    private fun buildQuery(repoInfo: BackupRepositoryInfo): Query {
        return Query(
            Criteria.where(BackupRepositoryInfo::projectId.name).isEqualTo(repoInfo.projectId)
                .and(BackupRepositoryInfo::name.name).isEqualTo(repoInfo.name)
                .and(BackupRepositoryInfo::deleted.name).isEqualTo(repoInfo.deleted)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupRepositoryDataHandler::class.java)
        private const val STORAGE_CREDENTIALS_COLLECTION_NAME = "storage_credentials"
    }
}