package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageInfo
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
class BackupPackageDataHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupDataHandler {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.PACKAGE_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria.where(PROJECT).isEqualTo(context.currentProjectId)
            .and(REPO_NAME).isEqualTo(context.currentRepoName)
        if (context.incrementDate != null) {
            criteria.and(BackupPackageInfo::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val packageInfo = record as BackupPackageInfo
        context.currentPackageId = record.id
        context.currentPackageKey = record.key
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupPackageInfo).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupPackageInfo
        val existRecord = findExistPackage(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistPackage(record)
        } else {
            mongoTemplate.save(record, BackupDataEnum.PACKAGE_DATA.collectionName)
            logger.info("Create package ${record.key} in ${record.projectId}|${record.name} success!")
        }
    }

    //TODO  依赖源节点恢复时需要考虑索引文件如何更新.(仓库索引/包索引等 )
    private fun findExistPackage(record: BackupPackageInfo): BackupPackageInfo? {
        val existPackageQuery = buildQuery(record)
        return mongoTemplate.findOne(
            existPackageQuery,
            BackupPackageInfo::class.java,
            BackupDataEnum.PACKAGE_DATA.collectionName
        )
    }

    private fun updateExistPackage(packageInfo: BackupPackageInfo) {
        val packageQuery = buildQuery(packageInfo)
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(BackupPackageInfo::latest.name, packageInfo.latest)
            .set(BackupPackageInfo::description.name, packageInfo.description)
            .set(BackupPackageInfo::extension.name, packageInfo.extension)
            .set(BackupPackageInfo::clusterNames.name, packageInfo.clusterNames)

        val updateResult = mongoTemplate.updateFirst(packageQuery, update, BackupDataEnum.PACKAGE_DATA.collectionName)
        if (updateResult.modifiedCount != 1L) {
            logger.error(
                "update exist package ${packageInfo.key} " +
                    "failed with name ${packageInfo.projectId}|${packageInfo.repoName}"
            )
        } else {
            logger.info(
                "update exist package ${packageInfo.key} " +
                    "success with name ${packageInfo.projectId}|${packageInfo.repoName}"
            )
        }
    }

    private fun buildQuery(packageInfo: BackupPackageInfo): Query {
        return Query(
            Criteria.where(BackupPackageInfo::projectId.name).isEqualTo(packageInfo.projectId)
                .and(BackupPackageInfo::repoName.name).isEqualTo(packageInfo.repoName)
                .and(BackupPackageInfo::key.name).isEqualTo(packageInfo.key)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupPackageDataHandler::class.java)
    }
}