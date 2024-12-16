package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PACKAGE_COLLECTION_NAME
import com.tencent.bkrepo.job.PACKAGE_VERSION_COLLECTION_NAME
import com.tencent.bkrepo.job.PROJECT_COLLECTION_NAME
import com.tencent.bkrepo.job.REPO_COLLECTION_NAME
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.BackupFileReferenceInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupMavenMetadata
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupPackageVersionInfoWithKeyInfo
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
                PACKAGE_FILE_NAME -> {
                    context.currentFile = Paths.get(context.targertPath.toString(), PACKAGE_FILE_NAME).toString()
                    loadAndStoreRecord(BackupPackageInfo::class.java, context)
                }
                PACKAGE_VERSION_FILE_NAME -> {
                    context.currentFile = Paths.get(
                        context.targertPath.toString(),
                        PACKAGE_VERSION_FILE_NAME
                    ).toString()
                    loadAndStoreRecord(BackupPackageVersionInfoWithKeyInfo::class.java, context)
                }
                MAVEN_METADATA_FILE_NAME -> {
                    context.currentFile = Paths.get(context.targertPath.toString(), MAVEN_METADATA_FILE_NAME).toString()
                    loadAndStoreRecord(BackupMavenMetadata::class.java, context)
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
            BackupPackageInfo::class -> {
                val record = data as BackupPackageInfo
                val existRecord = findExistPackage(record)
                storeData(existRecord, record, context)
            }
            BackupPackageVersionInfoWithKeyInfo::class -> {
                val record = data as BackupPackageVersionInfoWithKeyInfo
                val (packageId, existRecord) = findExistPackageVersion(record)
                record.packageId = packageId
                storeData(existRecord, record, context)
            }
            BackupMavenMetadata::class -> {
                val record = data as BackupMavenMetadata
                val existRecord = findExistMavenMetadata(record)
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
                BackupPackageInfo::class -> {
                    val record = newData as BackupPackageInfo
                    if (existedData != null) {
                        // TODO  package的id会在version表中使用
                        updateExistPackage(record)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, PACKAGE_COLLECTION_NAME)
                        logger.info("Create package ${record.key} in ${record.projectId}|${record.name} success!")
                    }
                }
                BackupPackageVersionInfo::class -> {
                    val record = newData as BackupPackageVersionInfo
                    if (existedData != null) {
                        updateExistPackageVersion(record)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, PACKAGE_VERSION_COLLECTION_NAME)
                        logger.info("Create version ${record.name} of packageId ${record.packageId} success!")
                    }
                }
                BackupMavenMetadata::class -> {
                    val record = newData as BackupMavenMetadata
                    if (existedData != null) {
                        updateExistMavenMetadata(record)
                    } else {
                        record.id = null
                        mongoTemplate.save(record, MAVEN_METADATA_COLLECTION_NAME)
                        logger.info("Create metadata in ${record.projectId}|${record.repoName} success!")
                    }
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
            Criteria.where(BackupRepositoryInfo::projectId.name).isEqualTo(repoInfo.projectId)
                .and(BackupRepositoryInfo::name.name).isEqualTo(repoInfo.name)
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

    private fun updateExistPackage(packageInfo: BackupPackageInfo) {
        val packageQuery = Query(
            Criteria.where(BackupPackageInfo::projectId.name).isEqualTo(packageInfo.projectId)
                .and(BackupPackageInfo::repoName.name).isEqualTo(packageInfo.repoName)
                .and(BackupPackageInfo::key.name).isEqualTo(packageInfo.key)
        )
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(BackupPackageInfo::latest.name, packageInfo.latest)
            .set(BackupPackageInfo::description.name, packageInfo.description)
            .set(BackupPackageInfo::extension.name, packageInfo.extension)
            .set(BackupPackageInfo::clusterNames.name, packageInfo.clusterNames)

        val updateResult = mongoTemplate.updateFirst(packageQuery, update, PACKAGE_COLLECTION_NAME)
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

    private fun updateExistPackageVersion(versionInfo: BackupPackageVersionInfo) {
        val packageQuery = Query(
            Criteria.where(BackupPackageVersionInfo::packageId.name).isEqualTo(versionInfo.packageId)
                .and(BackupPackageVersionInfo::name.name).isEqualTo(versionInfo.name)
        )
        // 逻辑删除， 同时删除索引
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

        val updateResult = mongoTemplate.updateFirst(packageQuery, update, PACKAGE_VERSION_COLLECTION_NAME)
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist version ${versionInfo.name} of package ${versionInfo.packageId} failed")
        } else {
            logger.error("update exist version ${versionInfo.name} of package ${versionInfo.packageId} success")
        }
    }

    private fun updateExistMavenMetadata(mavenMetadata: BackupMavenMetadata) {
        val metadataQuery = Query(
            Criteria.where(BackupMavenMetadata::projectId.name).isEqualTo(mavenMetadata.projectId)
                .and(BackupMavenMetadata::repoName.name).isEqualTo(mavenMetadata.repoName)
                .and(BackupMavenMetadata::groupId.name).isEqualTo(mavenMetadata.groupId)
                .and(BackupMavenMetadata::artifactId.name).isEqualTo(mavenMetadata.artifactId)
                .and(BackupMavenMetadata::version.name).isEqualTo(mavenMetadata.version)
                .and(BackupMavenMetadata::classifier.name).isEqualTo(mavenMetadata.classifier)
                .and(BackupMavenMetadata::extension.name).isEqualTo(mavenMetadata.extension)
        )
        val update = Update()
            .set(BackupMavenMetadata::timestamp.name, mavenMetadata.timestamp)
            .set(BackupMavenMetadata::buildNo.name, mavenMetadata.buildNo)

        val updateResult = mongoTemplate.updateFirst(metadataQuery, update, MAVEN_METADATA_COLLECTION_NAME)
        if (updateResult.modifiedCount != 1L) {
            logger.error("update exist metadata $mavenMetadata failed")
        } else {
            logger.error("update exist metadata $mavenMetadata success")
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

    private fun findExistPackage(record: BackupPackageInfo): BackupPackageInfo? {
        val existPackageQuery = Query(
            Criteria.where(BackupPackageInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupPackageInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupPackageInfo::key.name).isEqualTo(record.key)
        )
        return mongoTemplate.findOne(existPackageQuery, BackupPackageInfo::class.java, PACKAGE_COLLECTION_NAME)
    }

    private fun findExistPackageVersion(
        record: BackupPackageVersionInfoWithKeyInfo
    ): Pair<String, BackupPackageVersionInfo?> {
        // TODO packageId变化该如何处理
        val existPackageQuery = Query(
            Criteria.where(BackupPackageVersionInfoWithKeyInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupPackageVersionInfoWithKeyInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupPackageVersionInfoWithKeyInfo::key.name).isEqualTo(record.key)
        )
        // TODO 此处需要缓存，避免过多导致频繁查询
        val packageInfo = mongoTemplate.findOne(
            existPackageQuery,
            BackupPackageInfo::class.java,
            PACKAGE_COLLECTION_NAME
        )
        val existVersionQuery = Query(
            Criteria.where(BackupPackageVersionInfoWithKeyInfo::packageId.name).isEqualTo(packageInfo.id)
                .and(BackupPackageVersionInfoWithKeyInfo::name.name).isEqualTo(record.name)
        )
        val existVersion = mongoTemplate.findOne(
            existVersionQuery,
            BackupPackageVersionInfo::class.java,
            PACKAGE_VERSION_COLLECTION_NAME
        )
        return Pair(packageInfo.id!!, existVersion)
    }

    private fun findExistMavenMetadata(record: BackupMavenMetadata): BackupMavenMetadata? {
        // TODO 记录更新时需要对比时间，保留最新的记录
        // TODO 对于存在索引文件的仓库需要更新对应索引文件信息
        val existMetadataQuery = Query(
            Criteria.where(BackupMavenMetadata::projectId.name).isEqualTo(record.projectId)
                .and(BackupMavenMetadata::repoName.name).isEqualTo(record.repoName)
                .and(BackupMavenMetadata::groupId.name).isEqualTo(record.groupId)
                .and(BackupMavenMetadata::artifactId.name).isEqualTo(record.artifactId)
                .and(BackupMavenMetadata::version.name).isEqualTo(record.version)
                .and(BackupMavenMetadata::classifier.name).isEqualTo(record.classifier)
                .and(BackupMavenMetadata::extension.name).isEqualTo(record.extension)
        )
        return mongoTemplate.findOne(existMetadataQuery, BackupMavenMetadata::class.java, MAVEN_METADATA_COLLECTION_NAME)
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