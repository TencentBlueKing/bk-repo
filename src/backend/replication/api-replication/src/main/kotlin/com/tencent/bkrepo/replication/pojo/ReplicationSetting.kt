package com.tencent.bkrepo.replication.pojo

/**
 * 同步任务相关设置
 */
data class ReplicationSetting(
    val includeMetadata: Boolean = true,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.SKIP,
    val remoteClusterInfo: RemoteClusterInfo,
    val executionPlan: ExecutionPlan = ExecutionPlan(),
    val includeAllProject: Boolean = true,
    val replicationProjectReplicaList: List<ReplicationProject>? = null,
    val replicationList: List<ReplicationRepo>? = null
)
