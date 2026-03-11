package com.tencent.bkrepo.auth.pojo.account

import com.tencent.bkrepo.auth.pojo.token.CredentialSet

data class AccountInfo(
    val id: String? = null,
    val appId: String,
    val locked: Boolean = false,
    val authorizationGrantTypes: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val redirectUri: String? = null,
    val avatarUrl: String? = null,
    val scope: Set<String>? = null,
    val description: String? = null,
    /** 联邦同步专用：携带真实 credentials，用户态接口不填充此字段 */
    val credentials: List<CredentialSet>? = null,
)
