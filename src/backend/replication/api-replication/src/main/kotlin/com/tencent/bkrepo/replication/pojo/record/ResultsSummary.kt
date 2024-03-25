package com.tencent.bkrepo.replication.pojo.record

data class ResultsSummary(
    val replicaOverview: ReplicaOverview,
    val errorReason: String?,
    val status: ExecutionStatus
)
