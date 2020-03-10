package com.tencent.bkrepo.replication.pojo

data class ReplicaProgress(
    var totalProject: Int = 0,
    var totalRepo: Int = 0,
    var totalNode: Long = 0,
    var replicatedProject: Long = 0,
    var replicatedRepo: Long = 0,
    var successNode: Long = 0,
    var conflictedNode: Long = 0,
    var errorNode: Long = 0
) {
    fun getReplicatedNode() = successNode + conflictedNode + errorNode
}
