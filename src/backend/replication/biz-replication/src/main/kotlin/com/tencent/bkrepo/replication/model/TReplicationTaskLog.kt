package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.task.ReplicationProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("replication_task_log")
data class TReplicationTaskLog(
    var id: String? = null,
    @Indexed
    var taskKey: String,
    var status: ReplicationStatus,
    var replicationProgress: ReplicationProgress,
    var startTime: LocalDateTime,
    var endTime: LocalDateTime? = null,
    var errorReason: String? = null
)
