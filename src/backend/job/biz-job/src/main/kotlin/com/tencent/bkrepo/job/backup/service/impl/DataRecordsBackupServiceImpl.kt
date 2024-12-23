package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.PROJECT
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
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DataRecordsBackupServiceImpl(
    private val mongoTemplate: MongoTemplate,
    private val backupTaskDao: BackupTaskDao,
) : DataRecordsBackupService, BaseService() {
    override fun projectDataBackup(context: BackupContext) {
        with(context) {
            logger.info("Start to run backup task ${context.task}.")
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            // TODO 需要进行磁盘判断
            // TODO 需要进行仓库用量判断
            // TODO 异常需要捕获
            if (task.content == null || task.content!!.projects.isNullOrEmpty()) return
            initStorage(context)
            // 备份公共基础数据
            if (task.content!!.commonData) {
                commonDataBackup(context)
            }
            // 备份业务数据
            customDataBackup(context)
            //  最后进行压缩
            if (task.content!!.compression) {
                ZipFileUtil.compressDirectory(targertPath.toString(), buildZipFileName(context))
                //  最后需要删除目录
                try {
                    deleteDirectory(targertPath)
                } catch (e: Exception) {
                    logger.warn("delete temp folder error: ", e)
                }
            }
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
            logger.info("Backup task ${context.task} has been finished!")
        }
    }

    private fun initStorage(context: BackupContext) {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = Paths.get(context.task.storeLocation, context.task.name, currentDateStr)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
        context.targertPath = path
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
        with(project) {
            val criteria = Criteria()
            if (projectId.isNullOrEmpty()) {
                if (projectRegex.isNullOrEmpty()) {
                    criteria.and(PROJECT).nin(excludeProjects!!)
                } else {
                    criteria.and(PROJECT).regex(".*${EscapeUtils.escapeRegex(projectRegex)}.*")
                }
            } else {
                criteria.and(PROJECT).isEqualTo(projectId)
            }
            if (project.repoList.isNullOrEmpty()) {
                if (project.repoRegex.isNullOrEmpty()) {
                    if (!project.excludeRepos.isNullOrEmpty()) {
                        criteria.and(REPO_NAME).nin(project.excludeRepos)
                    }
                } else {
                    criteria.and(REPO_NAME).regex(".*${EscapeUtils.escapeRegex(project.repoRegex)}.*")
                }
            } else {
                criteria.and(REPO_NAME).`in`(project.repoList)
            }
            return criteria
        }
    }

    private fun buildZipFileName(context: BackupContext): String {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = context.task.name + StringPool.DASH + currentDateStr + ZIP_FILE_SUFFIX
        return Paths.get(context.task.storeLocation, context.task.name, path).toString()
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
                logger.info("start to backup custom data ${it.collectionName} for repo ${record.projectId}|${record.name}.")
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
                logger.error("Failed to process record $record with data of ${backupDataEnum.collectionName}", e)
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