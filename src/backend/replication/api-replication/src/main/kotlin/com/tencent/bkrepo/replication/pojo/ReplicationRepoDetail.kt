package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

data class ReplicationRepoDetail(
    val localRepoDetail: RepositoryDetail,
    val fileCount: Long,
    val remoteRepoName: String
)
