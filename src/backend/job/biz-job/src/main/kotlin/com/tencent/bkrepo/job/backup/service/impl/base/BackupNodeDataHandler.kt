package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.locator.HashFileLocator
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.backup.pojo.query.BackupFileReferenceInfo
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupConflictStrategy
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import com.tencent.bkrepo.job.backup.service.impl.BaseService
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter

@Component
class BackupNodeDataHandler(
    private val storageManager: StorageManager,
    private val mongoTemplate: MongoTemplate,
    private val storageService: StorageService,
) : BackupDataHandler, BaseService() {
    override fun dataType(): BackupDataEnum {
        return BackupDataEnum.NODE_DATA
    }

    override fun buildQueryCriteria(context: BackupContext): Criteria {
        val criteria = Criteria.where(PROJECT).isEqualTo(context.currentProjectId)
            .and(REPO_NAME).isEqualTo(context.currentRepoName)
        if (context.incrementDate != null) {
            criteria.and(BackupNodeInfo::lastModifiedDate.name).gte(context.incrementDate)
        }
        return criteria
    }

    override fun getCollectionName(backupDataEnum: BackupDataEnum, context: BackupContext): String {
        if (context.currentProjectId == null) return StringPool.EMPTY
        return SeparationUtils.getNodeCollectionName(context.currentProjectId!!)
    }

    override fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val node = record as BackupNodeInfo
        context.currentNode = node
        storeRealFile(context)
    }

    override fun <T> returnLastId(data: T): String {
        return (data as BackupNodeInfo).id!!
    }

    override fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val record = record as BackupNodeInfo?
        val collectionName = SeparationUtils.getNodeCollectionName(record!!.projectId)
        uploadFile(record, context)
        val existRecord = findExistNode(record)
        if (existRecord != null) {
            if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                return
            }
            updateExistNode(existRecord, collectionName)
        } else {
            try {
                mongoTemplate.save(record, collectionName)
                logger.info("Create node ${record.fullPath} in ${record.projectId}|${record.repoName} success!")
            } catch (exception: DuplicateKeyException) {
                if (context.task.backupSetting.conflictStrategy == BackupConflictStrategy.SKIP) {
                    return
                }
                // 可能存在已经上传的节点记录不在备份数据里
                updateDuplicateNode(record, collectionName)
            }
        }
    }

    private fun storeRealFile(context: BackupContext) {
        with(context) {
            if (currentNode!!.folder || currentNode!!.sha256 == FAKE_SHA256
                || currentNode!!.sha256.isNullOrEmpty()) return
            val nodeDetail = convertToDetail(currentNode)
            val dir = FILE_STORE_FOLDER + generateRandomPath(currentNode!!.sha256!!)
            val filePath = buildPath(dir, currentNode!!.sha256!!, context.targertPath)
            if (exist(filePath)) {
                logger.info("real file already exist [${currentNode!!.sha256}]")
                return
            }
            // 存储异常如何处理
            storageManager.loadFullArtifactInputStream(nodeDetail, currentStorageCredentials)?.use {
                try {
                    touch(filePath)
                    streamToFile(it, filePath.toString())
                    logger.info("Success to store real file  [${currentNode!!.sha256}]")
                } catch (exception: Exception) {
                    logger.error("Failed to store real file", exception)
                    throw StorageErrorException(StorageMessageCode.STORE_ERROR)
                }
            } ?: {
                logger.error("File of node $nodeDetail not exist!")
                throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            }
        }
    }

    private fun generateRandomPath(sha256: String): String {
        val fileLocator = HashFileLocator()
        return fileLocator.locate(sha256)
    }

    private fun convertToDetail(tNode: BackupNodeInfo?): NodeDetail? {
        return convert(tNode)?.let { NodeDetail(it) }
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

    private fun toMap(metadataList: List<MetadataModel>?): Map<String, Any> {
        return metadataList?.associate { it.key to it.value }.orEmpty()
    }

    private fun findExistNode(record: BackupNodeInfo): BackupNodeInfo? {
        val existNodeQuery = Query(
            Criteria.where(BackupNodeInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupNodeInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupNodeInfo::fullPath.name).isEqualTo(record.fullPath)
                .and(BackupNodeInfo::id.name).isEqualTo(record.id)
        )
        val collectionName = SeparationUtils.getNodeCollectionName(record.projectId)
        return mongoTemplate.findOne(existNodeQuery, BackupNodeInfo::class.java, collectionName)
    }

    private fun updateExistNode(nodeInfo: BackupNodeInfo, nodeCollectionName: String) {
        val nodeQuery = Query(Criteria.where(ID).isEqualTo(nodeInfo.id))
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(NodeDetailInfo::lastModifiedBy.name, nodeInfo.lastModifiedBy)
            .set(NodeDetailInfo::lastModifiedDate.name, nodeInfo.lastModifiedDate)
            .set(NodeDetailInfo::lastModifiedDate.name, nodeInfo.lastAccessDate)
            .set(NodeDetailInfo::deleted.name, nodeInfo.deleted)
            .set(NodeDetailInfo::expireDate.name, nodeInfo.expireDate)
            .set(NodeDetailInfo::lastModifiedDate.name, nodeInfo.lastAccessDate)
            .set(NodeDetailInfo::copyFromCredentialsKey.name, nodeInfo.copyFromCredentialsKey)
            .set(NodeDetailInfo::copyIntoCredentialsKey.name, nodeInfo.copyIntoCredentialsKey)
            .set(NodeDetailInfo::metadata.name, nodeInfo.metadata)
            .set(NodeDetailInfo::archived.name, nodeInfo.archived)
            .set(NodeDetailInfo::compressed.name, nodeInfo.compressed)

        mongoTemplate.updateFirst(nodeQuery, update, nodeCollectionName)
        logger.info(
            "update exist node success with id ${nodeInfo.id} " +
                "and fullPath ${nodeInfo.fullPath} in ${nodeInfo.projectId}|${nodeInfo.repoName}"
        )
    }


    fun uploadFile(record: BackupNodeInfo, context: BackupContext) {
        if (!sha256Check(record.folder, record.sha256)) return
        val repo = RepositoryCommonUtils.getRepositoryDetail(record.projectId, record.repoName)
        val filePath = generateRandomPath(context.targertPath, record.sha256!!)
        val artifactFile = filePath.toFile().toArtifactFile()
        // TODO 增加重试以及异常捕获
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

    fun updateDuplicateNode(record: BackupNodeInfo, collectionName: String) {
        val existNodeQuery = Query(
            Criteria.where(BackupNodeInfo::repoName.name).isEqualTo(record.repoName)
                .and(BackupNodeInfo::projectId.name).isEqualTo(record.projectId)
                .and(BackupNodeInfo::fullPath.name).isEqualTo(record.fullPath)
                .and(BackupNodeInfo::deleted.name).isEqualTo(record.deleted)
        )
        val update = Update()
            .set(NodeDetailInfo::lastModifiedBy.name, record.lastModifiedBy)
            .set(NodeDetailInfo::lastModifiedDate.name, record.lastModifiedDate)
            .set(NodeDetailInfo::lastModifiedDate.name, record.lastAccessDate)
            .set(NodeDetailInfo::expireDate.name, record.expireDate)
            .set(NodeDetailInfo::lastModifiedDate.name, record.lastAccessDate)
            .set(NodeDetailInfo::copyFromCredentialsKey.name, record.copyFromCredentialsKey)
            .set(NodeDetailInfo::copyIntoCredentialsKey.name, record.copyIntoCredentialsKey)
            .set(NodeDetailInfo::metadata.name, record.metadata)
            .set(NodeDetailInfo::archived.name, record.archived)
            .set(NodeDetailInfo::compressed.name, record.compressed)
            .set(NodeDetailInfo::sha256.name, record.sha256)
            .set(NodeDetailInfo::md5.name, record.md5)
            .set(NodeDetailInfo::size.name, record.size)
            .set(NodeDetailInfo::id.name, record.id)
        mongoTemplate.upsert(existNodeQuery, update, collectionName)
    }

    /**
     * 生成随机文件路径
     * */
    fun generateRandomPath(root: Path, sha256: String): Path {
        return Paths.get(root.toFile().path, FILE_STORE_FOLDER, generateRandomPath(sha256), sha256)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BackupNodeDataHandler::class.java)
        private const val FAKE_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}