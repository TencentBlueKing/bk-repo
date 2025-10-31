package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingDeleteRequest
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingListOption
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingRetryRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationMetadataTrackingService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
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

    private val replicaContextBuilder = ReplicaContextBuilder(
        replicaTaskService = replicaTaskService,
        replicaRecordService = replicaRecordService,
        localDataManager = localDataManager,
        replicationProperties = replicationProperties
    )

    private val fileTransferValidator = FileTransferValidator(
        localDataManager = localDataManager,
        clusterNodeService = clusterNodeService
    )

    private val fileTransferProcessor = FileTransferProcessor(
        replicaContextBuilder = replicaContextBuilder
    )

    private val retryStateManager = RetryStateManager(
        federationMetadataTrackingDao = federationMetadataTrackingDao
    )

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
            retryStateManager.setRetrying(
                recordId = existingRecord.id!!,
                retrying = true,
                incrementRetryCount = false
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

    /**
     * 处理单个文件传输（不带重试计数增加）
     */
    private fun processSingleFileTransfer(record: TFederationMetadataTracking): Boolean {
        return retryStateManager.executeWithRetryState(record, incrementRetryCount = false) {
            executeFileTransfer(record)
        }
    }

    /**
     * 执行文件传输的核心逻辑
     */
    private fun executeFileTransfer(record: TFederationMetadataTracking): Boolean {
        // 验证节点和集群信息
        val validationResult = fileTransferValidator.validate(record)
        if (!validationResult.isValid) {
            retryStateManager.updateRetryInfo(record.id!!, validationResult.errorMessage)
            logger.warn("Validation failed for node ${record.nodePath}: ${validationResult.errorMessage}")
            return false
        }

        // 执行文件传输
        val success = fileTransferProcessor.process(
            record = record,
            nodeInfo = validationResult.nodeInfo!!,
            clusterNodeInfo = validationResult.clusterNodeInfo!!
        )

        if (success) {
            // 成功后删除记录
            deleteByTaskKeyAndNodeId(record.taskKey, record.nodeId)
        }

        return success
    }

    override fun listPage(option: FederationMetadataTrackingListOption): Page<TFederationMetadataTracking> {
        val direction = option.sortDirection?.let {
            try {
                Sort.Direction.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                Sort.Direction.DESC
            }
        } ?: Sort.Direction.DESC

        val query = federationMetadataTrackingDao.buildQuery(
            taskKey = option.taskKey,
            remoteClusterId = option.remoteClusterId,
            projectId = option.projectId,
            localRepoName = option.localRepoName,
            remoteProjectId = option.remoteProjectId,
            remoteRepoName = option.remoteRepoName,
            retrying = option.retrying,
            maxRetryCount = option.maxRetryCount,
            sortField = option.sortField,
            sortDirection = direction
        )

        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val totalRecords = federationMetadataTrackingDao.count(query)
        val records = federationMetadataTrackingDao.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun findById(id: String): TFederationMetadataTracking? {
        return federationMetadataTrackingDao.findById(id)
    }

    override fun deleteByConditions(request: FederationMetadataTrackingDeleteRequest): Long {
        // 安全检查：至少需要一个删除条件，避免误删所有记录
        val hasCondition = !request.ids.isNullOrEmpty() || request.maxRetryCount != null

        if (!hasCondition) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                "At least one delete condition is required"
            )
        }

        return federationMetadataTrackingDao.deleteByConditions(
            ids = request.ids,
            maxRetryCount = request.maxRetryCount
        )
    }

    override fun retryRecord(request: FederationMetadataTrackingRetryRequest): Boolean {
        val record = federationMetadataTrackingDao.findById(request.id)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_TRACKING_RECORD_NOT_FOUND,
                request.id
            )

        return retryStateManager.executeWithRetryState(record, incrementRetryCount = true) {
            executeFileTransfer(record)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationMetadataTrackingServiceImpl::class.java)
    }
}
