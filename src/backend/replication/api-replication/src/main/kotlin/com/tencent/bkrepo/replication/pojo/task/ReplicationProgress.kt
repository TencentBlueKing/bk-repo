package com.tencent.bkrepo.replication.pojo.task

data class ReplicationProgress(
    var totalRepo: Int = 0,
    var totalNode: Long = 0,

    var replicatedProject: Long = 0,
    var successProject: Long = 0,
    var failedProject: Long = 0,

    var replicatedRepo: Long = 0,
    var successRepo: Long = 0,
    var failedRepo: Long = 0,

    var replicatedNode: Long = 0,
    var successNode: Long = 0,
    var conflictedNode: Long = 0,
    var failedNode: Long = 0,

    var replicatedPackageVersion: Long = 0,
    var successPackageVersion: Long = 0,
    var conflictedPackageVersion: Long = 0,
    var failedPackageVersion: Long = 0
)
