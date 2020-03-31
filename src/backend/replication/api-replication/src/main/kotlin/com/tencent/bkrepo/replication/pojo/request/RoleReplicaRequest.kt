package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.auth.pojo.enums.RoleType

data class RoleReplicaRequest(
    val roleId: String,
    val name: String,
    val type: RoleType,
    val projectId: String,
    val repoName: String? = null,
    val admin: Boolean
)
