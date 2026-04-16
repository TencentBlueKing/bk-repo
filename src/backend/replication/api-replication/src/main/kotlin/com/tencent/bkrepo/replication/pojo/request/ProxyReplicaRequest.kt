package com.tencent.bkrepo.replication.pojo.request

data class ProxyReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val name: String = "",
    val displayName: String = "",
    val projectId: String = "",
    val clusterName: String = "",
    val domain: String = "",
    val syncRateLimit: Long = -1L,
    val syncTimeRange: String = "0-24",
    val cacheExpireDays: Int = 7,
)
