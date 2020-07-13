package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.repository.constant.SYSTEM_USER

data class NodeReplicaRequest(
    val expires: Long = 0,
    val size: Long,
    val sha256: String,
    val md5: String,
    val metadata: Map<String, String> = mutableMapOf(),
    override val actionType: ActionType = ActionType.CREATE,
    override val userId: String = SYSTEM_USER
) : ReplicaRequest(actionType, userId)
