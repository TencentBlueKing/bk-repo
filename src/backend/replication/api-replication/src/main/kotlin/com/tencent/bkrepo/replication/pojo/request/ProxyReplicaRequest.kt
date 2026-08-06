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
    /** AES 加密后的 secretKey，与本端存储格式一致 */
    val secretKey: String? = null,
)
