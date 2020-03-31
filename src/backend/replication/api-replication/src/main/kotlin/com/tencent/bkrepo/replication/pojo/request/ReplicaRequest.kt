package com.tencent.bkrepo.replication.pojo.request

abstract class ReplicaRequest(
    open val actionType: ActionType,
    open val userId: String
)
