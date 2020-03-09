package com.tencent.bkrepo.replication.pojo

data class ReplicaTaskInfo(
    var id: String,
    var createdBy: String,
    var createdDate: String,
    var lastModifiedBy: String,
    var lastModifiedDate: String,

    var type: ReplicationType,
    var setting: ReplicationSetting,
    var status: ReplicationStatus,
    val replicaProgress: ReplicaProgress,
    var startTime: String? = null,
    var endTime: String? = null,
    var errorReason: String? = null
)
