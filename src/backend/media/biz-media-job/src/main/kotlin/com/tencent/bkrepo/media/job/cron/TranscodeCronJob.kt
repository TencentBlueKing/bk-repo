package com.tencent.bkrepo.media.job.cron

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * 定时下发转码任务
 */
@Service
class TranscodeCronJob {

    @Scheduled(fixedDelay = 5000)
    fun startTranscodeJob() {
    }
}