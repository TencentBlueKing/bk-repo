package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

data class ReplicationRepoDetail(
    val localRepoInfo: RepositoryInfo,
    val fileCount: Long,
    val remoteRepoName: String
)
