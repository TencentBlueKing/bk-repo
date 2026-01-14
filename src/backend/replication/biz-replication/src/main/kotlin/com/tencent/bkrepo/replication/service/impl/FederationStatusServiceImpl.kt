package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaRecordDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.ClusterStatusManager
import com.tencent.bkrepo.replication.metrics.FederationMetricsCollector
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.federation.FederationMemberStatus
import com.tencent.bkrepo.replication.pojo.federation.FederationMemberStatusInfo
import com.tencent.bkrepo.replication.pojo.federation.FederationRepositoryStatusInfo
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationStatusService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

/**
 * 联邦仓库状态服务实现
 */
@Service
class FederationStatusServiceImpl(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val clusterNodeService: ClusterNodeService,
    private val clusterStatusManager: ClusterStatusManager,
    private val eventRecordDao: EventRecordDao,
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao,
    private val replicaFailureRecordDao: ReplicaFailureRecordDao,
    private val replicaRecordDao: ReplicaRecordDao,
    private val replicaTaskService: ReplicaTaskService,
    private val metricsCollector: FederationMetricsCollector?,
    private val meterRegistry: MeterRegistry
) : FederationStatusService {

    override fun getFederationRepositoryStatus(
        projectId: String,
        repoName: String,
        federationId: String?
    ): List<FederationRepositoryStatusInfo> {
        val federatedRepos = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)

        if (federatedRepos.isEmpty()) {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "$projectId/$repoName"
            )
        }
        // TODO 缺少传输文件总大小和传输速度

        val result = mutableListOf<FederationRepositoryStatusInfo>()

        federatedRepos.map { federatedRepo ->
            // 获取当前集群信息
            val currentCluster = clusterNodeService.getByClusterId(federatedRepo.clusterId)
            if (currentCluster != null) {
                // 获取成员状态列表
                val members = getFederationMemberStatus(projectId, repoName, federatedRepo.federationId)

                // 统计成员状态
                val totalMembers = members.size
                val healthyMembers = members.count { it.status == FederationMemberStatus.HEALTHY }
                val delayedMembers = members.count { it.status == FederationMemberStatus.DELAYED }
                val errorMembers = members.count { it.status == FederationMemberStatus.ERROR }
                val disabledMembers = members.count { it.status == FederationMemberStatus.DISABLED }

                // 计算延迟统计
                val fileLag = countFileLag(projectId, repoName)
                val eventLag = countEventLag(projectId, repoName)
                val failureCount = countFailureRecords(projectId, repoName)

                // 计算同步统计
                val totalSyncArtifacts = members.sumOf { it.totalSyncArtifacts }
                val successSyncArtifacts = members.sumOf { it.successSyncArtifacts }
                val failedSyncArtifacts = members.sumOf { it.failedSyncArtifacts }
                val totalSyncFiles = members.sumOf { it.totalSyncFiles }
                val failedSyncFiles = members.sumOf { it.failedSyncFiles }
                val successSyncFiles = members.sumOf { it.successSyncFiles }

                // 计算全量同步耗时
                val fullSyncDuration = if (federatedRepo.lastFullSyncStartTime != null &&
                    federatedRepo.lastFullSyncEndTime != null
                ) {
                    Duration.between(
                        federatedRepo.lastFullSyncStartTime,
                        federatedRepo.lastFullSyncEndTime
                    ).toMillis()
                } else null

                result.add(
                    FederationRepositoryStatusInfo(
                        federationId = federatedRepo.federationId,
                        federationName = federatedRepo.name,
                        projectId = projectId,
                        repoName = repoName,
                        currentClusterId = federatedRepo.clusterId,
                        currentClusterName = currentCluster.name,
                        totalMembers = totalMembers,
                        healthyMembers = healthyMembers,
                        delayedMembers = delayedMembers,
                        errorMembers = errorMembers,
                        disabledMembers = disabledMembers,
                        members = members,
                        isFullSyncing = federatedRepo.isFullSyncing,
                        lastFullSyncStartTime = federatedRepo.lastFullSyncStartTime,
                        lastFullSyncEndTime = federatedRepo.lastFullSyncEndTime,
                        fullSyncDuration = fullSyncDuration,
                        fileLag = fileLag,
                        eventLag = eventLag,
                        failureCount = failureCount,
                        totalSyncArtifacts = totalSyncArtifacts,
                        successSyncArtifacts = successSyncArtifacts,
                        failedSyncArtifacts = failedSyncArtifacts,
                        totalSyncFiles = totalSyncFiles,
                        successSyncFiles = successSyncFiles,
                        failedSyncFiles = failedSyncFiles,
                        createdDate = federatedRepo.createdDate,
                        lastModifiedDate = federatedRepo.lastModifiedDate
                    )
                )
            }

        }
        return result
    }

    override fun getFederationMemberStatus(
        projectId: String,
        repoName: String,
        federationId: String
    ): List<FederationMemberStatusInfo> {
        val federatedRepo = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
            .firstOrNull()
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "$projectId/$repoName/$federationId"
            )

        val result = mutableListOf<FederationMemberStatusInfo>()
        federatedRepo.federatedClusters.forEach { federatedCluster ->
            val cluster = clusterNodeService.getByClusterId(federatedCluster.clusterId)

            if (cluster != null) {
                val connected = cluster.status == ClusterNodeStatus.HEALTHY
                val taskInfo = replicaTaskService.getByTaskId(federatedCluster.taskId.orEmpty())
                if (taskInfo == null) {
                    result.add(
                        FederationMemberStatusInfo(
                            clusterId = federatedCluster.clusterId,
                            clusterName = cluster.name,
                            clusterUrl = cluster.url,
                            projectId = federatedCluster.projectId,
                            repoName = federatedCluster.repoName,
                            status = FederationMemberStatus.ERROR,
                            enabled = federatedCluster.enabled,
                            connected = connected,
                            lastSyncTime = null,
                            lastConnectTime = if (connected) LocalDateTime.now() else null,
                            taskKey = StringPool.EMPTY
                        )
                    )
                } else {
                    // 获取该成员的延迟和错误统计
                    val eventLag = countEventLag(taskInfo.key)
                    val failureCount = countFailureRecords(taskInfo.key)
                    val fileLag = countFileLag(taskInfo.key)

                    // 计算成员状态
                    val status = FederationMemberStatus.calculateStatus(
                        enabled = federatedCluster.enabled,
                        connected = connected,
                        eventLag = eventLag,
                        failureCount = failureCount,
                        fileLag = fileLag
                    )

                    val record = replicaRecordDao.findLatestByTaskKey(taskInfo.key)

                    // 获取同步统计
                    val memberStats = getMemberSyncStatistics(record)

                    // 获取最后同步时间
                    val lastSyncTime = record?.endTime ?: record?.startTime

                    result.add(
                        FederationMemberStatusInfo(
                            clusterId = federatedCluster.clusterId,
                            clusterName = cluster.name,
                            clusterUrl = cluster.url,
                            projectId = federatedCluster.projectId,
                            repoName = federatedCluster.repoName,
                            status = status,
                            enabled = federatedCluster.enabled,
                            connected = connected,
                            lastSyncTime = lastSyncTime,
                            lastConnectTime = if (connected) LocalDateTime.now() else null,
                            eventLag = eventLag,
                            failureCount = failureCount,
                            fileLag = fileLag,
                            errorMessage = record?.errorReason,
                            successSyncFiles = memberStats.successFiles,
                            successSyncArtifacts = memberStats.successArtifacts,
                            failedSyncFiles = memberStats.failedFiles,
                            failedSyncArtifacts = memberStats.failedArtifacts,
                            totalSyncFiles = memberStats.totalFiles,
                            totalSyncArtifacts = memberStats.totalArtifacts,
                            taskKey = taskInfo.key
                        )
                    )
                }
            }
        }
        return result
    }

    override fun refreshMemberStatus(projectId: String, repoName: String, federationId: String) {
        logger.info("Refreshing member status for federation [$federationId], repo [$projectId/$repoName]")

        val federatedRepo = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
            .firstOrNull()
            ?: throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
                "$projectId/$repoName/$federationId"
            )

        federatedRepo.federatedClusters.map { federatedCluster ->
            clusterNodeService.getByClusterId(federatedCluster.clusterId)?.let {
                clusterStatusManager.ping(it)
            }
        }

    }


    /**
     * 统计文件延迟数
     */
    private fun countFileLag(projectId: String, repoName: String): Long {
        return try {
            val criteria = Criteria.where(TFederationMetadataTracking::projectId.name).`is`(projectId)
                .and(TFederationMetadataTracking::localRepoName.name).`is`(repoName)
            federationMetadataTrackingDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count metadata lag for [$projectId/$repoName]", e)
            0L
        }
    }

    /**
     * 统计事件延迟数
     * 统计指定项目和仓库的待处理事件数量
     */
    private fun countEventLag(projectId: String, repoName: String): Long {
        return try {
            // 使用点表示法查询嵌套对象字段
            val criteria = Criteria.where("event.projectId").`is`(projectId)
                .and("event.repoName").`is`(repoName)
                .and("taskCompleted").`is`(false) // 只统计未完成的任务
            eventRecordDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count event lag for [$projectId/$repoName]", e)
            0L
        }
    }

    /**
     * 统计失败记录数
     */
    private fun countFailureRecords(projectId: String, repoName: String): Long {
        return try {
            val criteria = Criteria.where(TReplicaFailureRecord::projectId.name).`is`(projectId)
                .and(TReplicaFailureRecord::repoName.name).`is`(repoName)
            replicaFailureRecordDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count failure records for [$projectId/$repoName]", e)
            0L
        }
    }

    /**
     * 统计成员延迟数
     */
    private fun countEventLag(taskKey: String): Long {
        return try {
            val criteria = Criteria.where(TEventRecord::taskKey.name).isEqualTo(taskKey)
                .and(TEventRecord::eventType.name).isEqualTo("FEDERATION")
            eventRecordDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count member lag for task [$taskKey]", e)
            0L
        }
    }

    /**
     * 统计失败数
     */
    private fun countFailureRecords(taskKey: String): Long {
        return try {
            val criteria = Criteria.where("taskKey").`is`(taskKey)
            replicaFailureRecordDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count member errors for task [$taskKey]", e)
            0L
        }
    }


    /**
     * 统计元数据同步，但文件还在传输数量
     */
    private fun countFileLag(taskKey: String): Long {
        return try {
            val criteria = Criteria.where("taskKey").`is`(taskKey)
            federationMetadataTrackingDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to count member errors for task [$taskKey]", e)
            0L
        }
    }

    /**
     * 获取成员同步统计信息
     * 根据taskId获取该成员的同步统计数据
     */
    private fun getMemberSyncStatistics(record: TReplicaRecord?): SyncStatistics {
        return try {
            var totalArtifacts = 0L
            var successArtifacts = 0L
            var failedArtifacts = 0L
            var totalFiles = 0L
            var successFiles = 0L
            var failedFiles = 0L
            var totalDuration = 0L

            record.let {

                // 统计文件数
                record?.replicaOverview?.let { overview ->
                    totalArtifacts = overview.success + overview.failed
                    successArtifacts = overview.success
                    failedArtifacts = overview.failed
                    successFiles = overview.fileSuccess
                    failedFiles = overview.fileFailed
                    totalFiles = overview.fileSuccess + overview.fileFailed
                }

                // 计算耗时
                if (record?.endTime != null) {
                    val duration = Duration.between(record.startTime, record.endTime).toMillis()
                    totalDuration += duration
                }
            }

            SyncStatistics(
                totalArtifacts = totalArtifacts,
                successArtifacts = successArtifacts,
                failedArtifacts = failedArtifacts,
                successFiles = successFiles,
                failedFiles = failedFiles,
                totalFiles = totalFiles,
            )
        } catch (e: Exception) {
            SyncStatistics()
        }
    }


    /**
     * 同步统计信息
     */
    private data class SyncStatistics(
        val totalArtifacts: Long = 0,
        val successArtifacts: Long = 0,
        val failedArtifacts: Long = 0,
        val successFiles: Long = 0,
        val totalFiles: Long = 0,
        val failedFiles: Long = 0
    )

    companion object {
        private val logger = LoggerFactory.getLogger(FederationStatusServiceImpl::class.java)
    }
}


