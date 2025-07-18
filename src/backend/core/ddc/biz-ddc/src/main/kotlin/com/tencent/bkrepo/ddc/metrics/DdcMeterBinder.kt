/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.ddc.metrics

import com.tencent.bkrepo.ddc.config.DdcConfiguration.Companion.BEAN_NAME_REF_BATCH_EXECUTOR
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component

@Component
class DdcMeterBinder(
    @Qualifier(BEAN_NAME_REF_BATCH_EXECUTOR)
    val refBatchExecutor: ThreadPoolTaskExecutor
) : MeterBinder {
    /**
     * ref inline加载耗时
     */
    lateinit var refInlineLoadTimer: Timer

    /**
     * ref compact binary 加载耗时
     */
    lateinit var refLoadTimer: Timer

    /**
     * legacy ref 加载耗时
     */
    lateinit var legacyRefLoadTimer: Timer

    /**
     * blob 加载耗时
     */
    lateinit var blobLoadTimer: Timer

    /**
     * ref 创建耗时
     */
    lateinit var refStoreTimer: Timer

    /**
     * legacy ref 加载耗时
     */
    lateinit var legacyRefStoreTimer: Timer

    private lateinit var registry: MeterRegistry


    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        refInlineLoadTimer = Timer
            .builder(DDC_REF_LOAD)
            .tag("inline", "true")
            .tag("legacy", "false")
            .register(registry)

        refLoadTimer = Timer
            .builder(DDC_REF_LOAD)
            .tag("inline", "false")
            .tag("legacy", "false")
            .register(registry)

        legacyRefLoadTimer = Timer
            .builder(DDC_REF_LOAD)
            .tag("inline", "false")
            .tag("legacy", "true")
            .register(registry)

        refStoreTimer = Timer
            .builder(DDC_REF_STORE)
            .tag("legacy", "false")
            .register(registry)

        legacyRefStoreTimer = Timer
            .builder(DDC_REF_STORE)
            .tag("legacy", "true")
            .register(registry)

        blobLoadTimer = Timer
            .builder(DDC_BLOB)
            .tag("type", "compressed")
            .tag("method", "load")
            .register(registry)

        Gauge.builder(DDC_REF_BATCH_EXECUTOR_ACTIVE, refBatchExecutor) { it.activeCount.toDouble() }
            .description("ddc ref batch executor active thread")
            .register(registry)

        Gauge.builder(DDC_REF_BATCH_EXECUTOR_QUEUE_SIZE, refBatchExecutor) { it.queueSize.toDouble() }
            .description("ddc ref batch executor queue size")
            .register(registry)
    }

    /**
     * 获取Ref请求总数
     */
    fun incCacheCount(projectId: String, repoName: String) {
        Counter
            .builder(DDC_REF_GETS)
            .tag("projectId", projectId)
            .tag("repoName", repoName)
            .tag("type", "total")
            .register(registry)
            .increment()
    }

    /**
     * Ref命中数
     */
    fun incCacheHitCount(projectId: String, repoName: String) {
        Counter
            .builder(DDC_REF_GETS)
            .tag("projectId", projectId)
            .tag("repoName", repoName)
            .tag("type", "hit")
            .register(registry)
            .increment()

    }

    companion object {
        private const val DDC_REF_GETS = "ddc.ref.gets"
        private const val DDC_REF_LOAD = "ddc.ref.load"
        private const val DDC_REF_STORE = "ddc.ref.store"
        private const val DDC_BLOB = "ddc.blob"
        private const val DDC_REF_BATCH_EXECUTOR_ACTIVE = "ddc.ref.batch.executor.active.count"
        private const val DDC_REF_BATCH_EXECUTOR_QUEUE_SIZE = "ddc.ref.batch.executor.queue.size"
    }
}
