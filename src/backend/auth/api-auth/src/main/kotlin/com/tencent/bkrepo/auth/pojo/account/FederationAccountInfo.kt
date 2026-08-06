package com.tencent.bkrepo.auth.pojo.account

import com.tencent.bkrepo.auth.pojo.enums.AccountLimit
import com.tencent.bkrepo.auth.pojo.token.CredentialSet

/**
 * 联邦同步专用账号 DTO，可携带真实 credentials。
 * 禁止用于用户态/对外 API；用户态请使用 [com.tencent.bkrepo.auth.pojo.account.Account]。
 */
data class FederationAccountInfo(
    val id: String? = null,
    val appId: String,
    val locked: Boolean = false,
    val authorizationGrantTypes: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val redirectUri: String? = null,
    val avatarUrl: String? = null,
    val scope: Set<String>? = null,
    val limit: AccountLimit? = null,
    val description: String? = null,
    val credentials: List<CredentialSet>? = null,
)
