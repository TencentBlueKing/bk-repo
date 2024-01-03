package com.tencent.bkrepo.job.pojo

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("job_failover")
@CompoundIndex(name = "name_idx", def = "{'name': 1}", background = true)
data class TJobFailover(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var name: String,
    var success: Long,
    var failed: Long,
    var total: Long,
    var data: String?,
)
