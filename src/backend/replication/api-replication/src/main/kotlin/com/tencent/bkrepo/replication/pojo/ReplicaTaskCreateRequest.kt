package com.tencent.bkrepo.replication.pojo

data class ReplicaTaskCreateRequest(
    val type: ReplicationType = ReplicationType.FULL,
    val setting: ReplicationSetting
)
