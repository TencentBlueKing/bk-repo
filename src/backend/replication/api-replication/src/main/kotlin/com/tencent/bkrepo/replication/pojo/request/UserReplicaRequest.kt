package com.tencent.bkrepo.replication.pojo.request

data class UserReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val userId: String,
    val name: String = "",
    val pwd: String? = null,
    val admin: Boolean = false,
    val asstUsers: List<String> = emptyList(),
    val group: Boolean = false,
    val email: String? = null,
    val phone: String? = null,
    val tenantId: String? = null
)
