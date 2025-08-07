package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.job.schedule.api.JobScheduleClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TranscodeHelper(jobScheduleClient: JobScheduleClient) {
    init {
        Companion.jobScheduleClient = jobScheduleClient
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeHelper::class.java)
        private lateinit var jobScheduleClient: JobScheduleClient

        fun addTask(
            jobId: String,
            transcodeParam: List<TranscodeParam>,
        ) {
            val jobParam = transcodeParam.toJsonString()
            jobScheduleClient.triggerJob(jobId, jobParam)
            logger.debug("Add transcode task {}", transcodeParam)
        }
    }
}
