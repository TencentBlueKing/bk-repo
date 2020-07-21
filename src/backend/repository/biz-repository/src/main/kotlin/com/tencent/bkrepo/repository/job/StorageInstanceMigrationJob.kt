package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.innercos.request.CopyObjectRequest
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.repository.RepoRepository
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.service.FileReferenceService
import com.tencent.bkrepo.repository.service.RepositoryService
import com.tencent.bkrepo.repository.service.StorageCredentialService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 存储实例迁移任务
 */
@Component
class StorageInstanceMigrationJob {

    @Autowired
    private lateinit var nodeDao: NodeDao

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Autowired
    private lateinit var repoRepository: RepoRepository

    @Autowired
    private lateinit var fileReferenceService: FileReferenceService

    @Autowired
    private lateinit var storageCredentialService: StorageCredentialService

    @Autowired
    private lateinit var storageProperties: StorageProperties

    @Async
    fun migrate(projectId: String, repoName: String, newStorageCredentialsKey: String) {
        logger.info("Start to migrate storage instance, projectId: $projectId, repoName: $repoName, key: $newStorageCredentialsKey.")
        try {
            val repository = repositoryService.queryRepository(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val oldStorageCredentialsKey = repository.credentialsKey
            val oldStorageCredentials = oldStorageCredentialsKey?.let { key -> storageCredentialService.findByKey(key) }
                ?: storageProperties.defaultStorageCredentials()
            val newStorageCredentials = storageCredentialService.findByKey(newStorageCredentialsKey)
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, newStorageCredentialsKey)

            if (oldStorageCredentials == newStorageCredentials) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, newStorageCredentialsKey)
            }
            require(oldStorageCredentials is InnerCosCredentials)
            require(newStorageCredentials is InnerCosCredentials)

            val srcBucket = oldStorageCredentials.bucket
            val srcCosClient = CosClient(oldStorageCredentials)
            val destCosClient = CosClient(newStorageCredentials)

            val startTimeMillis = System.currentTimeMillis()
            var successCount = 0L
            var failedCount = 0L
            var totalCount = 0L

            // 分页查询文件节点
            var page = 0
            val size = 10000
            val query = Query.query(
                Criteria.where(TNode::projectId.name).`is`(projectId)
                .and(TNode::repoName.name).`is`(repoName)
                .and(TNode::folder.name).`is`(false)
            )

            val total = nodeDao.count(query)
            logger.info("$total records to be migrated totally.")

            query.with(PageRequest.of(page, size))
            var nodeList = nodeDao.find(query)
            while (nodeList.isNotEmpty()) {
                logger.info("Retrieved [${nodeList.size}]/$total records to migrate.")
                nodeList.forEach { node ->
                    // 迁移数据，直接操作cos
                    val sha256 = node.sha256!!
                    try {
                        // 判断是否存在
                        if (!srcCosClient.checkObjectExist(CheckObjectExistRequest(sha256))) {
                            throw IllegalStateException("File[$sha256] is not found in cos.")
                        }
                        // 跨bucket copy
                        val copyObjectRequest = CopyObjectRequest(srcBucket, sha256, sha256)
                        destCosClient.copyObject(copyObjectRequest)
                        // old引用计数 -1
                        if (!fileReferenceService.decrement(sha256, oldStorageCredentialsKey)) {
                            throw IllegalStateException("Failed to decrement file reference[$sha256].")
                        }
                        // new引用计数 +1
                        if (!fileReferenceService.increment(sha256, newStorageCredentialsKey)) {
                            throw IllegalStateException("Failed to increment file reference[$sha256].")
                        }
                        logger.info("Success to migrate file[$sha256].")
                        successCount += 1
                        // FileReferenceCleanupJob 会定期清理引用为0的文件数据，所以不需要删除文件数据
                    } catch (exception: RuntimeException) {
                        logger.error("Failed to migrate file[$sha256].", exception)
                        failedCount += 1
                    } finally {
                        totalCount += 1
                    }
                }
                page += 1
                query.with(PageRequest.of(page, size))
                nodeList = nodeDao.find(query)
            }
            // 修改repository配置
            repository.credentialsKey = newStorageCredentialsKey
            repoRepository.save(repository)
            val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
            logger.info("Complete migrate storage instance, projectId: $projectId, repoName: $repoName, key: $newStorageCredentialsKey, " +
                "total: $totalCount, success: $successCount, failed: $failedCount, elapse $elapseTimeMillis ms totally.")
        } catch (exception: RuntimeException) {
            logger.error("Migrate storage instance failed.", exception)
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}