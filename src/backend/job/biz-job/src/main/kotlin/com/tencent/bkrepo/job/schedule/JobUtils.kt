package com.tencent.bkrepo.job.schedule

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.devops.schedule.enums.ScheduleTypeEnum
import org.springframework.scheduling.annotation.Scheduled

object JobUtils {
    fun parseBatchJob(job: BatchJob<*>): Job {
        with(job) {
            val type = if (batchJobProperties.cron != Scheduled.CRON_DISABLED) {
                JobScheduleType.CRON
            } else if (batchJobProperties.fixedDelay > 0) {
                JobScheduleType.FIX_DELAY
            } else if (batchJobProperties.fixedRate > 0) {
                JobScheduleType.FIX_RATE
            } else {
                error("Invalid job configuration")
            }
            val scheduleConf = when (type) {
                JobScheduleType.CRON -> {
                    batchJobProperties.cron
                }

                JobScheduleType.FIX_DELAY -> {
                    batchJobProperties.fixedDelay.toString()
                }

                JobScheduleType.FIX_RATE -> {
                    batchJobProperties.fixedRate.toString()
                }
            }
            return Job(
                name = getJobName(),
                runnable = this::start,
                scheduleConf = scheduleConf,
                scheduleType = type,
                group = batchJobProperties.workerGroup,
            )
        }
    }

    fun convertScheduleType(scheduleType: JobScheduleType): ScheduleTypeEnum {
        return when (scheduleType) {
            JobScheduleType.CRON -> ScheduleTypeEnum.CRON
            JobScheduleType.FIX_RATE -> ScheduleTypeEnum.FIX_RATE
            else -> error("Not support type[$scheduleType]")
        }
    }
}
