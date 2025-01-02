package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.config.DataBackupConfig
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PRIVATE_TYPE
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PUBLIC_TYPE
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupErrorStrategy
import com.tencent.bkrepo.job.backup.pojo.task.ProjectContentInfo
import com.tencent.bkrepo.job.backup.service.DataRecordsBackupService
import com.tencent.bkrepo.job.backup.service.impl.base.BackupDataMappings
import com.tencent.bkrepo.job.backup.util.ZipFileUtil
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DataRecordsBackupServiceImpl(
    private val mongoTemplate: MongoTemplate,
    private val backupTaskDao: BackupTaskDao,
    private val dataBackupConfig: DataBackupConfig,
) : DataRecordsBackupService, BaseService() {
    override fun projectDataBackup(context: BackupContext) {
        with(context) {
            logger.info("Start to run backup task ${context.task}.")
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            // 需要进行磁盘判断， 当达到磁盘容量大小的限定百分比时则停止
            freeSpaceCheck(context, dataBackupConfig.usageThreshold)
            // TODO 需要进行仓库用量判断
            // TODO 异常需要捕获
            // TODO 历史备份保留周期，当超过该周期时需要进行删除
            if (task.content == null || task.content!!.projects.isNullOrEmpty()) return
            init(context)
            // 备份公共基础数据
            if (task.content!!.commonData) {
                commonDataBackup(context)
            }
            // 备份业务数据
            customDataBackup(context)
            //  最后进行压缩
            if (task.content!!.compression) {
                // TODO 使用压缩需要关注对CPU的影响
                logger.info("start to compress file and upload to cos")
                val zipFileName = buildZipFileName(context)
                val zipFilePath = buildZipFilePath(context)
                try {
                    ZipFileUtil.compressDirectory(targertPath.toString(), zipFilePath)
                    val zipFile = File(zipFilePath)
                    val cosClient = onCreateClient(dataBackupConfig.cos)
                    cosClient.putFileObject(zipFileName, zipFile)
                } catch (e: Exception) {
                    logger.error("compress or upload zip file to cos error, $e")
                    throw StorageErrorException(StorageMessageCode.STORE_ERROR)
                }
                deleteFolder(targertPath)
            }
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
            logger.info("Backup task ${context.task} has been finished!")
        }
    }

    private fun init(context: BackupContext) {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))
        val path = Paths.get(context.task.storeLocation, context.task.name, currentDateStr)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
        context.targertPath = path
        if (context.task.content?.increment != null) {
            val date = if (context.task.content?.incrementDate != null) {
                LocalDateTime.parse(context.task.content!!.incrementDate, DateTimeFormatter.ISO_DATE_TIME)
            } else {
                context.startDate.minusDays(1)
            }
            context.incrementDate = date
        }
    }

    private fun commonDataBackup(context: BackupContext) {
        BackupDataEnum.getParentAndSpecialDataList(PUBLIC_TYPE).forEach {
            logger.info("start to backup common data ${it.collectionName}.")
            try {
                queryResult(context, it)
            } catch (e: Exception) {
                logger.error("backup common data ${it.collectionName} error $e")
                throw e
            }
            logger.info("common data ${it.collectionName} has been backed up!")
        }
    }

    private fun customDataBackup(context: BackupContext) {
        with(context) {
            for (projectFilterInfo in task.content!!.projects!!) {
                if (!checkProjectParams(projectFilterInfo)) continue
                val criteria = buildCriteria(projectFilterInfo)
                queryResult(context, BackupDataEnum.REPOSITORY_DATA, criteria)
            }
        }
    }

    private fun checkProjectParams(project: ProjectContentInfo?): Boolean {
        return !(project != null &&
            project.projectRegex.isNullOrEmpty() &&
            project.projectId.isNullOrEmpty() &&
            project.excludeProjects.isNullOrEmpty())
    }

    private fun buildCriteria(project: ProjectContentInfo): Criteria {
        return Criteria().andOperator(buildProjectCriteria(project), buildRepoCriteria(project))
    }

    private fun buildProjectCriteria(project: ProjectContentInfo): Criteria {
        with(project) {
            val criteria = Criteria()
            if (projectId.isNullOrEmpty()) {
                if (projectRegex.isNullOrEmpty()) {
                    criteria.and(PROJECT).nin(excludeProjects!!)
                } else {
                    val escapeValue = EscapeUtils.escapeRegexExceptWildcard(projectRegex)
                    val regexPattern = escapeValue.replace("*", ".*")
                    criteria.and(PROJECT).regex("^$regexPattern")
                }
            } else {
                criteria.and(PROJECT).isEqualTo(projectId)
            }
            return criteria
        }
    }

    private fun buildRepoCriteria(project: ProjectContentInfo): Criteria {
        val criteria = Criteria()
        if (project.repoList.isNullOrEmpty()) {
            if (project.repoRegex.isNullOrEmpty()) {
                if (!project.excludeRepos.isNullOrEmpty()) {
                    criteria.and(NAME).nin(project.excludeRepos)
                }
            } else {
                val escapeValue = EscapeUtils.escapeRegexExceptWildcard(project.repoRegex)
                val regexPattern = escapeValue.replace("*", ".*")
                criteria.and(NAME).regex("^$regexPattern")
            }
        } else {
            criteria.and(NAME).`in`(project.repoList)
        }
        return criteria
    }

    private fun buildZipFileName(context: BackupContext): String {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))
        return context.task.name + StringPool.DASH + currentDateStr + ZIP_FILE_SUFFIX
    }

    private fun buildZipFilePath(context: BackupContext): String {
        val fileName = buildZipFileName(context)
        return Paths.get(context.task.storeLocation, context.task.name, fileName).toString()
    }

    private fun queryResult(
        context: BackupContext,
        backupDataEnum: BackupDataEnum,
        customRepoCriteria: Criteria? = null
    ) {
        val pageSize = BATCH_SIZE
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        val criteria = customRepoCriteria ?: BackupDataMappings.buildQueryCriteria(backupDataEnum, context)
        val collectionName = BackupDataMappings.getCollectionName(backupDataEnum, context)
        do {
            val query = Query(criteria)
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(BATCH_SIZE)
                .with(Sort.by(ID).ascending())
            val datas = mongoTemplate.find(query, backupDataEnum.backupClazz, collectionName)
            if (datas.isEmpty()) {
                break
            }
            if (customRepoCriteria == null) {
                processData(datas, backupDataEnum, context)
            } else {
                processCustomRepoData(datas as List<BackupRepositoryInfo>, context)
            }
            querySize = datas.size
            lastId = ObjectId(BackupDataMappings.returnLastId(datas.last(), backupDataEnum))
        } while (querySize == pageSize)
    }

    private fun processCustomRepoData(records: List<BackupRepositoryInfo>, context: BackupContext) {
        records.forEach { record ->
            context.currentRepositoryType = record.type
            context.currentProjectId = record.projectId
            context.currentRepoName = record.name
            BackupDataEnum.getParentAndSpecialDataList(PRIVATE_TYPE).forEach {
                logger.info(
                    "start to backup custom data ${it.collectionName} " +
                        "for repo ${record.projectId}|${record.name}."
                )
                try {
                    queryResult(context, it)
                } catch (e: Exception) {
                    logger.error(
                        "backup custom data ${it.collectionName} " +
                            "error for repo ${record.projectId}|${record.name} $e"
                    )
                    throw e
                }
                logger.info(
                    "custom data ${it.collectionName} of " +
                        "repo ${record.projectId}|${record.name} has been backed up!"
                )
            }
        }
    }

    private fun <T> processData(data: List<T>, backupDataEnum: BackupDataEnum, context: BackupContext) {
        data.forEach { record ->
            try {
                BackupDataMappings.preBackupDataHandler(record, backupDataEnum, context)
                storeData(record, backupDataEnum, context)
                BackupDataMappings.postBackupDataHandler(backupDataEnum, context)
                if (!backupDataEnum.relatedData.isNullOrEmpty()) {
                    val relatedDataEnum = BackupDataEnum.getByCollectionName(backupDataEnum.relatedData)
                    queryResult(context, relatedDataEnum)
                }
                val specialData = BackupDataMappings.getSpecialDataEnum(backupDataEnum, context)
                specialData?.forEach {
                    queryResult(context, it)
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to process record $record with " +
                        "data of ${backupDataEnum.collectionName}, error is ${e.message}"
                )
                if (context.task.backupSetting.errorStrategy == BackupErrorStrategy.FAST_FAIL) {
                    throw StorageErrorException(StorageMessageCode.STORE_ERROR)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataRecordsBackupServiceImpl::class.java)
    }
}