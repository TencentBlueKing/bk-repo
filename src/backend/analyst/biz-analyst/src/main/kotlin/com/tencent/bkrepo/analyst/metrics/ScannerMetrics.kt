/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.metrics

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.distribution.DistributedCount
import com.tencent.bkrepo.analyst.distribution.DistributedCountFactory
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.statemachine.ScanTaskSchedulerConfiguration.Companion.SCAN_TASK_SCHEDULER_THREAD_POOL_BEAN_NAME
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 扫描服务数据统计
 */
@Component
@Suppress("TooManyFunctions")
class ScannerMetrics(
    private val scannerProperties: ScannerProperties,
    @Qualifier(SCAN_TASK_SCHEDULER_THREAD_POOL_BEAN_NAME)
    private val scanTaskSchedulerThreadPool: ThreadPoolTaskExecutor,
    private val distributedCountFactory: DistributedCountFactory
) : MeterBinder {

    private lateinit var meterRegistry: MeterRegistry

    /**
     * 记录各状态任务数量的Map，key为状态，value为任务数量
     */
    private val taskCountMap = ConcurrentHashMap<String, DistributedCount>(ScanTaskStatus.values().size)

    /**
     * 记录各状态子任务数量的Map，key为状态，value为任务数量
     */
    private val subtaskCounterMap = ConcurrentHashMap<String, DistributedCount>(SubScanTaskStatus.values().size)

    /**
     * 记录各文件类型扫描速率的Map，key为文件类型（参考[speedSummaryCacheKey]），value为速率统计信息
     */
    private val subtaskSpeedSummaryMap = ConcurrentHashMap<String, DistributionSummary>()

    /**
     * 重用扫描结果的任务数量统计
     */
    private val reuseResultSubtaskCounters: DistributedCount by lazy {
        // 统计重用扫描结果的子任务数量
        val distributedCount = createDistributedCount(SCANNER_SUBTASK_REUSE_RESULT_COUNT)
        Gauge.builder(SCANNER_SUBTASK_REUSE_RESULT_COUNT, distributedCount, DistributedCount::get)
            .register(meterRegistry)
        distributedCount
    }

    override fun bindTo(registry: MeterRegistry) {
        this.meterRegistry = registry
        taskGauge(
            scanTaskSchedulerThreadPool,
            { threadPoolExecutor.queue.size.toDouble() },
            ScanTaskStatus.PENDING,
            true
        ).register(meterRegistry)

        taskGauge(
            scanTaskSchedulerThreadPool,
            { activeCount.toDouble() },
            ScanTaskStatus.SCANNING_SUBMITTING,
            true
        ).register(meterRegistry)
    }

    /**
     * 处于[status]状态的任务数量增加[count]个
     */
    fun incTaskCountAndGet(status: ScanTaskStatus, count: Double = 1.0): Double {
        if (status == ScanTaskStatus.SCANNING_SUBMITTING) {
            taskCounter(ScanTaskStatus.PENDING).addAndGet(-count)
        }
        if (status == ScanTaskStatus.SCANNING_SUBMITTED) {
            taskCounter(ScanTaskStatus.SCANNING_SUBMITTING).addAndGet(-count)
        }
        if (status == ScanTaskStatus.FINISHED) {
            taskCounter(ScanTaskStatus.SCANNING_SUBMITTED).addAndGet(-count)
        }

        return taskCounter(status).addAndGet(count)
    }

    /**
     * [pre]状态任务数量减1，[next]状态任务数量加1
     */
    fun taskStatusChange(pre: ScanTaskStatus, next: ScanTaskStatus) {
        taskCounter(pre).decrementAndGet()
        taskCounter(next).incrementAndGet()
    }

    /**
     * 设置[status]状态的任务数量为[count]
     */
    fun setTaskCount(status: ScanTaskStatus, count: Double) {
        taskCounter(status).set(count)
    }

    /**
     * 处于[status]状态的任务数量增加[count]个
     */
    fun incSubtaskCountAndGet(status: SubScanTaskStatus, count: Double = 1.0): Double {
        return subtaskCounter(status).addAndGet(count)
    }

    /**
     * 处于[status]状态的任务数量减少[count]个
     */
    fun decSubtaskCountAndGet(status: SubScanTaskStatus, count: Double = 1.0): Double {
        return subtaskCounter(status).addAndGet(-count)
    }

    /**
     * [pre]状态任务数量减1，[next]状态任务数量加1
     */
    fun subtaskStatusChange(pre: SubScanTaskStatus, next: SubScanTaskStatus) {
        subtaskCounter(pre).decrementAndGet()
        subtaskCounter(next).incrementAndGet()
    }

    fun setSubtaskCount(status: SubScanTaskStatus, count: Double) {
        subtaskCounter(status).set(count)
    }

    /**
     * 重用扫描结果的子任务数量加1
     */
    fun incReuseResultSubtaskCount(count: Double = 1.0) {
        reuseResultSubtaskCounters.addAndGet(count)
    }

    fun record(
        fullPath: String,
        fileSize: Long,
        scanner: String,
        duration: Duration
    ) {
        val fileExtensionName = fullPath.substringAfterLast('.', UNKNOWN_EXTENSION)
        val summary = taskSpeedSummary(fileExtensionName, scanner)
        val elapsedSeconds = maxOf(duration.seconds.toDouble(), 1.0)
        summary.record(fileSize / elapsedSeconds)
    }

    private fun subtaskCounter(status: SubScanTaskStatus): DistributedCount {
        return subtaskCounterMap.getOrPut(status.name) {
            // 统计不同状态扫描任务数量
            val key = metricsKey(SCANNER_SUBTASK_COUNT, "status", status.name)
            val distributedCount = createDistributedCount(key)
            Gauge.builder(SCANNER_SUBTASK_COUNT, distributedCount, DistributedCount::get)
                .description("${status.name} subtask count")
                .tag("status", status.name)
                .register(meterRegistry)
            distributedCount
        }
    }

    private fun taskCounter(status: ScanTaskStatus): DistributedCount {
        return taskCountMap.getOrPut(status.name) {
            // 统计不同状态子任务数量
            val key = metricsKey(SCANNER_TASK_COUNT, "status", status.name)
            val distributedCount = createDistributedCount(key)
            taskGauge(distributedCount, DistributedCount::get, status).register(meterRegistry)
            distributedCount
        }
    }

    private fun <T> taskGauge(
        obj: T,
        f: T.() -> Double,
        status: ScanTaskStatus,
        local: Boolean = false
    ): Gauge.Builder<T> {
        return Gauge.builder(SCANNER_TASK_COUNT, obj, f)
            .description("${status.name} task count")
            .tag("status", status.name)
            .tag("local", local.toString())
    }

    private fun taskSpeedSummary(fileType: String, scanner: String): DistributionSummary {
        return subtaskSpeedSummaryMap.getOrPut(speedSummaryCacheKey(fileType, scanner)) {
            DistributionSummary.builder(SCANNER_SUBTASK_SPEED)
                .description("subtask speed")
                .baseUnit("bytes/seconds")
                .tag("fileType", fileType)
                .tag("scanner", scanner)
                .register(meterRegistry)
        }
    }

    private fun speedSummaryCacheKey(fileType: String, scanner: String) =
        "$fileType:$scanner"

    private fun metricsKey(meterName: String, vararg tags: String): String {
        val newMeterName = meterName.removePrefix("scanner.").replace(".", ":")
        return "metrics:scanner:$newMeterName:${tags.joinToString(":")}"
    }

    private fun createDistributedCount(key: String): DistributedCount {
        // 一个任务可能被不同的服务实例处理，统计数据需要放到公共存储上才能保证数据准确
        return distributedCountFactory.create(key, scannerProperties.distributedCountType)
    }

    companion object {
        /**
         * 扫描任务数量
         */
        private const val SCANNER_TASK_COUNT = "scanner.task.count"

        /**
         * 子扫描任务数量
         */
        private const val SCANNER_SUBTASK_COUNT = "scanner.subtask.count"

        /**
         * 重用扫描结果的子任务数量
         */
        private const val SCANNER_SUBTASK_REUSE_RESULT_COUNT = "scanner.subtask.reuse-result.count"

        /**
         * 子任务执行耗时
         */
        private const val SCANNER_SUBTASK_SPEED = "scanner.subtask.speed"

        /**
         * 未知扩展名
         */
        private const val UNKNOWN_EXTENSION = "UNKNOWN"
    }
}
