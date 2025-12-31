package com.tencent.bkrepo.replication.metrics

import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_LAG
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_EVENT_LAG_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FAILURE_LAG
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FAILURE_LAG_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_ACTIVE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_ACTIVE_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_QUEUE_SIZE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_TRANSFER_QUEUE_SIZE_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_ACTIVE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_ACTIVE_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_QUEUE_SIZE
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_QUEUE_SIZE_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_RUNNING
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FULL_SYNC_RUNNING_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_DELAYED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_DELAYED_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_DISABLED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_DISABLED_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_ERROR
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_ERROR_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_HEALTHY
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_HEALTHY_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_TOTAL
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_TOTAL_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_UNSUPPORTED
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_MEMBER_UNSUPPORTED_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_LAG
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_FILE_LAG_DESC
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_REPOSITORY_TOTAL
import com.tencent.bkrepo.common.metrics.constant.FEDERATION_REPOSITORY_TOTAL_DESC
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederationMemberStatus
import com.tencent.bkrepo.replication.replica.executor.FederationFileThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.FederationFullSyncThreadPoolExecutor
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * 联邦仓库监控指标
 *
 * - Lag Metrics：延迟指标（文件、事件、失败记录）
 * - Full Sync Metrics：全量同步指标
 * - Member Status Metrics：成员状态指标（健康、延迟、错误等）
 * - Miscellaneous Metrics：其他指标（线程池、仓库数量等）
 *
 */
