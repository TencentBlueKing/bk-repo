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

package com.tencent.bkrepo.job.metrics

import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileRetainResolver
import com.tencent.bkrepo.job.batch.file.NodeRetainResolver
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.BaseUnits
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class StorageCacheMetrics(
    private val registry: MeterRegistry,
    private val retainResolver: FileRetainResolver? = null,
) {

    private val cacheSizeMap = ConcurrentHashMap<String, Long>()
    private val cacheCountMap = ConcurrentHashMap<String, Long>()
    private val storageProjectRetainSizeMap = ConcurrentHashMap<String, Map<String, Long>>()
    private val storageProjectRetainCountMap = ConcurrentHashMap<String, Map<String, Long>>()

    /**
     * 设置当前缓存总大小及数量，由于目前只有Job服务在清理缓存时会统计，因此只有Job服务会调用该方法
     */
    fun setCacheMetrics(storageKey: String, size: Long, count: Long) {
        cacheSizeMap[storageKey] = size
        gauge(CACHE_SIZE, storageKey, cacheSizeMap, "storage cache total size", BaseUnits.BYTES)
        cacheCountMap[storageKey] = count
        gauge(CACHE_COUNT, storageKey, cacheCountMap, "storage cache total count")
    }

    /**
     * 设置根据策略保留的文件总大小及数量
     */
    fun setProjectRetainCacheMetrics(storageKey: String, sha256Set: Set<String>) {
        if (retainResolver !is NodeRetainResolver) {
            return
        }

        val projectRetainSize = ConcurrentHashMap<String, Long>()
        val projectRetainCount = ConcurrentHashMap<String, Long>()
        sha256Set.forEach { sha256 ->
            retainResolver.getRetainNode(sha256)?.let { retainNode ->
                val projectId = retainNode.projectId
                val size = projectRetainSize[projectId] ?: 0L
                val count = projectRetainCount[projectId] ?: 0L
                projectRetainSize[projectId] = size + retainNode.size
                projectRetainCount[projectId] = count + 1
            }
        }

        storageProjectRetainSizeMap[storageKey] = projectRetainSize
        projectRetainSize.forEach { (projectId, _) ->
            projectGauge(
                CACHE_RETAIN_SIZE,
                storageKey,
                projectId,
                storageProjectRetainSizeMap,
                "storage cache retain size",
                BaseUnits.BYTES
            )
        }
        storageProjectRetainCountMap[storageKey] = projectRetainCount
        projectRetainCount.forEach { (projectId, _) ->
            projectGauge(
                CACHE_RETAIN_COUNT,
                storageKey,
                projectId,
                storageProjectRetainCountMap,
                "storage cache retain count",
            )
        }
    }

    private fun gauge(name: String, storageKey: String, data: Map<String, Long>, des: String, unit: String? = null) {
        Gauge.builder(name, data) { it.getOrDefault(storageKey, 0L).toDouble() }
            .baseUnit(unit)
            .tag(TAG_STORAGE_KEY, storageKey)
            .description(des)
            .register(registry)
    }

    private fun projectGauge(
        name: String,
        storageKey: String,
        projectId: String,
        data: Map<String, Map<String, Long>>,
        des: String,
        unit: String? = null
    ) {
        Gauge.builder(name, data) { it[storageKey]?.get(projectId)?.toDouble() ?: 0.0 }
            .baseUnit(unit)
            .tag(TAG_STORAGE_KEY, storageKey)
            .tag(TAG_PROJECT_ID, projectId)
            .description(des)
            .register(registry)
    }

    companion object {
        const val CACHE_SIZE = "storage.cache.size"
        const val CACHE_COUNT = "storage.cache.count"
        const val CACHE_RETAIN_SIZE = "storage.cache.retain.size"
        const val CACHE_RETAIN_COUNT = "storage.cache.retain.count"
        const val TAG_STORAGE_KEY = "storageKey"
        const val TAG_PROJECT_ID = "projectId"
    }
}
