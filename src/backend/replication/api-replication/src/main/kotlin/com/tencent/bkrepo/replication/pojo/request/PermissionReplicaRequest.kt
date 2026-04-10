package com.tencent.bkrepo.replication.pojo.request

data class PermissionReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val resourceType: String,
    val projectId: String? = null,
    val permName: String,
    val repos: List<String> = emptyList(),
    val includePattern: List<String> = emptyList(),
    val excludePattern: List<String> = emptyList(),
    val users: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val departments: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val createBy: String = "",
    val updatedBy: String = ""
)
