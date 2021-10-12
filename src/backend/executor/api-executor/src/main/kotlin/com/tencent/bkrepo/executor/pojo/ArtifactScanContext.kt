package com.tencent.bkrepo.executor.pojo

data class ArtifactScanContext(
    val taskId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String
)
