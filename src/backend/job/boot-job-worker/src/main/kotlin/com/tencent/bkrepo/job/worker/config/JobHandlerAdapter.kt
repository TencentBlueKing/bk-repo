package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.devops.schedule.executor.JobContext
import com.tencent.devops.schedule.executor.JobHandler
import com.tencent.devops.schedule.pojo.job.JobExecutionResult

/**
 * JobHandler适配器，将原有的BatchJob，转换成JobHandler,以支持devops framework调度框架调度
 * */
class JobHandlerAdapter(private val batchJob: BatchJob<*>) : JobHandler {
    override fun execute(context: JobContext): JobExecutionResult {
        batchJob.start()
        return JobExecutionResult.success()
    }
}
