package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.slf4j.LoggerFactory

abstract class CompositeMongoDbBatchJob<T>(
    private val properties: CompositeJobProperties
) : MongoDbBatchJob<T, CompositeJobContext<T>>(properties) {

    override fun doStart0(jobContext: CompositeJobContext<T>) {
        // start child job
        jobContext.childJobs.forEach {
            it.onParentJobStart(jobContext.childContext(it.getJobName()))
            logger.info("child job[${it.getJobName()}] started")
        }

        super.doStart0(jobContext)

        // finish child job
        jobContext.childJobs.forEach {
            logException { it.onParentJobFinished(jobContext.childContext(it.getJobName())) }
            logger.info("child job[${it.getJobName()}] finished")
        }
    }

    override fun run(row: T, collectionName: String, context: CompositeJobContext<T>) {
        context.childJobs.forEach {
            logException { it.run(row, collectionName, context.childContext(it.getJobName())) }
        }
    }

    override fun createJobContext(): CompositeJobContext<T> {
        val enabledJobs = properties.enabledChildJobs
        val disabledJobs = properties.disabledChildJobs
        val enabledChildJobs = createChildJobs().filter {
            val childJobName = it.getJobName()
            enabledJobs.isEmpty() && disabledJobs.isEmpty() ||
                enabledJobs.isNotEmpty() && childJobName in enabledJobs ||
                enabledJobs.isEmpty() && disabledJobs.isNotEmpty() && childJobName !in disabledJobs
        }
        return CompositeJobContext(enabledChildJobs)
    }

    protected abstract fun createChildJobs(): List<ChildMongoDbBatchJob<T>>

    @Suppress("TooGenericExceptionCaught")
    private fun logException(func: () -> Unit) {
        try {
            func()
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompositeMongoDbBatchJob::class.java)
    }
}
