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
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class ScannerMetrics : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        ScanTaskStatus.values().forEach {
            val taskCount = taskCountMap.put(it.name, AtomicLong(0L))!!
            Gauge.builder(SCANNER_TASK_COUNT, taskCount, AtomicLong::toDouble)
                .description("${it.name} task count")
                .tag("status", it.name)
                .register(registry)
        }

        SubScanTaskStatus.values().forEach {
            val subtaskCount = subtaskCountMap.put(it.name, AtomicLong(0L))!!
            Gauge.builder(SCANNER_SUBTASK_COUNT, subtaskCount, AtomicLong::toDouble)
                .description("${it.name} subtask count")
                .tag("status", it.name)
                .register(registry)
        }

        reuseResultSubtaskCounter = Counter.builder(SCANNER_SUBTASK_REUSE_RESULT_COUNT)
            .description("reuse resul subtask count")
            .register(registry)
    }

    companion object {
        private const val SCANNER_TASK_COUNT = "scanner.task.count"
        private const val SCANNER_SUBTASK_COUNT = "scanner.subtask.count"
        private const val SCANNER_SUBTASK_REUSE_RESULT_COUNT = "scanner.subtask.reuse-result.count"

        private val taskCountMap = HashMap<String, AtomicLong>(ScanTaskStatus.values().size)
        private val subtaskCountMap = HashMap<String, AtomicLong>(SubScanTaskStatus.values().size)
        private lateinit var reuseResultSubtaskCounter: Counter

        fun incTaskCountAndGet(status: ScanTaskStatus, count: Long = 1): Long {
            if (status == ScanTaskStatus.SCANNING_SUBMITTING) {
                taskCountMap[ScanTaskStatus.PENDING.name]!!.addAndGet(-count)
            }
            if (status == ScanTaskStatus.SCANNING_SUBMITTED) {
                taskCountMap[ScanTaskStatus.SCANNING_SUBMITTING.name]!!.addAndGet(-count)
            }
            if (status == ScanTaskStatus.FINISHED) {
                taskCountMap[ScanTaskStatus.SCANNING_SUBMITTED.name]!!.addAndGet(-count)
            }
            return taskCountMap[status.name]!!.addAndGet(count)
        }

        fun taskStatusChange(pre: ScanTaskStatus, next: ScanTaskStatus) {
            taskCountMap[pre.name]!!.decrementAndGet()
            taskCountMap[next.name]!!.incrementAndGet()
        }

        fun incSubtaskCountAndGet(status: SubScanTaskStatus, count: Long = 1): Long {
            return subtaskCountMap[status.name]!!.addAndGet(count)
        }

        fun decSubtaskCountAndGet(status: SubScanTaskStatus, count: Long = 1): Long {
            return subtaskCountMap[status.name]!!.addAndGet(-count)
        }

        fun subtaskStatusChange(pre: SubScanTaskStatus, next: SubScanTaskStatus) {
            subtaskCountMap[pre.name]!!.decrementAndGet()
            subtaskCountMap[next.name]!!.incrementAndGet()
        }

        fun incReuseResultSubtaskCount() {
            reuseResultSubtaskCounter.increment()
        }
    }

}
