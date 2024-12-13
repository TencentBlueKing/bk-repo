package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.ID_IDX
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.PROJECT_COLLECTION_NAME
import com.tencent.bkrepo.job.REPO_COLLECTION_NAME
import com.tencent.bkrepo.job.STORAGE_CREDENTIALS_COLLECTION_NAME
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupProjectInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupStorageCredentials
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.task.ProjectContentInfo
import com.tencent.bkrepo.job.backup.service.DataRecordsBackupService
import com.tencent.bkrepo.job.backup.util.ZipFileUtil
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
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
    private val storageManager: StorageManager,
    private val backupTaskDao: BackupTaskDao,
) : DataRecordsBackupService, BaseService() {
    override fun projectDataBackup(context: BackupContext) {
        with(context) {
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            // TODO 需要进行磁盘判断
            // TODO 需要进行仓库用量判断
            if (task.content == null || task.content!!.projects.isNullOrEmpty()) return
            initStorage(context)
            for (projectFilterInfo in task.content!!.projects!!) {
                if (!checkProjectParams(projectFilterInfo)) continue
                val criteria = buildProjectCriteria(projectFilterInfo)
                context.currrentProjectInfo = projectFilterInfo
                queryResult(criteria, BackupProjectInfo::class.java, PROJECT_COLLECTION_NAME, context)
            }
            // TODO 最后需要进行压缩
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
            ZipFileUtil.compressDirectory(targertPath.toString(), buildZipFileName(context))
        }
    }

    private fun initStorage(context: BackupContext) {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = Paths.get(context.task.storeLocation, context.task.name, currentDateStr)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
        context.targertPath = path
        context.tempClient = FileSystemClient(path)
    }

    private fun buildZipFileName(context: BackupContext): String {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = context.task.name + StringPool.DASH + currentDateStr + ZIP_FILE_SUFFRIX
        return Paths.get(context.task.storeLocation, context.task.name, path).toString()
    }

    private fun repoDataBackup(context: BackupContext) {
        val criteria = buildRepositoryCriteria(context.currentProjectId!!, context.currrentProjectInfo!!)
        queryResult(criteria, BackupRepositoryInfo::class.java, REPO_COLLECTION_NAME, context)
    }

    private fun nodeDataBackup(context: BackupContext) {
        val criteria = buildNodeCriteria(context.currentProjectId!!, context.currentRepoName!!)
        val collectionName = SeparationUtils.getNodeCollectionName(context.currentProjectId!!)
        queryResult(criteria, BackupNodeInfo::class.java, collectionName, context)
    }

    private inline fun <reified T> queryResult(
        criteria: Criteria,
        clazz: Class<T>,
        collectionName: String,
        context: BackupContext
    ) {
        val pageSize = BATCH_SIZE
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        do {
            val query = Query(criteria)
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(BATCH_SIZE)
                .with(Sort.by(ID).ascending())
            val data = mongoTemplate.find(query, clazz, collectionName)
            if (data.isEmpty()) {
                break
            }
            val dataLastId = processData(data, context)
            querySize = data.size
            lastId = ObjectId(dataLastId)
        } while (querySize == pageSize)
    }

    private inline fun <reified T> processData(data: List<T>, context: BackupContext): String {
        when (T::class) {
            BackupProjectInfo::class -> {
                data.forEach {
                    val record = it as BackupProjectInfo
                    context.currentProjectId = record.name
                    context.currentRepoName = null
                    storeData(record, context)
                    repoDataBackup(context)
                }
                return (data.last() as BackupProjectInfo).id!!
            }
            BackupRepositoryInfo::class -> {
                data.forEach {
                    val record = it as BackupRepositoryInfo
                    context.currentRepoName = record.name
                    context.currentStorageCredentials = findStorageCredentials(record.credentialsKey)
                    storeData(record, context)
                    nodeDataBackup(context)
                }
                return (data.last() as BackupRepositoryInfo).id!!
            }
            BackupNodeInfo::class -> {
                data.forEach {
                    val record = it as BackupNodeInfo
                    context.currentNode = record
                    storeData(record, context)
                    storeRealFile(context)
                }
                return (data.last() as BackupNodeInfo).id!!
            }
            else -> return StringPool.EMPTY
        }
    }

    // TODO 全存储在一个文件中，当数据过多会导致内容过大
    private inline fun <reified T> storeData(data: T, context: BackupContext) {
        val fileName = when (T::class) {
            BackupProjectInfo::class -> PROJECT_FILE_NAME
            BackupRepositoryInfo::class -> REPOSITORY_FILE_NAME
            BackupNodeInfo::class -> NODE_FILE_NAME
            else -> return
        }
        try {
            context.tempClient.touch(StringPool.EMPTY, fileName)
            logger.info("Success to create file [$fileName]")
            val dataStr = data!!.toJsonString().replace(System.lineSeparator(), "")
            val inputStream = dataStr.byteInputStream()
            val size = dataStr.length.toLong()
            context.tempClient.append(StringPool.EMPTY, fileName, inputStream, size)
            val lineEndStr = "\n"
            context.tempClient.append(
                StringPool.EMPTY,
                fileName,
                lineEndStr.byteInputStream(),
                lineEndStr.length.toLong()
            )
            logger.info("Success to append file [$fileName]")
        } catch (exception: Exception) {
            logger.error("Failed to create file", exception)
            throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            // TODO 异常该如何处理
        }
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

    private fun storeRealFile(context: BackupContext) {
        with(context) {
            if (currentNode!!.folder || currentNode!!.sha256 == FAKE_SHA256 || currentNode!!.sha256.isNullOrEmpty()) return
            val nodeDetail = convertToDetail(currentNode)
            val dir = generateRandomPath(currentNode!!.sha256!!)
            if (tempClient.exist(dir, currentNode!!.sha256!!)) {
                logger.info("real file already exist [${currentNode!!.sha256}]")
                return
            }
            // 存储异常如何处理
            storageManager.loadFullArtifactInputStream(nodeDetail, currentStorageCredentials)?.use {
                try {
                    tempClient.store(dir, currentNode!!.sha256!!, it, currentNode!!.size)
                    logger.info("Success to store real file  [${currentNode!!.sha256}]")
                } catch (exception: Exception) {
                    logger.error("Failed to store real file", exception)
                    throw StorageErrorException(StorageMessageCode.STORE_ERROR)
                }
            }
        }
    }


    private fun checkProjectParams(project: ProjectContentInfo?): Boolean {
        return !(project != null &&
            project.projectRegex.isNullOrEmpty() &&
            project.projectId.isNullOrEmpty() &&
            project.excludeProjects.isNullOrEmpty())
    }

    private fun buildProjectCriteria(project: ProjectContentInfo): Criteria {
        with(project) {
            val criteria = Criteria()
            if (projectId.isNullOrEmpty()) {
                if (projectRegex.isNullOrEmpty()) {
                    criteria.and(NAME).nin(excludeProjects!!)
                } else {
                    criteria.and(NAME).regex(".*${EscapeUtils.escapeRegex(projectRegex)}.*")
                }
            } else {
                criteria.and(NAME).isEqualTo(projectId)
            }
            return criteria
        }
    }

    private fun buildRepositoryCriteria(projectId: String, project: ProjectContentInfo): Criteria {
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
        if (project.repoList.isNullOrEmpty()) {
            if (project.repoRegex.isNullOrEmpty()) {
                if (!project.excludeRepos.isNullOrEmpty()) {
                    criteria.and(NAME).nin(project.excludeRepos)
                }
            } else {
                criteria.and(NAME).regex(".*${EscapeUtils.escapeRegex(project.repoRegex)}.*")
            }
        } else {
            criteria.and(NAME).`in`(project.repoList)
        }
        return criteria
    }


    private fun buildNodeCriteria(projectId: String, repoName: String): Criteria {
        return Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO_NAME).isEqualTo(repoName)
            .and(DELETED_DATE).isEqualTo(null)
    }

    private fun convert(tNode: BackupNodeInfo?): NodeInfo? {
        return tNode?.let {
            val metadata = toMap(it.metadata)
            NodeInfo(
                id = it.id,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                projectId = it.projectId,
                repoName = it.repoName,
                folder = it.folder,
                path = it.path,
                name = it.name,
                fullPath = it.fullPath,
                size = if (it.size < 0L) 0L else it.size,
                nodeNum = it.nodeNum?.let { nodeNum ->
                    if (nodeNum < 0L) 0L else nodeNum
                },
                sha256 = it.sha256,
                md5 = it.md5,
                metadata = metadata,
                nodeMetadata = it.metadata,
                copyFromCredentialsKey = it.copyFromCredentialsKey,
                copyIntoCredentialsKey = it.copyIntoCredentialsKey,
                deleted = it.deleted?.format(DateTimeFormatter.ISO_DATE_TIME),
                lastAccessDate = it.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                clusterNames = it.clusterNames,
                archived = it.archived,
                compressed = it.compressed,
            )
        }
    }

    fun convertToDetail(tNode: BackupNodeInfo?): NodeDetail? {
        return convert(tNode)?.let { NodeDetail(it) }
    }

    fun toMap(metadataList: List<MetadataModel>?): Map<String, Any> {
        return metadataList?.associate { it.key to it.value }.orEmpty()
    }


    private fun convert(credentials: BackupStorageCredentials): StorageCredentials {
        return credentials.credentials.readJsonString<StorageCredentials>().apply { this.key = credentials.id }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataRecordsBackupServiceImpl::class.java)
    }
}