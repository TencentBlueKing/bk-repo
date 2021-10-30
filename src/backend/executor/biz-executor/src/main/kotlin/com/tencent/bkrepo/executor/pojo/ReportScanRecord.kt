package com.tencent.bkrepo.executor.pojo

import java.time.LocalDateTime

data class ReportScanRecord(
    val taskId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val content: String,
    val status: Boolean?,
    val createAt: LocalDateTime
)
