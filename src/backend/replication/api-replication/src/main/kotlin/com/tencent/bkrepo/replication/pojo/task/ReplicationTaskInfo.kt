package com.tencent.bkrepo.replication.pojo.task

import com.tencent.bkrepo.replication.pojo.setting.ReplicationSetting

data class ReplicationTaskInfo(
    var id: String,
    var createdBy: String,
    var createdDate: String,
    var lastModifiedBy: String,
    var lastModifiedDate: String,

    val includeAllProject: Boolean,
    val localProjectId: String? = null,
    val localRepoName: String? = null,
    val remoteProjectId: String? = null,
    val remoteRepoName: String? = null,

    val type: ReplicationType,
    val setting: ReplicationSetting,
    val status: ReplicationStatus,
    val replicationProgress: ReplicationProgress,
    val startTime: String? = null,
    val endTime: String? = null,
    val errorReason: String? = null
)
