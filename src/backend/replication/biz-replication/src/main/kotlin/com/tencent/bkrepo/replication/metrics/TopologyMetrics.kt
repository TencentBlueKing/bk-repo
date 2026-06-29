/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 链式拓扑追溯相关 Prometheus 指标。
 */
@Component
class TopologyMetrics(
    private val registry: MeterRegistry
) {

    /** 周期同步成功 / 失败计数 */
    fun incSyncResult(success: Boolean) {
        Counter.builder(SYNC_TOTAL)
            .description("Total upstream-edge sync cycles by result")
            .tag(TAG_RESULT, if (success) RESULT_SUCCESS else RESULT_FAILED)
            .register(registry)
            .increment()
    }

    /** 单次周期同步执行耗时直方图 */
    fun recordSyncDuration(durationNanos: Long) {
        Timer.builder(SYNC_DURATION)
            .description("Upstream-edge sync cycle duration")
            .publishPercentileHistogram()
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }

    /** 链式追溯查询耗时直方图 */
    fun recordQueryDuration(durationNanos: Long) {
        Timer.builder(QUERY_DURATION)
            .description("Topology upstream query duration")
            .publishPercentileHistogram()
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }

    /** 链式追溯查询深度直方图 */
    fun recordQueryDepth(depth: Int) {
        Timer.builder(QUERY_DEPTH)
            .description("Topology upstream query reached depth (units in seconds is misleading; tag 'unit'='depth')")
            .tag(TAG_UNIT, UNIT_DEPTH)
            .register(registry)
            .record(depth.toLong(), TimeUnit.SECONDS)
    }

    companion object {
        const val SYNC_TOTAL = "replication_topology_sync_total"
        const val SYNC_DURATION = "replication_topology_sync_duration_seconds"
        const val QUERY_DURATION = "replication_topology_query_duration_seconds"
        const val QUERY_DEPTH = "replication_topology_query_depth"
        const val TAG_RESULT = "result"
        const val TAG_UNIT = "unit"
        const val RESULT_SUCCESS = "success"
        const val RESULT_FAILED = "failed"
        const val UNIT_DEPTH = "depth"
    }
}
