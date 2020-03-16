package com.tencent.bkrepo.replication.pojo

/**
 * 远程集群信息
 */
data class RemoteClusterInfo(
    val url: String,
    val cert: String? = null,
    val accessKey: String,
    val secretKey: String
)
