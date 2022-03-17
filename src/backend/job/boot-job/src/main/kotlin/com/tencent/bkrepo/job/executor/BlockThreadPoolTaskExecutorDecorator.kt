package com.tencent.bkrepo.job.executor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.job.listener.event.TaskExecutedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

/**
 * 阻塞任务执行器
 * 可以设置限制大小，当达到任务限制时，会阻塞当前线程
 * 支持id任务，可以执行相同id的多个任务，然后等待所有任务完成
 * */
class BlockThreadPoolTaskExecutorDecorator(
    private val workerGroup: ThreadPoolTaskExecutor,
    limit: Int,
    bossCount: Int = 1
) {

    // 信号量
    private val semaphore: Semaphore = Semaphore(limit)

    // 存放任务信息
    private val taskInfos = ConcurrentHashMap<String, IdentityTaskInfo>()

    private val bossGroup = ThreadPoolExecutor(
        bossCount, bossCount,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(8096),
        ThreadFactoryBuilder().setNameFormat("BlockThreadPool-boss-%d").build()
    )

    /**
     * 执行Runnable
     * */
    fun execute(command: () -> Unit) {
        semaphore.acquire()
        workerGroup.execute {
            try {
                command.invoke()
            } finally {
                semaphore.release()
            }
        }
    }

    /**
     * 执行产生子任务的Runnable
     * */
    fun executeProduce(command: () -> Unit) {
        bossGroup.execute {
            command.invoke()
        }
    }

    /**
     * 执行带Id的任务
     * */
    fun executeWithId(identityTask: IdentityTask, produce: Boolean = false, permitsPerSecond: Double = 0.0) {
        val id = identityTask.id
        val taskInfo = taskInfos.getOrPut(id) { IdentityTaskInfo(id, permitsPerSecond = permitsPerSecond) }
        taskInfo.count.incrementAndGet()
        val enterTime = System.currentTimeMillis()
        val task = {
            try {
                taskInfo.acquire()
                val beginTime = System.currentTimeMillis()
                taskInfo.waitTime.addAndGet((beginTime - enterTime).toInt())
                measureTimeMillis { identityTask.run() }.apply {
                    taskInfo.executeTime.addAndGet(this.toInt())
                }
                Unit
            } finally {
                with(taskInfo) {
                    doneCount.incrementAndGet()
                    if (count.decrementAndGet() == 0) {
                        signalAll()
                    }
                }
            }
        }
        if (produce) {
            this.executeProduce(task)
        } else {
            this.execute(task)
        }
    }

    /**
     * 获取id的任务执行，如果执行未完成，则线程会进行阻塞等待
     * 在以下两种情况下该线程会返回
     * 1.生成者生产活动结束，且消费方任务完全消费结束
     * 2.生成者生产活动结束，等待超时且任务数没有变化
     * @param id identityTask id
     * @param timeout 任务执行超时时间
     * */
    fun get(id: String, timeout: Long) {
        val taskInfo = taskInfos[id] ?: throw IllegalArgumentException("no task $id")
        val duration = Duration.ofMillis(timeout)
        with(taskInfo) {
            var done = doneCount.get()
            while (!(complete && count.get() == 0)) {
                val result = await(duration)
                // 等待超时后，已做任务数没有变化
                if (!result && done == doneCount.get()) {
                    taskInfos.remove(id)
                    afterGet(taskInfo)
                    throw TimeoutException("Task[$id] was timeout,info: $this")
                }
                done = doneCount.get()
            }
        }
        afterGet(taskInfo)
        logger.info("Task[$id] has complete,info: $taskInfo")
        taskInfos.remove(id)
    }

    /**
     * 完成任务
     * @param id identityTask id
     * */
    fun complete(id: String) {
        val taskInfo = taskInfos[id] ?: throw IllegalArgumentException("no task $id")
        taskInfo.complete = true
        // 通知
        taskInfo.signalAll()
    }

    /**
     * 完成任务的同时，等待任务执行结束
     * @param id identityTask id
     * @param timeout 任务执行超时时间
     * */
    fun completeAndGet(id: String, timeout: Long) {
        val taskInfo = taskInfos[id] ?: throw IllegalArgumentException("no task $id")
        taskInfo.complete = true
        get(id, timeout)
    }

    private fun afterGet(taskInfo: IdentityTaskInfo) {
        with(taskInfo) {
            val taskExecutedEvent = TaskExecutedEvent(
                doneCount.get(),
                Duration.ofMillis(avgWaitTime().toLong()),
                Duration.ofMillis(avgExecuteTime().toLong())
            )
            SpringContextUtils.publishEvent(taskExecutedEvent)
        }
    }

    /**
     * 实时线程池任务数
     * */
    fun activeCount(): Int {
        return bossGroup.activeCount.plus(workerGroup.activeCount)
    }

    /**
     * 任务队列长度
     * */
    fun queueSize(): Int {
        return bossGroup.queue.size.plus(workerGroup.threadPoolExecutor.queue.size)
    }

    /**
     * 实时Job任务数
     * */
    fun activeTaskCount(): Int {
        return taskInfos.size
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlockThreadPoolTaskExecutorDecorator::class.java)
    }
}
