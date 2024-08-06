package com.tencent.bkrepo.job.schedule.api

import com.tencent.bkrepo.common.api.constant.SCHEDULE_SERVICE_NAME
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@FeignClient(SCHEDULE_SERVICE_NAME, contextId = "JobScheduleClient")
@RequestMapping("/service/job")
interface JobScheduleClient {
    /**
     * 触发任务执行
     * @param id 任务id
     * @param executorParam 执行参数
     * */
    @PostMapping("/trigger/{id}")
    fun triggerJob(@PathVariable id: String, @RequestBody executorParam: String)
}
