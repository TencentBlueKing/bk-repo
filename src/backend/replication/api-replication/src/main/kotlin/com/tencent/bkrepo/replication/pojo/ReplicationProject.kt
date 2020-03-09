package com.tencent.bkrepo.replication.pojo

data class ReplicationProject(
    val remoteProjectId: String,
    val selfProjectId: String? = null
)
