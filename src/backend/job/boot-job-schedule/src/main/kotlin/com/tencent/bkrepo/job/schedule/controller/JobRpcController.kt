package com.tencent.bkrepo.job.schedule.controller

import com.tencent.devops.api.pojo.Response
import com.tencent.devops.schedule.constants.SERVER_BASE_PATH
import com.tencent.devops.schedule.constants.SERVER_RPC_V1
import com.tencent.devops.schedule.manager.JobManager
import com.tencent.devops.schedule.pojo.job.JobCreateRequest
import com.tencent.devops.schedule.pojo.job.JobInfo
import com.tencent.devops.schedule.pojo.job.JobQueryParam
import com.tencent.devops.schedule.pojo.job.JobUpdateRequest
import com.tencent.devops.schedule.pojo.page.Page
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$SERVER_BASE_PATH$SERVER_RPC_V1")
class JobRpcController(
    private val jobManager: JobManager,
) {
    @GetMapping("/job/list")
    fun list(param: JobQueryParam): Response<Page<JobInfo>> {
        return Response.success(jobManager.listJobPage(param))
    }

    @PostMapping("/job/create")
    fun create(@RequestBody request: JobCreateRequest): Response<String> {
        return Response.success(jobManager.createJob(request))
    }

    @PostMapping("/job/update")
    fun update(@RequestBody request: JobUpdateRequest): Response<Void> {
        jobManager.updateJob(request)
        return Response.success()
    }

    @DeleteMapping("/job/delete")
    fun delete(@RequestParam id: String): Response<Void> {
        jobManager.deleteJob(id)
        return Response.success()
    }

    @PostMapping("/job/stop")
    fun stop(@RequestParam id: String): Response<Void> {
        jobManager.stopJob(id)
        return Response.success()
    }

    @PostMapping("/job/start")
    fun start(@RequestParam id: String): Response<Void> {
        jobManager.startJob(id)
        return Response.success()
    }
}
