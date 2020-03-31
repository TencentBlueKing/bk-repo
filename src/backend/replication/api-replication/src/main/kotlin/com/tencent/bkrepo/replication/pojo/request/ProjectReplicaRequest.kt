package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class ProjectReplicaRequest(
    val name: String,
    val displayName: String,
    val description: String? = null,
    override val actionType: ActionType = ActionType.CREATE_OR_UPDATE,
    override val userId: String = SYSTEM_USER
) : ReplicaRequest(actionType, userId)
