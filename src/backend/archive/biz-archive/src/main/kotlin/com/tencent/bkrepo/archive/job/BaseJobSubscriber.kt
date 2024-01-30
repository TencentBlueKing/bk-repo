package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.reactivestreams.Subscription
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.SignalType
import reactor.util.context.Context
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch

/**
 * 通用任务订阅者
 * 完成任务的通用功能，比如context的维护，任务的起始记录等
 * */
open class BaseJobSubscriber<T> : BaseSubscriber<T>() {

    /**
     * 开始时间
     * */
    private var startAt: Long = -1L

    /**
     * 任务上下文
     * */
    protected val jobContext = JobContext()

    private fun getJobName(): String = javaClass.simpleName

    /**
     * 闭锁，用于同步任务
     * */
    private val countDownLatch = CountDownLatch(1)

    override fun hookOnSubscribe(subscription: Subscription) {
        LoggerHolder.jobLogger.info("Start execute job[${getJobName()}].")
        startAt = System.currentTimeMillis()
        ArchiveUtils.monitor.addMonitor(getJobName(), jobContext)
        super.hookOnSubscribe(subscription)
    }

    override fun hookOnNext(value: T) {
        try {
            doOnNext(value)
            jobContext.success.incrementAndGet()
        } catch (e: Exception) {
            LoggerHolder.jobLogger.error("DoOnNext error: ", e)
            jobContext.failed.incrementAndGet()
        } finally {
            jobContext.total.incrementAndGet()
            jobContext.totalSize.addAndGet(getSize(value))
        }
    }

    /**
     * 处理[value]
     * */
    open fun doOnNext(value: T) {
    }

    /**
     * 获取[value]的大小
     * */
    protected open fun getSize(value: T): Long {
        return 0
    }

    override fun hookOnComplete() {
        val stopAt = System.currentTimeMillis()
        val throughput = Throughput(jobContext.totalSize.get(), stopAt - startAt, ChronoUnit.MILLIS)
        LoggerHolder.jobLogger.info("Job[${getJobName()}] execute successful.summary: $jobContext $throughput.")
    }

    override fun hookOnError(throwable: Throwable) {
        LoggerHolder.jobLogger.error("Job[${getJobName()}] execute failed: ", throwable)
    }

    override fun hookFinally(type: SignalType) {
        ArchiveUtils.monitor.removeMonitor(getJobName())
        countDownLatch.countDown()
    }

    override fun hookOnCancel() {
        LoggerHolder.jobLogger.info("Job[${getJobName()}] cancelled.")
    }

    override fun currentContext(): Context {
        return Context.of(JOB_NAME, getJobName(), JOB_CTX, jobContext)
    }

    fun blockLast() {
        countDownLatch.await()
    }

    companion object {
        const val JOB_NAME = "JOB-NAME"
        const val JOB_CTX = "JOB-CTX"
    }
}
