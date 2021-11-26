package com.tencent.bkrepo.executor.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.executor.config.ExecutorConfig
import com.tencent.bkrepo.executor.constant.SCAN_FILE_ENDPOINT
import com.tencent.bkrepo.executor.constant.SCAN_REPORT_ENDPOINT
import com.tencent.bkrepo.executor.constant.SCAN_REPO_ENDPOINT
import com.tencent.bkrepo.executor.constant.SCAN_STATUS_ENDPOINT
import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.request.FileScanRequest
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.context.ScanReportContext
import com.tencent.bkrepo.executor.pojo.request.RepoScanRequest
import com.tencent.bkrepo.executor.pojo.request.ScanReportRequest
import com.tencent.bkrepo.executor.pojo.response.TaskRunResponse
import com.tencent.bkrepo.executor.service.Task
import com.tencent.bkrepo.executor.util.TaskIdUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@RestController
class RemoteTask @Autowired constructor() {

    @Autowired
    lateinit var remoteTask: Task

    @Autowired
    lateinit var config: ExecutorConfig

    @PostMapping(SCAN_FILE_ENDPOINT)
    @Principal(PrincipalType.ADMIN)
    fun runByFile(@RequestBody request: FileScanRequest): Response<String> {
        with(request) {
            val runTaskId = TaskIdUtil.build()
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

    @PostMapping(SCAN_REPO_ENDPOINT)
    @Principal(PrincipalType.ADMIN)
    fun runByRepo(@RequestBody request: RepoScanRequest): Response<String> {
        with(request) {
            val runTaskId = TaskIdUtil.build()
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

    @GetMapping(SCAN_STATUS_ENDPOINT)
    @Principal(PrincipalType.ADMIN)
    fun getRunningStatus(
        @RequestParam taskId: String,
        @RequestParam pageNumber: Int?,
        @RequestParam pageSize: Int?
    ): Response<TaskRunResponse> {
        return ResponseBuilder.success(remoteTask.getTaskStatus(taskId, pageNumber, pageSize))
    }

    @GetMapping(SCAN_REPORT_ENDPOINT)
    @Principal(PrincipalType.ADMIN)
    fun getTaskReport(@RequestBody request: ScanReportRequest): Response<MutableList<*>?> {
        with(request) {
            val context = ScanReportContext(
                projectId = projectId,
                repoName = repoName,
                taskId = taskId,
                fullPath = fullPath,
                report = report
            )
            return ResponseBuilder.success(remoteTask.getTaskReport(context))
        }
    }
}
