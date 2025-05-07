package com.tencent.bkrepo.git.listener

import com.tencent.bkrepo.git.internal.storage.CodeRepository
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

data class SyncRepositoryEvent(
    val repositoryDetail: RepositoryDetail,
    val db: CodeRepository,
    val user: String
)
