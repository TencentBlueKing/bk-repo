package com.tencent.bkrepo.executor.pojo.context

import com.tencent.bkrepo.executor.pojo.enums.TaskRunStatus

data class ScanTaskContext(
    val taskId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val status: TaskRunStatus
)
