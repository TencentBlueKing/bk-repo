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

import com.tencent.bkrepo.replication.constant.EDGE_PULL_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.EDGE_PULL_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.EVENT_CONSUMER_TASK_QUEUE_SIZE_DESC
import com.tencent.bkrepo.replication.constant.MANUAL_TASK_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.MANUAL_TASK_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.MANUAL_TASK_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.MANUAL_TASK_QUEUE_SIZE_DESC
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
import com.tencent.bkrepo.replication.constant.RUN_ONCE_EXECUTOR_ACTIVE_COUNT
import com.tencent.bkrepo.replication.constant.RUN_ONCE_EXECUTOR_ACTIVE_COUNT_DESC
import com.tencent.bkrepo.replication.constant.RUN_ONCE_EXECUTOR_QUEUE_SIZE
import com.tencent.bkrepo.replication.constant.RUN_ONCE_EXECUTOR_QUEUE_SIZE_DESC
import com.tencent.bkrepo.replication.replica.executor.EdgePullThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.EventConsumerThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.ManualThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.OciThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.ReplicaThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.executor.RunOnceThreadPoolExecutor
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * 制品同步服务度量指标
 */
@Component
class ReplicationMetrics : MeterBinder {

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

        Gauge.builder(
            RUN_ONCE_EXECUTOR_ACTIVE_COUNT, RunOnceThreadPoolExecutor.instance
        ) { it.activeCount.toDouble() }
            .description(RUN_ONCE_EXECUTOR_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(
            RUN_ONCE_EXECUTOR_QUEUE_SIZE, RunOnceThreadPoolExecutor.instance
        ) { it.queue.size.toDouble() }
            .description(RUN_ONCE_EXECUTOR_QUEUE_SIZE_DESC)
            .register(registry)

        Gauge.builder(
            MANUAL_TASK_ACTIVE_COUNT, ManualThreadPoolExecutor.instance
        ) { it.activeCount.toDouble() }
            .description(MANUAL_TASK_ACTIVE_COUNT_DESC)
            .register(registry)

        Gauge.builder(
            MANUAL_TASK_QUEUE_SIZE, ManualThreadPoolExecutor.instance
        ) { it.queue.size.toDouble() }
            .description(MANUAL_TASK_QUEUE_SIZE_DESC)
            .register(registry)

        Gauge.builder(
            EDGE_PULL_ACTIVE_COUNT, EdgePullThreadPoolExecutor.instance
        ) { it.activeCount.toDouble() }
            .description(EDGE_PULL_ACTIVE_COUNT_DESC)
            .register(registry)
    }
}
