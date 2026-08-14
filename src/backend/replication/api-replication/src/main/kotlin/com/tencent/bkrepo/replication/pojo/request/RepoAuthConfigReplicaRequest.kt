package com.tencent.bkrepo.replication.pojo.request

data class RepoAuthConfigReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val id: String = "",
    val projectId: String = "",
    val repoName: String = "",
    val accessControlMode: String = "DEFAULT",
    val officeDenyGroupSet: Set<String> = emptySet(),
    val bkiamv3Check: Boolean = false
)
