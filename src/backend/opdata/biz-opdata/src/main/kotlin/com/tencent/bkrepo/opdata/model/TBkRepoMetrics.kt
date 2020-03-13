package com.tencent.bkrepo.opdata.model

import org.springframework.data.mongodb.core.mapping.Document

@Document("bkrepo_metrics")
data class TBkRepoMetrics(
    var date: String,
    var projectNum: Long,
    var nodeNum: Long,
    var capSize: Long
)
