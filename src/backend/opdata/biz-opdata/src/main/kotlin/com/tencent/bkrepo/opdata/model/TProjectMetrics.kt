package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import org.springframework.data.mongodb.core.mapping.Document

@Document("project_metrics")
data class TProjectMetrics(
    var projectId: String,
    var nodeNum: Long,
    var capSize: Long,
    val repoMetrics: List<RepoMetrics>
)
