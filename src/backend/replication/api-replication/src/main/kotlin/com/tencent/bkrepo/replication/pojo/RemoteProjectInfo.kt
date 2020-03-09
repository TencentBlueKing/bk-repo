package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.project.ProjectInfo

data class RemoteProjectInfo(
    val project: ProjectInfo,
    val repoList: List<RemoteRepoInfo>
)
