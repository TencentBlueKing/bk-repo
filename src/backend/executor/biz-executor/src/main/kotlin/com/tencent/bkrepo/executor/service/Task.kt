package com.tencent.bkrepo.executor.service

import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.response.FileScanResponse

interface Task {

    fun runFile(context: FileScanContext): FileScanResponse

    fun runRepo(context: RepoScanContext): String
}
