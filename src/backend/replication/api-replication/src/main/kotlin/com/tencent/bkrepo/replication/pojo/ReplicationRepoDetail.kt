package com.tencent.bkrepo.replication.pojo

import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

data class ReplicationRepoDetail(
    val remoteRepo: RepositoryInfo,
    val count: Long,
    val selfRepoName: String,
    val includeAllNode: Boolean = true
)
