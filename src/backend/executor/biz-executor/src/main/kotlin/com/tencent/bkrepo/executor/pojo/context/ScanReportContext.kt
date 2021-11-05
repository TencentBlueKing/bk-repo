package com.tencent.bkrepo.executor.pojo.context

import com.tencent.bkrepo.executor.pojo.enums.ScanTaskReport

data class ScanReportContext(
    val taskId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    var report: ScanTaskReport?
)
