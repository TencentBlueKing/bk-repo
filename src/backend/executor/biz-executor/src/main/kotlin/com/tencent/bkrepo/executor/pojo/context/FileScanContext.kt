package com.tencent.bkrepo.executor.pojo.context

import com.tencent.bkrepo.executor.config.ExecutorConfig

data class FileScanContext(
    val taskId: String,
    val config: ExecutorConfig,
    val projectId: String,
    val repoName: String,
    val fullPath: String
)
