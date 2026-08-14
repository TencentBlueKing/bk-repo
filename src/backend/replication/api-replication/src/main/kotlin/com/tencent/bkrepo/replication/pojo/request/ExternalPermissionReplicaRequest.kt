package com.tencent.bkrepo.replication.pojo.request

data class ExternalPermissionReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val id: String? = null,
    val url: String = "",
    val headers: Map<String, String>? = null,
    val projectId: String = "",
    val repoName: String = "",
    val scope: String = "",
    val platformWhiteList: List<String>? = null,
    val enabled: Boolean = false,
)
