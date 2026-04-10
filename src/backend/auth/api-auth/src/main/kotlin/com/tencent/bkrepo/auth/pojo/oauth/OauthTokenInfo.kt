package com.tencent.bkrepo.auth.pojo.oauth

data class OauthTokenInfo(
    val accessToken: String,
    val refreshToken: String? = null,
    val expireSeconds: Long? = null,
    val type: String,
    val accountId: String,
    val userId: String,
    val scope: Set<String>? = null,
    val issuedAt: Long,
)
