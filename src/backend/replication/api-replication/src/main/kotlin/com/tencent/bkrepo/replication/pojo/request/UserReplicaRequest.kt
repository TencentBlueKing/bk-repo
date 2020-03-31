package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.auth.pojo.Token

data class UserReplicaRequest(
    val userId: String,
    val name: String,
    val pwd: String,
    val admin: Boolean = false,
    val tokens: List<Token>
)
