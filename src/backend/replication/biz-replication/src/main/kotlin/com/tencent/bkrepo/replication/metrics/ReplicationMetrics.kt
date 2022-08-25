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

package com.tencent.bkrepo.replication.metrics

import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_QUEUE_SIZE_DESC
import com.tencent.bkrepo.replication.constant.OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE_DESC
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_COMPLETED_COUNT
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_COMPLETED_COUNT_DESC
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.REPLICATION_TASK_QUEUE_SIZE_DESC
import com.tencent.bkrepo.replication.replica.base.executor.EventConsumerThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.base.executor.OciThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.base.executor.ReplicaThreadPoolExecutor
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * 制品同步服务度量指标
 */
@Component
class ReplicationMetrics(
    meterRegistry: MeterRegistry
) : MeterBinder {

    var runOnceTaskCreateCount = AtomicInteger(0)
    var runOnceTaskExecuteCount = AtomicInteger(0)
    var realTimeTaskCreateCount = AtomicInteger(0)
    var realTimeTaskExecuteCount = AtomicInteger(0)
    var scheduledTaskCreateCount = AtomicInteger(0)
    var scheduledTaskExecuteCount = AtomicInteger(0)

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder(REPLICATION_TASK_ACTIVE_COUNT, ReplicaThreadPoolExecutor.instance) { it.activeCount.toDouble() }
            .description(REPLICATION_TASK_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(REPLICATION_TASK_QUEUE_SIZE, ReplicaThreadPoolExecutor.instance) { it.queue.size.toDouble() }
            .description(REPLICATION_TASK_QUEUE_SIZE_DESC)
            .register(registry)

        Gauge.builder(REPLICATION_TASK_COMPLETED_COUNT, ReplicaThreadPoolExecutor.instance) {
            it.completedTaskCount.toDouble()
        }.description(REPLICATION_TASK_COMPLETED_COUNT_DESC)
            .register(registry)

        Gauge.builder(OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT, OciThreadPoolExecutor.instance) { it.activeCount.toDouble() }
            .description(OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE, OciThreadPoolExecutor.instance) { it.queue.size.toDouble() }
            .description(OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE_DESC)
            .register(registry)

        Gauge.builder(
            EVENT_CONSUMER_TASK_ACTIVE_COUNT, EventConsumerThreadPoolExecutor.instance
        ) { it.activeCount.toDouble() }
            .description(EVENT_CONSUMER_TASK_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(
            EVENT_CONSUMER_TASK_QUEUE_SIZE, EventConsumerThreadPoolExecutor.instance
        ) { it.queue.size.toDouble() }
            .description(EVENT_CONSUMER_TASK_QUEUE_SIZE_DESC)
            .register(registry)
    }
}
