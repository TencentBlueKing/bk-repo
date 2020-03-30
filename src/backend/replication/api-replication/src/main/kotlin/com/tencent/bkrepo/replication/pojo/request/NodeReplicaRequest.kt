package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class NodeReplicaRequest(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val expires: Long = 0,
    val size: Long,
    val sha256: String,
    val md5: String,
    val metadata: Map<String, String> = mutableMapOf(),
    val userId: String = SYSTEM_USER
)