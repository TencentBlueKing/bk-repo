package com.tencent.bkrepo.replication.pojo.request

data class PersonalPathReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val userId: String = "",
    val projectId: String = "",
    val repoName: String = "",
    val fullPath: String = "",
)
