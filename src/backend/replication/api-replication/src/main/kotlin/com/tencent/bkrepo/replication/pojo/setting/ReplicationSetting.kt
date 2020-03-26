package com.tencent.bkrepo.replication.pojo.setting

data class ReplicationSetting (
    val includeMetadata: Boolean = true,
    val includePermission: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.SKIP,
    val remoteClusterInfo: RemoteClusterInfo,
    val executionPlan: ExecutionPlan = ExecutionPlan()
)
