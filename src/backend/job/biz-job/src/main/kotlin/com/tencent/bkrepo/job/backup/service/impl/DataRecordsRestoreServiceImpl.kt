package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.PROJECT_COLLECTION_NAME
import com.tencent.bkrepo.job.REPO_COLLECTION_NAME
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.BackupFileReferenceInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupProjectInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupRepositoryInfo
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupConflictStrategy
import com.tencent.bkrepo.job.backup.service.DataRecordsRestoreService
import com.tencent.bkrepo.job.backup.util.ZipFileUtil
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.streams.toList

@Service
class DataRecordsRestoreServiceImpl(
    private val mongoTemplate: MongoTemplate,
    private val storageService: StorageService,
    private val backupTaskDao: BackupTaskDao,
) : DataRecordsRestoreService, BaseService() {
    override fun projectDataRestore(context: BackupContext) {
        with(context) {
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            val unZipTempFolder = buildTargetFolder(context)
            ZipFileUtil.decompressFile(task.storeLocation, unZipTempFolder.toString())
            initStorage(unZipTempFolder, context)
            processFiles(context)
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
        }
    }

    private fun buildTargetFolder(context: BackupContext): Path {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val path = Paths.get(context.task.storeLocation)
        if (!Files.exists(path)) {
            throw FileNotFoundException(context.task.storeLocation)
        }
        val tempFolder = path.name.removeSuffix(ZIP_FILE_SUFFRIX) + StringPool.DASH + currentDateStr
        return Paths.get(path.parent.toString(), tempFolder)
    }

    private fun initStorage(unZipTempFolder: Path, context: BackupContext) {
        if (!Files.exists(unZipTempFolder)) {
            throw FileNotFoundException(unZipTempFolder.toString())
        }
        val subdirectories = Files.list(unZipTempFolder)
            .filter { Files.isDirectory(it) }
            .toList()
        val targetFolder = subdirectories.firstOrNull() ?: throw FileNotFoundException(unZipTempFolder.toString())
        context.targertPath = targetFolder
        context.tempClient = FileSystemClient(targetFolder)
    }

    private fun processFiles(context: BackupContext) {
        for (file in FILE_LIST) {
            when (file) {
                PROJECT_FILE_NAME -> {
                    context.currentFile = Paths.get(context.targertPath.toString(), PROJECT_FILE_NAME).toString()
                    loadAndStoreRecord(BackupProjectInfo::class.java, context)
                }
                REPOSITORY_FILE_NAME -> {
                    context.currentFile = Paths.get(context.targertPath.toString(), REPOSITORY_FILE_NAME).toString()
                    loadAndStoreRecord(BackupRepositoryInfo::class.java, context)
                }
                NODE_FILE_NAME -> {
                    context.currentFile = Paths.get(context.targertPath.toString(), NODE_FILE_NAME).toString()
                    loadAndStoreRecord(BackupNodeInfo::class.java, context)
                }
                else -> continue
            }
        }
    }

    private inline fun <reified T> loadAndStoreRecord(clazz: Class<T>, context: BackupContext) {
        with(context) {
            if (!Files.exists(Paths.get(currentFile))) {
                logger.error("$currentFile not exist!")
                return
            }
            val file = File(currentFile)
            file.forEachLine { line ->
                val record = JsonUtils.objectMapper.readValue(line, clazz)
                processData(record, context)
            }
        }
    }

    private inline fun <reified T> processData(data: T, context: BackupContext) {
        when (T::class) {
            BackupProjectInfo::class -> {
                val record = data as BackupProjectInfo
                val existRecord = findExistProject(record)
                storeData(existRecord, record, context)
            }
            BackupRepositoryInfo::class -> {
                val record = data as BackupRepositoryInfo
                val existRecord = findExistRepository(record)
                storeData(existRecord, record, context)
            }
            BackupNodeInfo::class -> {
                val record = data as BackupNodeInfo
                val existRecord = findExistNode(record)
                storeData(existRecord, record, context)
            }
            else -> return
        }
    }

    //TODO  如果项目仓库恢复失败, 节点恢复的时候需要禁止
    private inline fun <reified T> storeData(existedData: T?, newData: T, context: BackupContext) {
        if (existedData != null && context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
            return
        }
        try {
            when (T::class) {
                BackupProjectInfo::class -> {
                    val record = newData as BackupProjectInfo
                    if (existedData != null) {
                        updateExistProject(record)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, PROJECT_COLLECTION_NAME)
                        logger.info("Create project ${record.name} success!")
                    }
                }
                BackupRepositoryInfo::class -> {
                    val record = newData as BackupRepositoryInfo
                    if (existedData != null) {
                        // TODO  仓库涉及存储, 不能简单更新
                        updateExistRepo(record)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, REPO_COLLECTION_NAME)
                        logger.info("Create repo ${record.projectId}|${record.name} success!")
                    }
                }
                BackupNodeInfo::class -> {
                    val record = newData as BackupNodeInfo
                    val existRecord = existedData as BackupNodeInfo?
                    val collectionName = SeparationUtils.getNodeCollectionName(record.projectId)
                    if (existRecord != null) {
                        removeExistNode(existRecord, collectionName)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, collectionName)
                        logger.info("Create node ${record.fullPath} in ${record.projectId}|${record.repoName} success!")
                    }
                    uploadFile(record, context)
                }
                else -> return
            }
        } catch (e: DuplicateKeyException) {
            logger.warn("insert data occurred DuplicateKeyException")
        }

    }

    private fun updateExistProject(projectInfo: BackupProjectInfo) {
        val projectQuery = Query(Criteria.where(NAME).isEqualTo(projectInfo.name))
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

        val updateResult = mongoTemplate.updateFirst(projectQuery, update, PROJECT_COLLECTION_NAME)
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist project failed with name ${projectInfo.name} ")
        } else {
            logger.info("update exist project success with name ${projectInfo.name}")
        }
    }

    private fun updateExistRepo(repoInfo: BackupRepositoryInfo) {
        val repoQuery = Query(
            Criteria.where(NAME).isEqualTo(repoInfo.name)
                .and(PROJECT).isEqualTo(repoInfo.projectId)
                .and(DELETED_DATE).isEqualTo(null)
        )
        // 逻辑删除， 同时删除索引
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

        // TODO quote和used需要进行事实计算更新

        val updateResult = mongoTemplate.updateFirst(repoQuery, update, REPO_COLLECTION_NAME)

        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist repo failed with name ${repoInfo.projectId}|${repoInfo.name}")
        } else {
            logger.info("update exist repo success with name ${repoInfo.projectId}|${repoInfo.name}")
        }
    }

    private fun removeExistNode(
        nodeInfo: BackupNodeInfo,
        nodeCollectionName: String
    ) {
        val nodeQuery = Query(Criteria.where(ID).isEqualTo(nodeInfo.id))
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(NodeDetailInfo::lastModifiedBy.name, SYSTEM_USER)
            .set(NodeDetailInfo::deleted.name, LocalDateTime.now())
        val updateResult = mongoTemplate.updateFirst(nodeQuery, update, nodeCollectionName)
        if (updateResult.modifiedCount != 1L) {
            logger.error(
                "delete exist node failed with id ${nodeInfo.id} " +
                    "and fullPath ${nodeInfo.fullPath} in ${nodeInfo.projectId}|${nodeInfo.repoName}"
            )
        } else {
            logger.info(
                "delete exist node success with id ${nodeInfo.id} " +
                    "and fullPath ${nodeInfo.fullPath} in ${nodeInfo.projectId}|${nodeInfo.repoName}"
            )
        }
    }

    private fun findExistProject(record: BackupProjectInfo): BackupProjectInfo? {
        val existProjectQuery = Query(
            Criteria.where(BackupProjectInfo::name.name).isEqualTo(record.name)
        )
        return mongoTemplate.findOne(existProjectQuery, BackupProjectInfo::class.java, PROJECT_COLLECTION_NAME)
    }

    private fun findExistRepository(record: BackupRepositoryInfo): BackupRepositoryInfo? {
        val existRepoQuery = Query(
            Criteria.where(BackupRepositoryInfo::name.name).isEqualTo(record.name)
                .and(BackupRepositoryInfo::projectId.name).isEqualTo(record.projectId)
        )
        return mongoTemplate.findOne(existRepoQuery, BackupRepositoryInfo::class.java, REPO_COLLECTION_NAME)
    }

    private fun findExistNode(record: BackupNodeInfo): BackupNodeInfo? {
        val existNodeQuery = Query(
            Criteria.where(BackupNodeInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupNodeInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupNodeInfo::fullPath.name).isEqualTo(record.fullPath)
                .and(BackupNodeInfo::deleted.name).isEqualTo(null)
        )
        val collectionName = SeparationUtils.getNodeCollectionName(record.projectId)
        return mongoTemplate.findOne(existNodeQuery, BackupNodeInfo::class.java, collectionName)
    }

    fun uploadFile(record: BackupNodeInfo, context: BackupContext) {
        if (!sha256Check(record.folder, record.sha256)) return
        val repo = RepositoryCommonUtils.getRepositoryDetail(record.projectId, record.repoName)
        val filePath = generateRandomPath(context.targertPath, record.sha256!!)
        val artifactFile = filePath.toFile().toArtifactFile()
        // TODO 增加重试已经异常捕获
        storageService.store(record.sha256!!, artifactFile, repo.storageCredentials)
        // 只有新增的时候才去尽显文件索引新增
        increment(record.sha256!!, repo.storageCredentials?.key)
    }


    fun sha256Check(folder: Boolean, sha256: String?): Boolean {
        // 增加对应cold node 的文件引用
        if (folder || sha256.isNullOrBlank() || sha256 == FAKE_SHA256) {
            return false
        }
        return true
    }


    fun increment(sha256: String, credentialsKey: String?) {
        val collectionName = SeparationUtils.getFileReferenceCollectionName(sha256)
        val criteria = Criteria.where(BackupFileReferenceInfo::sha256.name).`is`(sha256)
            .and(BackupFileReferenceInfo::credentialsKey.name).`is`(credentialsKey)
        val query = Query(criteria)
        val update = Update().inc(BackupFileReferenceInfo::count.name, 1)
        try {
            mongoTemplate.upsert(query, update, collectionName)
        } catch (exception: DuplicateKeyException) {
            // retry because upsert operation is not atomic
            mongoTemplate.upsert(query, update, collectionName)
        }
        logger.info("Increment node reference of file [$sha256] on credentialsKey [$credentialsKey].")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataRecordsRestoreServiceImpl::class.java)
    }
}