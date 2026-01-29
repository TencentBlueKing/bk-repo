package com.tencent.bkrepo.media.job.controller

import com.tencent.bkrepo.media.job.service.TranscodeJobService
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 管理员任务控制器
 * */
@RestController
@RequestMapping("/api/media/job")
class JobController(
    private val transcodeJobService: TranscodeJobService
) {
    @PutMapping("/restart")
    fun restart(
        @RequestAttribute userId: String,
        @RequestBody ids: Set<String>,
    ) {
        transcodeJobService.restartJob(ids)
    }
}