@Component
class FederationMetrics(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao,
    private val eventRecordDao: EventRecordDao,
    private val replicaFailureRecordDao: ReplicaFailureRecordDao,
    private val clusterNodeDao: ClusterNodeDao,
    private val replicaTaskDao: ReplicaTaskDao,
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        try {
            // ==================== Lag Metrics（延迟指标） ====================

            // 文件延迟数
            Gauge.builder(FEDERATION_FILE_LAG, this) { getFileLagCount().toDouble() }
                .description(FEDERATION_FILE_LAG_DESC)
                .register(registry)

            // 增量同步事件延迟数
            Gauge.builder(FEDERATION_EVENT_LAG, this) { getEventLagCount().toDouble() }
                .description(FEDERATION_EVENT_LAG_DESC)
                .register(registry)

            // 全量同步失败延迟数
            Gauge.builder(FEDERATION_FAILURE_LAG, this) { getFailureLagCount().toDouble() }
                .description(FEDERATION_FAILURE_LAG_DESC)
                .register(registry)

            // ==================== Full Sync Metrics（全量同步指标） ====================

            // 全量同步执行中的仓库数
            Gauge.builder(FEDERATION_FULL_SYNC_RUNNING, this) { getFullSyncRunningCount().toDouble() }
                .description(FEDERATION_FULL_SYNC_RUNNING_DESC)
                .register(registry)

            // ==================== Miscellaneous Metrics（其他指标） ====================

            // 联邦仓库总数
            Gauge.builder(FEDERATION_REPOSITORY_TOTAL, this) { getFederationRepositoryCount().toDouble() }
                .description(FEDERATION_REPOSITORY_TOTAL_DESC)
                .register(registry)

            // 文件传输线程池指标
            Gauge.builder(
                FEDERATION_FILE_TRANSFER_ACTIVE,
                FederationFileThreadPoolExecutor.instance
            ) { it.activeCount.toDouble() }
                .description(FEDERATION_FILE_TRANSFER_ACTIVE_DESC)
                .register(registry)

            Gauge.builder(
                FEDERATION_FILE_TRANSFER_QUEUE_SIZE,
                FederationFileThreadPoolExecutor.instance
            ) { it.queue.size.toDouble() }
                .description(FEDERATION_FILE_TRANSFER_QUEUE_SIZE_DESC)
                .register(registry)

            // 全量同步线程池指标
            Gauge.builder(
                FEDERATION_FULL_SYNC_ACTIVE,
                FederationFullSyncThreadPoolExecutor.instance
            ) { it.activeCount.toDouble() }
                .description(FEDERATION_FULL_SYNC_ACTIVE_DESC)
                .register(registry)

            Gauge.builder(
                FEDERATION_FULL_SYNC_QUEUE_SIZE,
                FederationFullSyncThreadPoolExecutor.instance
            ) { it.queue.size.toDouble() }
                .description(FEDERATION_FULL_SYNC_QUEUE_SIZE_DESC)
                .register(registry)

            // ==================== Member Status Metrics（成员状态指标） ====================

            // 联邦成员总数
            Gauge.builder(FEDERATION_MEMBER_TOTAL, this) { getMemberTotalCount().toDouble() }
                .description(FEDERATION_MEMBER_TOTAL_DESC)
                .register(registry)

            // 健康成员数
            Gauge.builder(FEDERATION_MEMBER_HEALTHY, this) { getMemberHealthyCount().toDouble() }
                .description(FEDERATION_MEMBER_HEALTHY_DESC)
                .register(registry)

            // 延迟成员数
            Gauge.builder(FEDERATION_MEMBER_DELAYED, this) { getMemberDelayedCount().toDouble() }
                .description(FEDERATION_MEMBER_DELAYED_DESC)
                .register(registry)

            // 错误成员数
            Gauge.builder(FEDERATION_MEMBER_ERROR, this) { getMemberErrorCount().toDouble() }
                .description(FEDERATION_MEMBER_ERROR_DESC)
                .register(registry)

            // 禁用成员数
            Gauge.builder(FEDERATION_MEMBER_DISABLED, this) { getMemberDisabledCount().toDouble() }
                .description(FEDERATION_MEMBER_DISABLED_DESC)
                .register(registry)

            // 不支持成员数
            Gauge.builder(FEDERATION_MEMBER_UNSUPPORTED, this) { getMemberUnsupportedCount().toDouble() }
                .description(FEDERATION_MEMBER_UNSUPPORTED_DESC)
                .register(registry)

            logger.info("Federation metrics successfully registered")
        } catch (e: Exception) {
            logger.warn("Failed to bind federation metrics", e)
        }
    }

    /**
     * 获取文件延迟数量
     * 记录元数据已同步但文件尚未传输的数量
     */
    private fun getFileLagCount(): Long {
        return try {
            federationMetadataTrackingDao.count(Query())
        } catch (e: Exception) {
            logger.warn("Failed to get file lag count", e)
            0L
        }
    }

    /**
     * 获取增量同步事件延迟数量
     * 记录待处理的增量同步事件数量
     */
    private fun getEventLagCount(): Long {
        return try {
            eventRecordDao.count(Query())
        } catch (e: Exception) {
            logger.warn("Failed to get event lag count", e)
            0L
        }
    }

    /**
     * 获取全量同步失败延迟数量
     * 记录全量同步中失败的节点和版本数量
     */
    private fun getFailureLagCount(): Long {
        return try {
            replicaFailureRecordDao.count(Query())
        } catch (e: Exception) {
            logger.warn("Failed to get failure lag count", e)
            0L
        }
    }

    /**
     * 获取全量同步执行中的仓库数量
     */
    private fun getFullSyncRunningCount(): Long {
        return try {
            val criteria = Criteria.where("isFullSyncing").`is`(true)
            federatedRepositoryDao.count(Query(criteria))
        } catch (e: Exception) {
            logger.warn("Failed to get full sync running count", e)
            0L
        }
    }

    /**
     * 获取联邦仓库总数
     */
    private fun getFederationRepositoryCount(): Long {
        return try {
            federatedRepositoryDao.count(Query())
        } catch (e: Exception) {
            logger.warn("Failed to get federation repository count", e)
            0L
        }
    }

    /**
     * 获取联邦成员总数
     */
    private fun getMemberTotalCount(): Long {
        return try {
            val repos = federatedRepositoryDao.find(Query())
            repos.sumOf { it.federatedClusters.size.toLong() }
        } catch (e: Exception) {
            logger.warn("Failed to get member total count", e)
            0L
        }
    }

    /**
     * 获取健康成员数
     */
    private fun getMemberHealthyCount(): Long {
        return getMemberCountByStatus(FederationMemberStatus.HEALTHY)
    }

    /**
     * 获取延迟成员数
     */
    private fun getMemberDelayedCount(): Long {
        return getMemberCountByStatus(FederationMemberStatus.DELAYED)
    }

    /**
     * 获取错误成员数
     */
    private fun getMemberErrorCount(): Long {
        return getMemberCountByStatus(FederationMemberStatus.ERROR)
    }

    /**
     * 获取禁用成员数
     */
    private fun getMemberDisabledCount(): Long {
        return try {
            val repos = federatedRepositoryDao.find(Query())
            repos.sumOf { repo ->
                repo.federatedClusters.count { !it.enabled }.toLong()
            }
        } catch (e: Exception) {
            logger.warn("Failed to get member disabled count", e)
            0L
        }
    }

    /**
     * 获取不支持成员数
     */
    private fun getMemberUnsupportedCount(): Long {
        return getMemberCountByStatus(FederationMemberStatus.UNSUPPORTED)
    }

    /**
     * 根据状态获取成员数量
     */
    private fun getMemberCountByStatus(status: FederationMemberStatus): Long {
        return try {
            federatedRepositoryDao.find(Query())
                .flatMap { it.federatedClusters }
                .count { getClusterMemberStatus(it) == status }
                .toLong()
        } catch (e: Exception) {
            logger.warn("Failed to get member count by status [$status]", e)
            0L
        }
    }

    /**
     * 获取单个集群的成员状态
     */
    private fun getClusterMemberStatus(cluster: FederatedCluster): FederationMemberStatus {
        val connected = isClusterConnected(cluster.clusterId)
        val taskKey = getTaskKey(cluster.taskId.orEmpty())
        val eventLag = countByTaskKey(taskKey) { eventRecordDao.count(it) }
        val errorCount = countByTaskKey(taskKey) { replicaFailureRecordDao.count(it) }
        val fileLag = countByTaskKey(taskKey) { federationMetadataTrackingDao.count(it) }

        return FederationMemberStatus.calculateStatus(
            enabled = cluster.enabled,
            connected = connected,
            eventLag = eventLag,
            failureCount = errorCount,
            fileLag = fileLag
        )
    }

    /**
     * 检查集群是否连接健康
     */
    private fun isClusterConnected(clusterId: String): Boolean {
        return try {
            clusterNodeDao.findById(clusterId)?.status == ClusterNodeStatus.HEALTHY
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取任务 key
     */
    private fun getTaskKey(taskId: String): String {
        return if (taskId.isNotEmpty()) {
            replicaTaskDao.findById(taskId)?.key.orEmpty()
        } else ""
    }

    /**
     * 根据任务 key 统计数量
     */
    private fun countByTaskKey(taskKey: String, counter: (Query) -> Long): Long {
        return if (taskKey.isNotEmpty()) {
            counter(Query(Criteria.where(TReplicaTask::key.name).`is`(taskKey)))
        } else 0L
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationMetrics::class.java)
    }
}
