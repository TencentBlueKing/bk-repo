package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.project.ProjectInfo

data class ReplicationProjectDetail(
    val remoteProject: ProjectInfo,
    val repoList: List<ReplicationRepoDetail>,
    val selfProjectId: String
)
