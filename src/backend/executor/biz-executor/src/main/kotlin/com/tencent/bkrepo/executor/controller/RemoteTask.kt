package com.tencent.bkrepo.executor.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.executor.config.ExecutorConfig
import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.request.FileScanRequest
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.request.RepoScanRequest
import com.tencent.bkrepo.executor.pojo.response.FileScanResponse
import com.tencent.bkrepo.executor.service.Task
import com.tencent.bkrepo.executor.util.TaskIdUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RemoteTask @Autowired constructor() {

    @Autowired
    lateinit var remoteTask: Task

    @Autowired
    lateinit var config: ExecutorConfig

    @PostMapping("/scan/file")
    @Principal(PrincipalType.ADMIN)
    fun runByFile(@RequestBody scanRequest: FileScanRequest): Response<FileScanResponse> {
        with(scanRequest) {
            val runTaskId = taskId ?: TaskIdUtil.build()
            val context = FileScanContext(
                taskId = runTaskId,
                config = config,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath
            )
            return ResponseBuilder.success(remoteTask.runFile(context))
        }
    }

    @PostMapping("/scan/repo")
    @Principal(PrincipalType.ADMIN)
    fun runByRepo(@RequestBody scanRequest: RepoScanRequest): Response<String> {
        with(scanRequest) {
            val runTaskId = taskId ?: TaskIdUtil.build()
            val context = RepoScanContext(
                taskId = runTaskId,
                config = config,
                projectId = projectId,
                repoName = repoName,
                name = name,
                rule = rule
            )
            val result = remoteTask.runRepo(context)
            return ResponseBuilder.success(result)
        }
    }
}
