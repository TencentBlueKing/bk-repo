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

package com.tencent.bkrepo.scanner.metrics

import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 扫描服务数据统计
 */
@Component
class ScannerMetrics(
    private val registryProvider: ObjectProvider<MeterRegistry>
) {
    private val taskCountMap = ConcurrentHashMap<String, AtomicLong>(ScanTaskStatus.values().size)
    private val subtaskCounterMap = ConcurrentHashMap<String, AtomicLong>(SubScanTaskStatus.values().size)
    private val reuseResultSubtaskCounters: List<Counter> by lazy {
        // 统计重用扫描结果的子任务数量
        val counter = Counter.builder(SCANNER_SUBTASK_REUSE_RESULT_COUNT).description("reuse resul subtask count")
        registryProvider.map { counter.register(it) }
    }

    /**
     * 处于[status]状态的任务数量增加[count]个
     */
    fun incTaskCountAndGet(status: ScanTaskStatus, count: Long = 1): Long {
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
     * 处于[status]状态的任务数量增加[count]个
     */
    fun incSubtaskCountAndGet(status: SubScanTaskStatus, count: Long = 1): Long {
        return subtaskCounter(status).addAndGet(count)
    }

    /**
     * 处于[status]状态的任务数量减少[count]个
     */
    fun decSubtaskCountAndGet(status: SubScanTaskStatus, count: Long = 1): Long {
        return subtaskCounter(status).addAndGet(-count)
    }

    /**
     * [pre]状态任务数量减1，[next]状态任务数量加1
     */
    fun subtaskStatusChange(pre: SubScanTaskStatus, next: SubScanTaskStatus) {
        subtaskCounter(pre).decrementAndGet()
        subtaskCounter(next).incrementAndGet()
    }

    /**
     * 重用扫描结果的子任务数量加1
     */
    fun incReuseResultSubtaskCount() {
        reuseResultSubtaskCounters.forEach { it.increment() }
    }

    private fun subtaskCounter(status: SubScanTaskStatus): AtomicLong {
        return subtaskCounterMap.syncGetOrPut(status.name) {
            // 统计不同状态扫描任务数量
            val atomicLong = AtomicLong(0L)
            val gauge = Gauge.builder(SCANNER_TASK_COUNT, atomicLong, AtomicLong::toDouble)
                .description("${status.name} task count")
                .tag("status", status.name)
            registryProvider.forEach { gauge.register(it) }
            atomicLong
        }
    }

    private fun taskCounter(status: ScanTaskStatus): AtomicLong {
        return taskCountMap.syncGetOrPut(status.name) {
            val atomicLong = AtomicLong(0L)
            // 统计不同状态子任务数量
            val gauge = Gauge.builder(SCANNER_SUBTASK_COUNT, atomicLong, AtomicLong::toDouble)
                .description("${status.name} subtask count")
                .tag("status", status.name)
            registryProvider.forEach { gauge.register(it) }
            atomicLong
        }
    }

    private fun <K : Any, V : Any> ConcurrentHashMap<K, V>.syncGetOrPut(key: K, defaultValue: () -> V): V {
        var value = get(key)
        if (value == null) {
            synchronized(this) {
                value = get(key)
                if (value == null) {
                    value = put(key, defaultValue.invoke())
                }
            }
        }
        return value!!
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

        private const val SCANNER_SUBTASK_TIME_SPENT = "scanner.subtask.spent.millis"
    }

}
