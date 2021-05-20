package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.task.ArtifactReplicationFailLevel
import com.tencent.bkrepo.replication.pojo.task.ArtifactReplicationResult
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("replication_task_log_detail")
class wTReplicationTaskLogDetail(
    var id: String? = null,
    @Indexed
    var taskLogKey: String,
    var status: ArtifactReplicationResult,
    var masterName: String,
    var slaveName: String,
    var projectId: String,
    var repoName: String?,
    var packageName: String?,
    var packageKey: String?,
    var type: PackageType?,
    var version: String?,
    var failLevelArtifact: ArtifactReplicationFailLevel? = null,
    var errorReason: String? = null
)
