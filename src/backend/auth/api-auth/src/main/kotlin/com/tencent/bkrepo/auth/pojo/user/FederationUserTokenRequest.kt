package com.tencent.bkrepo.auth.pojo.user

/**
 * 联邦同步写入用户 token 的请求体。
 * hashedTokenId 放 body，避免出现在 URL/access log。
 */
data class FederationUserTokenRequest(
    val hashedTokenId: String,
    val createdAt: String? = null,
    val expiredAt: String? = null,
)
