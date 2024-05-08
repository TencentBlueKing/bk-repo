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

package com.tencent.bkrepo.common.artifact.metrics.push.custom

import com.tencent.bkrepo.common.artifact.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.artifact.metrics.push.prometheus.PrometheusDrive
import io.prometheus.client.CollectorRegistry
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

class ScheduleMetricsExporter(
    private val registry: CollectorRegistry,
    private var drive: PrometheusDrive,
    scheduler: TaskScheduler,
    pushRate: Duration = Duration.ofSeconds(30),
) {
    var queue: ConcurrentLinkedQueue<MetricsItem> = ConcurrentLinkedQueue()

    init {
        scheduler.scheduleAtFixedRate(this::exportMetricsData, pushRate)
    }

    fun reportMetrics(item: MetricsItem) {
        queue.offer(item)
    }

    private fun exportMetricsData() {
        if (queue.isEmpty()) {
            return
        }
        val count = queue.size
        for (i in 0 until count) {
            val item: MetricsItem = queue.poll()
            val data = MetricsDataManager.createMetricsData(
                item.type, item.labels, registry
            )
            data.updateValue(item.value)
        }
        drive.push()
    }

    companion object {
        /**
         * 队列大小限制
         */
        private const val QUEUE_LIMIT = 4096
    }
}
