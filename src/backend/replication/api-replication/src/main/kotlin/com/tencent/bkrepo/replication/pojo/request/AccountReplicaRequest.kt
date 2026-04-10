package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.auth.pojo.token.CredentialSet

data class AccountReplicaRequest(
    val action: ReplicaAction = ReplicaAction.UPSERT,
    val appId: String = "",
    val locked: Boolean = false,
    val authorizationGrantTypes: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val redirectUri: String? = null,
    val avatarUrl: String? = null,
    val scope: Set<String>? = null,
    val description: String? = null,
    /** 携带真实 credentials，确保联邦集群能正常鉴权 */
    val credentials: List<CredentialSet>? = null,
)
