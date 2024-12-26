package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.job.PACKAGE_ID
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfoWithKeyInfo
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
class BackupPackageVersionDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.PACKAGE_VERSION_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria.where(PACKAGE_ID).isEqualTo(context.currentPackageId)
        if (context.incrementDate != null) {
            criteria.and(BackupPackageVersionInfoWithKeyInfo::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupPackageVersionInfoWithKeyInfo
        context.currentVersionName = record.name
        record.projectId = context.currentProjectId!!
        record.repoName = context.currentRepoName!!
        record.key = context.currentPackageKey!!

    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupPackageVersionInfoWithKeyInfo).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupPackageVersionInfoWithKeyInfo
        val (packageId, existRecord) = findExistPackageVersion(record)
        record.packageId = packageId
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistPackageVersion(record)
        } else {
            val storeRecord = convert(record)
            mongoTemplate.save(storeRecord, BackupDataEnum.PACKAGE_VERSION_DATA.collectionName)
            logger.info("Create version ${record.name} of packageId ${record.packageId} success!")
        }

    }

    private fun findExistPackageVersion(
        record: BackupPackageVersionInfoWithKeyInfo
    ): Pair<String, BackupPackageVersionInfo?> {
        // package的id会在version表中使用
        val existPackageQuery = Query(
            Criteria.where(BackupPackageVersionInfoWithKeyInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupPackageVersionInfoWithKeyInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupPackageVersionInfoWithKeyInfo::key.name).isEqualTo(record.key)
        )
        // TODO 此处需要缓存，避免过多导致频繁查询
        val packageInfo = mongoTemplate.findOne(
            existPackageQuery,
            BackupPackageInfo::class.java,
            BackupDataEnum.PACKAGE_DATA.collectionName
        )
        val existVersionQuery = Query(
            Criteria.where(BackupPackageVersionInfoWithKeyInfo::packageId.name).isEqualTo(packageInfo.id)
                .and(BackupPackageVersionInfoWithKeyInfo::name.name).isEqualTo(record.name)
        )
        val existVersion = mongoTemplate.findOne(
            existVersionQuery,
            BackupPackageVersionInfo::class.java,
            BackupDataEnum.PACKAGE_VERSION_DATA.collectionName
        )
        return Pair(packageInfo.id!!, existVersion)
    }

    private fun updateExistPackageVersion(versionInfo: BackupPackageVersionInfoWithKeyInfo) {
        val packageQuery = Query(
            Criteria.where(BackupPackageVersionInfo::packageId.name).isEqualTo(versionInfo.packageId)
                .and(BackupPackageVersionInfo::name.name).isEqualTo(versionInfo.name)
        )
        val update = Update()
            .set(BackupPackageVersionInfo::size.name, versionInfo.size)
            .set(BackupPackageVersionInfo::ordinal.name, versionInfo.ordinal)
            .set(BackupPackageVersionInfo::downloads.name, versionInfo.downloads)
            .set(BackupPackageVersionInfo::manifestPath.name, versionInfo.manifestPath)
            .set(BackupPackageVersionInfo::artifactPath.name, versionInfo.artifactPath)
            .set(BackupPackageVersionInfo::stageTag.name, versionInfo.stageTag)
            .set(BackupPackageVersionInfo::metadata.name, versionInfo.metadata)
            .set(BackupPackageVersionInfo::tags.name, versionInfo.tags)
            .set(BackupPackageVersionInfo::extension.name, versionInfo.extension)
            .set(BackupPackageVersionInfo::clusterNames.name, versionInfo.clusterNames)

        val updateResult = mongoTemplate.updateFirst(
            packageQuery,
            update,
            BackupDataEnum.PACKAGE_VERSION_DATA.collectionName
        )
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist version ${versionInfo.name} of package ${versionInfo.packageId} failed")
        } else {
            logger.info("update exist version ${versionInfo.name} of package ${versionInfo.packageId} success")
        }
    }

    fun convert(versionInfo: BackupPackageVersionInfoWithKeyInfo): BackupPackageVersionInfo {
        with(versionInfo) {
            return BackupPackageVersionInfo(
                id = id,
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate,
                packageId = packageId,
                name = name,
                size = size,
                ordinal = ordinal,
                downloads = downloads,
                manifestPath = manifestPath,
                artifactPath = artifactPath,
                stageTag = stageTag,
                metadata = metadata,
                tags = tags,
                extension = extension,
                clusterNames = clusterNames,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupPackageVersionDataHandler::class.java)
    }
}