package com.tencent.bkrepo.job.worker.config

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.batch.base.JobExecuteContext
import com.tencent.bkrepo.job.worker.rpc.JobRpcClient
import com.tencent.devops.schedule.enums.ExecutionCodeEnum
import com.tencent.devops.schedule.executor.JobContext
import com.tencent.devops.schedule.executor.JobHandler
import com.tencent.devops.schedule.pojo.job.JobExecutionResult

/**
 * JobHandler适配器，将原有的BatchJob，转换成JobHandler,以支持devops framework调度框架调度
 * */
class JobHandlerAdapter<C : com.tencent.bkrepo.job.batch.base.JobContext>(
    private val batchJob: BatchJob<C>,
    private val jobRpcClient: JobRpcClient,
) :
    JobHandler {
    override fun execute(context: JobContext): JobExecutionResult {
        with(context) {
            val jobExecuteContext = JobExecuteContext(
                jobId = jobId,
                jobParamMap = jobParamMap,
                logId = logId,
                triggerTime = triggerTime,
                updateTime = updateTime,
                broadcastIndex = broadcastIndex,
                broadcastTotal = broadcastTotal,
                source = source,
                image = image,
            )
            val batchJobContext = batchJob.createJobContext()
            batchJobContext.executeContext = jobExecuteContext
            return if (jobRpcClient.lastExecutionWasCompleted(jobId, scheduledFireTime)) {
                batchJob.doStart(batchJobContext)
                JobExecutionResult(ExecutionCodeEnum.SUCCESS.code(), batchJobContext.toString())
            } else {
                JobExecutionResult(ExecutionCodeEnum.FAILED.code(), "Job[$jobId] in process, skip.")
            }
        }
    }
}
