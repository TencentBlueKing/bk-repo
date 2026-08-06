package com.tencent.bkrepo.auth.pojo.proxy

/** 联邦同步专用：含加密 secretKey */
data class FederationProxyInfo(
    val name: String,
    val displayName: String,
    val projectId: String,
    val clusterName: String,
    val domain: String,
    val syncRateLimit: Long = -1L,
    val syncTimeRange: String = "0-24",
    val cacheExpireDays: Int = 7,
    val secretKey: String,
)
