package com.tencent.bkrepo.replication.pojo.request

data class UserTokenReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val userId: String,
    val tokenName: String,
    /** sm3 hashed token id，与本地存储格式一致 */
    val hashedTokenId: String = "",
    val createdAt: String = "",
    val expiredAt: String? = null,
)
