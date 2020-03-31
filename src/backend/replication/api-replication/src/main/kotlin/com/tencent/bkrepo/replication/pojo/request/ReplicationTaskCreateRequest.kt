package com.tencent.bkrepo.replication.pojo.request

import com.tencent.bkrepo.replication.pojo.setting.ReplicationSetting
import com.tencent.bkrepo.replication.pojo.task.ReplicationType

data class ReplicationTaskCreateRequest(
    val type: ReplicationType = ReplicationType.FULL,
    val includeAllProject: Boolean,
    val localProjectId: String? = null,
    val localRepoName: String? = null,
    val remoteProjectId: String? = null,
    val remoteRepoName: String? = null,
    val setting: ReplicationSetting
)
