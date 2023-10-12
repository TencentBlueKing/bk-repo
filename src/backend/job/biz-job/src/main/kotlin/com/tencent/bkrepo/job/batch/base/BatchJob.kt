/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import com.tencent.bkrepo.job.listener.event.TaskExecutedEvent
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

/**
 * 抽象批处理作业Job
 * */
abstract class BatchJob<C : JobContext>(open val batchJobProperties: BatchJobProperties) {
    /**
     * 锁名称
     */
    open fun getLockName(): String = getJobName()

    /**
     * 返回任务名称
     *
     * 与job在配置中心的配置前缀保持一致，前缀规则为job.[Class.getSimpleNameq]去掉最后job的后缀
     * 例： NodeCopyJob 配置为 job.NodeCopy
     */
    fun getJobName(): String = javaClass.simpleName

    abstract fun createJobContext(): C

    /**
     * Job停止标志
     * */
    @Volatile
    private var stop = true

    /**
     * 是否排他执行，如果是则会加分布式锁
     * */
    protected open val isExclusive: Boolean = true

    /**
     * 任务id
     * 唯一标识
     * */
    protected val taskId: String = javaClass.simpleName

    /**
     * 最少加锁时间
     */
    open fun getLockAtLeastFor(): Duration = Duration.ofSeconds(1)

    /**
     * 最长加锁时间
     */
    open fun getLockAtMostFor(): Duration = Duration.ofMinutes(1)

    /**
     * 任务执行进度
     * */
    open fun report(jobContext: JobContext) {}

    @Autowired
    private lateinit var lockingTaskExecutor: LockingTaskExecutor

    var lastBeginTime: LocalDateTime? = null
    var lastEndTime: LocalDateTime? = null
    var lastExecuteTime: Long? = null

    open fun start(): Boolean {
        if (!shouldExecute()) {
            return false
        }
        logger.info("Start to execute async job[${getJobName()}]")
        stop = false
        val jobContext = createJobContext()
        val wasExecuted = if (isExclusive) {
            val task = LockingTaskExecutor.TaskWithResult { doStart(jobContext) }
            val result = lockingTaskExecutor.executeWithLock(task, getLockConfiguration())
            result.wasExecuted()
        } else {
            doStart(jobContext)
            true
        }
        if (stop) {
            logger.info("Job[${getJobName()}] stop execution.Execute result: $jobContext")
            return true
        }
        if (!wasExecuted) {
            logger.info("Job[${getJobName()}] already execution.")
            return wasExecuted
        }
        stop = true
        return wasExecuted
    }

    /**
     * 启动任务的具体实现
     * */
    private fun doStart(jobContext: C) {
        try {
            lastBeginTime = LocalDateTime.now()
            val elapseNano = measureNanoTime {
                doStart0(jobContext)
            }
            val elapsedTime = HumanReadable.time(elapseNano)
            logger.info("Job[${getJobName()}] execution completed, elapse $elapsedTime.Execute result: $jobContext")
            lastExecuteTime = Duration.ofNanos(elapseNano).toMillis()
            lastEndTime = LocalDateTime.now()
            val event = TaskExecutedEvent(
                name = getJobName(),
                context = jobContext,
                time = Duration.ofNanos(elapseNano)
            )
            SpringContextUtils.publishEvent(event)
        } catch (e: Exception) {
            logger.info("Job[${getJobName()}] execution failed.", e)
        }
    }

    abstract fun doStart0(jobContext: C)

    /**
     * 停止任务
     * */
    fun stop() {
        stop = true
    }

    /**
     * 启用
     */
    fun enable() {
        batchJobProperties.enabled = true
    }

    /**
     * 停止启用
     */
    fun disable() {
        batchJobProperties.enabled = false
    }

    /**
     * 任务是否在运行
     * */
    fun isRunning(): Boolean {
        return !stop
    }

    /**
     * 获取分布式锁需要的锁配置
     * */
    private fun getLockConfiguration(): LockConfiguration {
        return LockConfiguration(getLockName(), getLockAtMostFor(), getLockAtLeastFor())
    }

    /**
     * 判断当前节点是否执行该任务
     */
    open fun shouldExecute(): Boolean {
        return batchJobProperties.enabled
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
