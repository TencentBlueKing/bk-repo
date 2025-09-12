package com.tencent.bkrepo.replication.service.impl.federation

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.replica.executor.FederationFullSyncThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.federation.FederationFullSyncReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Callable

/**
 * 联邦同步管理器
 * 负责联邦仓库的全量同步操作
 */
@Component
class FederationSyncManager(
    private val localFederationManager: LocalFederationManager,
    private val replicaTaskService: ReplicaTaskService,
    private val federationFullSyncReplicaJobExecutor: FederationFullSyncReplicaJobExecutor,
) {

    private val executor = FederationFullSyncThreadPoolExecutor.instance

    /**
     * 执行联邦仓库全量同步
     */
    fun executeFullSync(projectId: String, repoName: String, federationId: String) {
        val federationRepository = validateAndGetFederationRepository(projectId, repoName, federationId)

        // 检查并设置同步状态
        ensureSyncNotRunningAndStart(projectId, repoName, federationId)

        logger.info("Starting full sync for federation repository: $projectId/$repoName, federationId: $federationId")

        // 异步执行同步任务
        submitSyncTask(projectId, repoName, federationId, federationRepository)
    }

    /**
     * 验证并获取联邦仓库
     */
    private fun validateAndGetFederationRepository(
        projectId: String,
        repoName: String,
        federationId: String
    ): TFederatedRepository {
        return localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, federationId)
    }

    /**
     * 确保同步未运行并开始同步
     */
    private fun ensureSyncNotRunningAndStart(projectId: String, repoName: String, federationId: String) {
        // 检查是否已经在进行全量同步
        if (localFederationManager.isFullSyncing(projectId, repoName, federationId)) {
            logger.warn(
                "Federation repository is already in full sync: $projectId/$repoName, federationId: $federationId"
            )
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_FULL_SYNC_RUNNING)
        }

        // 尝试设置同步状态为开始，使用原子操作防止并发
        if (!localFederationManager.updateFullSyncStart(projectId, repoName, federationId)) {
            logger.warn(
                "Failed to start full sync, another task may be " +
                    "in progress: $projectId/$repoName, federationId: $federationId"
            )
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_FULL_SYNC_RUNNING)
        }
    }

    /**
     * 提交同步任务
     */
    private fun submitSyncTask(
        projectId: String,
        repoName: String,
        federationId: String,
        federationRepository: TFederatedRepository
    ) {
        try {
            executor.execute(
                createSyncRunnable(projectId, repoName, federationId, federationRepository).trace()
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to submit federation sync task for" +
                    " $projectId/$repoName, id: $federationId, error: ${e.message}", e
            )
            localFederationManager.updateFullSyncEnd(projectId, repoName, federationId)
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_FULL_SYNC_FAILED)
        }
    }

    /**
     * 创建同步任务的Runnable
     */
    private fun createSyncRunnable(
        projectId: String,
        repoName: String,
        federationId: String,
        federationRepository: TFederatedRepository
    ): Runnable {
        return Runnable {
            try {
                executeFederationSync(federationRepository)
                logger.info("Full sync completed for federation: $projectId/$repoName, federationId: $federationId")
            } catch (e: Exception) {
                logger.error(
                    "Failed to execute federation tasks for " +
                        "$projectId/$repoName, id: $federationId, error: ${e.message}", e
                )
                throw e
            } finally {
                // 无论成功还是失败，都要更新同步状态为完成
                localFederationManager.updateFullSyncEnd(projectId, repoName, federationId)
            }
        }
    }

    /**
     * 执行联邦同步
     */
    private fun executeFederationSync(federationRepository: TFederatedRepository) {
        val futures = federationRepository.federatedClusters.mapNotNull { fedCluster ->
            createClusterSyncTask(fedCluster)
        }

        // 等待所有任务完成
        futures.forEach { it.get() }
    }

    /**
     * 为单个集群创建同步任务
     */
    private fun createClusterSyncTask(fedCluster: FederatedCluster): java.util.concurrent.Future<*>? {
        val taskInfo = replicaTaskService.getByTaskId(fedCluster.taskId!!)
        return taskInfo?.let { task ->
            executor.submit(Callable {
                val taskDetail = replicaTaskService.getDetailByTaskKey(task.key)
                federationFullSyncReplicaJobExecutor.execute(taskDetail)
            }.trace())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationSyncManager::class.java)
    }
}