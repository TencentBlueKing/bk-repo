package com.tencent.bkrepo.job.schedule.controller

import com.tencent.devops.api.pojo.Response
import com.tencent.devops.schedule.constants.SERVER_BASE_PATH
import com.tencent.devops.schedule.constants.SERVER_RPC_V1
import com.tencent.devops.schedule.manager.JobManager
import com.tencent.devops.schedule.pojo.job.JobInfo
import com.tencent.devops.schedule.web.JobController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$SERVER_BASE_PATH$SERVER_RPC_V1")
class JobRpcController(private val jobManager: JobManager) : JobController(jobManager) {
    @GetMapping("/job/{id}")
    fun get(@PathVariable id: String): Response<JobInfo?> {
        return Response.success(jobManager.findJobById(id))
    }
}
