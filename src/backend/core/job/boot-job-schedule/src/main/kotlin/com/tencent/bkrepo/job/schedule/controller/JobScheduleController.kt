package com.tencent.bkrepo.job.schedule.controller

import com.tencent.bkrepo.job.schedule.api.JobScheduleClient
import com.tencent.devops.schedule.manager.JobManager
import org.springframework.web.bind.annotation.RestController

@RestController
class JobScheduleController(
    private val jobManager: JobManager,
) : JobScheduleClient {
    override fun triggerJob(id: String, executorParam: String) {
        jobManager.triggerJob(id, executorParam)
    }
}
