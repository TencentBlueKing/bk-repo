package com.tencent.bkrepo.replication.pojo

data class ReplicationRepo(
    val remoteProjectId: String,
    val remoteRepoName: String,
    val includeAllNode: Boolean = true,
    val selfProjectId: String? = null,
    val selfRepoName: String? = null
)
