package com.tencent.bkrepo.executor.pojo.response

data class FileRunResponse(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val status: TaskRunResponse
)
