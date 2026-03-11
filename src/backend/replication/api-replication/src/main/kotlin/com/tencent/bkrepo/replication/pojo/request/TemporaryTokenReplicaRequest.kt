package com.tencent.bkrepo.replication.pojo.request

data class TemporaryTokenReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val projectId: String = "",
    val repoName: String = "",
    val fullPath: String = "",
    val token: String = "",
    val authorizedUserList: Set<String> = emptySet(),
    val authorizedIpList: Set<String> = emptySet(),
    val expireDate: String? = null,
    val permits: Int? = null,
    val type: String = "DOWNLOAD",
    val createdBy: String = "",
)
