package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.ReplicationSetting
import com.tencent.bkrepo.replication.pojo.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.ReplicationType
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("replica_task")
data class TReplicaTask(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    var type: ReplicationType,
    var setting: ReplicationSetting,
    var status: ReplicationStatus,
    val replicaProgress: ReplicaProgress,
    var startTime: LocalDateTime? = null,
    var endTime: LocalDateTime? = null,
    var errorReason: String? = null
)
