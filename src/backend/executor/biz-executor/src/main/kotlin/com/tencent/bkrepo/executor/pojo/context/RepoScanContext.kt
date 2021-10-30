package com.tencent.bkrepo.executor.pojo.context

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.executor.config.ExecutorConfig

data class RepoScanContext(
    val taskId: String,
    val config: ExecutorConfig,
    val projectId: String,
    val repoName: String,
    val name: String?,
    val rule: OperationType?
)
