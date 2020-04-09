package com.tencent.bkrepo.replication.pojo.setting

/**
 * 远程集群信息
 */
data class RemoteClusterInfo(
    val url: String,
    val certificate: String? = null,
    val username: String,
    val password: String
)
