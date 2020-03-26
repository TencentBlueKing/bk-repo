package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.setting.ReplicationSetting
import com.tencent.bkrepo.replication.pojo.task.ReplicationProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("replication_task")
data class TReplicationTask(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    val includeAllProject: Boolean,
    val localProjectId: String? = null,
    val localRepoName: String? = null,
    val remoteProjectId: String? = null,
    val remoteRepoName: String? = null,

    var type: ReplicationType,
    var setting: ReplicationSetting,
    var status: ReplicationStatus,
    val replicationProgress: ReplicationProgress,
    var startTime: LocalDateTime? = null,
    var endTime: LocalDateTime? = null,
    var errorReason: String? = null
)
