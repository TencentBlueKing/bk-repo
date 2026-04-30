package com.tencent.bkrepo.replication.pojo.request

data class RoleReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val id: String? = null,
    val roleId: String = "",
    val name: String = "",
    val type: String = "PROJECT",
    val projectId: String? = null,
    val repoName: String? = null,
    val admin: Boolean = false,
    val users: List<String> = emptyList(),
    val description: String? = null,
)
