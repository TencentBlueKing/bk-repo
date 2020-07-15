package com.tencent.bkrepo.replication.pojo.log

import com.tencent.bkrepo.replication.pojo.task.ReplicationProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus

data class ReplicationTaskLog(
    val taskKey: String,
    var status: ReplicationStatus,
    var replicationProgress: ReplicationProgress,
    var startTime: String,
    var endTime: String? = null,
    var errorReason: String? = null

)