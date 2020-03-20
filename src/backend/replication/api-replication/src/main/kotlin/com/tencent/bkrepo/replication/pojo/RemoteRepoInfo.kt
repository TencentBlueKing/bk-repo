package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

data class RemoteRepoInfo(
    val repo: RepositoryInfo,
    val count: Long
)
