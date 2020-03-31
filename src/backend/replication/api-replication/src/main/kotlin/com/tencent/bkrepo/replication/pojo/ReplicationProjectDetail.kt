package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.project.ProjectInfo

data class ReplicationProjectDetail(
    val localProjectInfo: ProjectInfo,
    val repoDetailList: List<ReplicationRepoDetail>,
    val remoteProjectId: String
)
