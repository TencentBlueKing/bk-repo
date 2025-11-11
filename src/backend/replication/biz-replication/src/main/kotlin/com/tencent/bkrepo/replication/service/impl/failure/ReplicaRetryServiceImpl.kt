package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.TaskExecuteType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.type.ReplicaService
import com.tencent.bkrepo.replication.replica.type.event.EventBasedReplicaService
import com.tencent.bkrepo.replication.replica.type.federation.FederationBasedReplicaService
import com.tencent.bkrepo.replication.replica.type.schedule.ScheduledReplicaService
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 同步重试服务实现
 * 根据失败记录构建ReplicaContext，调用对应的replicaService.replica(context)进行重试
 */
@Service
class ReplicaRetryServiceImpl(
    private val replicaTaskService: ReplicaTaskService,
    private val clusterNodeService: ClusterNodeService,
    private val replicaRecordService: ReplicaRecordService,
    private val localDataManager: LocalDataManager,
    private val replicationProperties: ReplicationProperties,
    private val eventBasedReplicaService: EventBasedReplicaService,
    private val federationBasedReplicaService: FederationBasedReplicaService,
    private val scheduledReplicaService: ScheduledReplicaService,
) : ReplicaRetryService {

    override fun retryFailureRecord(failureRecord: TReplicaFailureRecord): Boolean {
        logger.info("Retrying failure record[${failureRecord.id}], taskKey[${failureRecord.taskKey}]")

        return try {
            // 根据taskKey查询任务详情
            val taskDetail = replicaTaskService.getDetailByTaskKey(failureRecord.taskKey)
            val replicaType = taskDetail.task.replicaType

            // 验证重试前置条件
            if (!validateRetryPreconditions(failureRecord, taskDetail)) {
                return false
            }

            // 检查远程集群是否存在
            val remoteCluster = clusterNodeService.getByClusterId(failureRecord.remoteClusterId)
            if (remoteCluster == null) {
                logger.warn("Remote cluster[${failureRecord.remoteClusterId}] does not exist, skip retry")
                return false
            }

            // 验证远程集群是否在任务的远程集群列表中
            if (!validateRemoteClusterInTask(failureRecord, taskDetail)) {
                return false
            }

            // 根据任务类型获取对应的replicaService
            val replicaService = getReplicaService(replicaType) ?: run {
                logger.warn("ReplicaService for type[$replicaType] is not available, skip retry")
                return false
            }

            // 构建ReplicaContext并调用replica方法进行重试
            buildAndRetry(taskDetail, failureRecord, remoteCluster, replicaService)
        } catch (e: Exception) {
            logger.error("Failed to retry failure record[${failureRecord.id}]: ${e.message}", e)
            false
        }
    }

    /**
     * 验证重试前置条件
     */
    private fun validateRetryPreconditions(
        failureRecord: TReplicaFailureRecord,
        taskDetail: ReplicaTaskDetail
    ): Boolean {
        // 检查任务是否启用
        if (!taskDetail.task.enabled) {
            logger.warn("Task[${failureRecord.taskKey}] is disabled, skip retry")
            return false
        }
        return true
    }

    /**
     * 验证远程集群是否在任务的远程集群列表中
     */
    private fun validateRemoteClusterInTask(
        failureRecord: TReplicaFailureRecord,
        taskDetail: ReplicaTaskDetail
    ): Boolean {
        val clusterNodeName = taskDetail.task.remoteClusters.find { it.id == failureRecord.remoteClusterId }
        if (clusterNodeName == null) {
            logger.warn(
                "Remote cluster[${failureRecord.remoteClusterId}] is not in task[${failureRecord.taskKey}] " +
                    "remote clusters list, skip retry"
            )
            return false
        }
        return true
    }

    /**
     * 根据任务类型获取对应的ReplicaService
     */
    private fun getReplicaService(replicaType: ReplicaType): ReplicaService? {
        return when (replicaType) {
            ReplicaType.REAL_TIME -> eventBasedReplicaService
            ReplicaType.FEDERATION -> federationBasedReplicaService
            ReplicaType.SCHEDULED -> scheduledReplicaService
            else -> {
                logger.warn("task type is not supported for retry")
                null
            }
        }
    }

    /**
     * 构建ReplicaContext并调用replica方法进行重试
     */
    private fun buildAndRetry(
        taskDetail: ReplicaTaskDetail,
        failureRecord: TReplicaFailureRecord,
        remoteCluster: ClusterNodeInfo,
        replicaService: ReplicaService
    ): Boolean {
        return try {
            // 获取本地仓库信息
            val localRepo = localDataManager.findRepoByName(
                failureRecord.projectId,
                failureRecord.repoName
            )

            // 从taskDetail中找到匹配的taskObject以获取仓库类型等信息
            val matchedTaskObject = taskDetail.objects.firstOrNull { it.localRepoName == failureRecord.repoName }
            val repoType = matchedTaskObject?.repoType ?: localRepo.type

            // 构建ReplicaObjectInfo
            val taskObject = ReplicaObjectInfo(
                localRepoName = failureRecord.repoName,
                remoteProjectId = failureRecord.remoteProjectId,
                remoteRepoName = failureRecord.remoteRepoName,
                repoType = repoType,
                packageConstraints = failureRecord.packageConstraint?.let { listOf(it) },
                pathConstraints = failureRecord.pathConstraint?.let { listOf(it) },
                sourceFilter = matchedTaskObject?.sourceFilter
            )

            // 创建或查找任务记录
            val taskRecord = replicaRecordService.findOrCreateLatestRecord(taskDetail.task.key)
                .copy(startTime = LocalDateTime.now())

            // 构建ReplicaContext
            val context = ReplicaContext(
                taskDetail = taskDetail,
                taskObject = taskObject,
                taskRecord = taskRecord,
                localRepo = localRepo,
                remoteCluster = remoteCluster,
                replicationProperties = replicationProperties
            )

            // 如果有event，设置到context中
            failureRecord.event?.let {
                context.event = it
                context.executeType = TaskExecuteType.DELTA
            }

            // 设置失败记录ID，用于在重试成功后删除记录
            context.failedRecordId = failureRecord.id

            // 调用replicaService.replica(context)进行重试
            replicaService.replica(context)

            // 检查执行结果
            val success = context.status != ExecutionStatus.FAILED
            if (success) {
                logger.info(
                    "Successfully retried failure record[${failureRecord.id}], " +
                        "taskKey[${failureRecord.taskKey}]"
                )
            } else {
                logger.warn(
                    "Retry failed for record[${failureRecord.id}], taskKey[${failureRecord.taskKey}], " +
                        "error: ${context.errorMessage}"
                )
            }
            success
        } catch (e: Exception) {
            logger.error("Failed to retry failure record[${failureRecord.id}]: ${e.message}", e)
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaRetryServiceImpl::class.java)
    }
}
