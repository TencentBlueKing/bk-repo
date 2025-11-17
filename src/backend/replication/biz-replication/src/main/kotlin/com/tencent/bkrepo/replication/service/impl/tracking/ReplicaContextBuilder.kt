package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import java.time.LocalDateTime

/**
 * ReplicaContext 构建器
 * 负责从跟踪记录构建 ReplicaContext
 */
class ReplicaContextBuilder(
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val localDataManager: LocalDataManager,
    private val replicationProperties: ReplicationProperties
) {

    /**
     * 根据跟踪记录构建 ReplicaContext
     */
    fun build(record: TFederationMetadataTracking, clusterNodeInfo: ClusterNodeInfo): ReplicaContext {
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
}

