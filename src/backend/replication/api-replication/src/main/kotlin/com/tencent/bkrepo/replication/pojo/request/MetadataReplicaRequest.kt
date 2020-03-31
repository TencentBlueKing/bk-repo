package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class MetadataReplicaRequest(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val metadata: Map<String, String> = mutableMapOf(),
    override val actionType: ActionType = ActionType.CREATE_OR_UPDATE,
    override val userId: String = SYSTEM_USER
) : ReplicaRequest(actionType, userId)
