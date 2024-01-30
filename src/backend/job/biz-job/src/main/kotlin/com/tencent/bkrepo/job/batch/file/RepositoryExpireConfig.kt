package com.tencent.bkrepo.job.batch.file

import org.springframework.util.unit.DataSize

data class RepositoryExpireConfig(
    var repos: List<RepoConfig> = mutableListOf(),
    var size: DataSize = DataSize.ofGigabytes(10),
    var max: Int = 100000,
    var cacheTime: Long = 60000,
)

data class RepoConfig(
    var projectId: String = "",
    var repoName: String = "",
    // 路径前缀匹配
    var pathPrefix: List<String> = emptyList(),
    // 保留最近多少天内访问
    var days: Int = 30,
)
