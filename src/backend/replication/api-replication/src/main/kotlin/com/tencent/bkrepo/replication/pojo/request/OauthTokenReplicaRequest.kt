package com.tencent.bkrepo.replication.pojo.request

data class OauthTokenReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val accessToken: String = "",
    val refreshToken: String? = null,
    val expireSeconds: Long? = null,
    val type: String = "Bearer",
    val accountId: String = "",
    val userId: String = "",
    val scope: Set<String>? = null,
    val issuedAt: Long = 0L,
)
