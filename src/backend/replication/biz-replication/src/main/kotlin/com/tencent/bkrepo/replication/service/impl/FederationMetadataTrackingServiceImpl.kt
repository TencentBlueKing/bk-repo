package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationMetadataTrackingService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FederationMetadataTrackingServiceImpl(
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao,
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val replicationProperties: ReplicationProperties
) : FederationMetadataTrackingService {

    override fun createTrackingRecord(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        nodePath: String,
        nodeId: String
    ) {
        val existingRecord = federationMetadataTrackingDao.findByTaskKeyAndNodeId(taskKey, nodeId)
        if (existingRecord == null) {
            val trackingRecord = TFederationMetadataTracking(
                taskKey = taskKey,
                remoteClusterId = remoteClusterId,
                projectId = projectId,
                localRepoName = localRepoName,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                nodePath = nodePath,
                nodeId = nodeId,
                createdDate = LocalDateTime.now()
            )
            federationMetadataTrackingDao.insert(trackingRecord)
        } else {
            federationMetadataTrackingDao.setRetrying(
                id = existingRecord.id!!,
                retrying = true
            )
        }
    }

    override fun deleteByTaskKeyAndNodeId(taskKey: String, nodeId: String) {
        federationMetadataTrackingDao.deleteByTaskKeyAndNodeId(taskKey, nodeId)
    }

    override fun processPendingFileTransfers(): Int {
        logger.info("Starting to process pending file transfers")

        val pendingRecords = findByRetryingFalse(replicationProperties.maxRetryNum)

        if (pendingRecords.isEmpty()) {
            logger.info("No pending file transfers found")
            return 0
        }

        logger.info("Found ${pendingRecords.size} pending file transfers to process")
        var successCount = 0

        for (record in pendingRecords) {
            try {
                if (processSingleFileTransfer(record)) {
                    successCount++
                }
            } catch (e: Exception) {
                logger.error("Error processing file transfer for node ${record.nodePath}: ${e.message}", e)
            }
        }

        logger.info("Processed ${successCount} file transfers successfully")
        return successCount
    }

    override fun cleanExpiredFailedRecords(): Long {
        if (!replicationProperties.autoCleanExpiredFailedRecords) {
            logger.debug("Auto clean expired failed records is disabled, skipping cleanup")
            return 0
        }

        logger.info("Starting to clean expired failed records")

        val maxRetryNum = replicationProperties.maxRetryNum
        val retentionDays = replicationProperties.failedRecordRetentionDays
        val cutoffDate = LocalDateTime.now().minusDays(retentionDays)

        val deletedCount = federationMetadataTrackingDao.deleteExpiredFailedRecords(maxRetryNum, cutoffDate)

        logger.info(
            "Cleaned $deletedCount expired failed records " +
                "(retryCount > $maxRetryNum, lastModifiedDate < $cutoffDate)"
        )
        return deletedCount
    }
    /**
     * 查找需要重试的记录（重试次数小于指定值且不在重试中）
     */
    private fun findByRetryingFalse(maxRetryNum: Int): List<TFederationMetadataTracking> {
        val query = Query(
            where(TReplicaFailureRecord::retrying).isEqualTo(false)
                .and(TReplicaFailureRecord::retryCount.name).lte(maxRetryNum)
        )
        return federationMetadataTrackingDao.find(query)
    }

    private fun updateRetryInfo(id: String, failureReason: String?) {
        federationMetadataTrackingDao.updateRetryInfo(id, failureReason)
    }

    private fun setRetrying(id: String, retrying: Boolean) {
        federationMetadataTrackingDao.setRetrying(id, retrying, incrementRetryCount = false)
    }

    private fun processSingleFileTransfer(record: TFederationMetadataTracking): Boolean {
        // 设置重试状态为true
        setRetrying(record.id!!, true)

        try {
            // 获取节点信息
            val nodeInfo = localDataManager.findNodeById(projectId = record.projectId, nodeId = record.nodeId)
            if (nodeInfo == null) {
                val newRetryCount = record.retryCount + 1
                updateRetryInfo(record.id!!, "Node not found")
                logger.warn("Node ${record.nodePath} not found, retry count: $newRetryCount")
                return false
            }

            // 获取集群信息
            val clusterNodeInfo = clusterNodeService.getByClusterId(record.remoteClusterId)
            if (clusterNodeInfo == null) {
                val newRetryCount = record.retryCount + 1
                updateRetryInfo(record.id!!, "Cluster not found: ${record.remoteClusterId}")
                logger.warn("Cluster ${record.remoteClusterId} not found, retry count: $newRetryCount")
                return false
            }

            // 创建ReplicaContext
            val context = createReplicaContext(record, clusterNodeInfo)

            // 调用FederationReplicator的公共方法
            val replicator = SpringContextUtils.getBean<FederationReplicator>()
            val success = replicator.pushFileToFederatedClusterPublic(context, nodeInfo)

            if (success) {
                // 成功后删除记录
                deleteByTaskKeyAndNodeId(record.taskKey, record.nodeId)
                logger.info("Successfully processed file transfer for node ${record.nodePath}")
                return true
            } else {
                val newRetryCount = record.retryCount + 1
                updateRetryInfo(record.id!!, "File transfer failed")
                logger.warn("Failed to process file transfer for node ${record.nodePath}, retry count: $newRetryCount")
                return false
            }
        } catch (e: Exception) {
            val newRetryCount = record.retryCount + 1
            val errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            updateRetryInfo(record.id!!, errorMessage)
            logger.error("Error processing file transfer for node ${record.nodePath}, retry count: $newRetryCount", e)
            return false
        } finally {
            // 确保重试状态被重置
            setRetrying(record.id!!, false)
        }
    }

    private fun createReplicaContext(
        record: TFederationMetadataTracking,
        clusterNodeInfo: ClusterNodeInfo
    ): ReplicaContext {
        // 获取任务详情
        val taskDetail = replicaTaskService.getDetailByTaskKey(record.taskKey)

        // 获取本地仓库信息
        val localRepo = localDataManager.findRepoByName(record.projectId, record.localRepoName)

        // 创建任务记录
        val taskRecord = replicaRecordService.findOrCreateLatestRecord(record.taskKey)
            .copy(startTime = LocalDateTime.now())

        // 创建ReplicaObjectInfo
        val taskObject = ReplicaObjectInfo(
            localRepoName = record.localRepoName,
            remoteProjectId = record.remoteProjectId,
            remoteRepoName = record.remoteRepoName,
            repoType = localRepo.type,
            pathConstraints = listOf(PathConstraint(path = record.nodePath)),
            packageConstraints = null,
        )

        return ReplicaContext(
            taskDetail = taskDetail,
            taskObject = taskObject,
            taskRecord = taskRecord,
            localRepo = localRepo,
            remoteCluster = clusterNodeInfo,
            replicationProperties = replicationProperties
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationMetadataTrackingServiceImpl::class.java)
    }
}
