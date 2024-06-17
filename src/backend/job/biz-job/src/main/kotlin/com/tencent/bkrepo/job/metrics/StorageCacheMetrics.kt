/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.BaseUnits
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class StorageCacheMetrics(
    private val registry: MeterRegistry,
) {

    private val cacheSizeMap = ConcurrentHashMap<String, Long>()
    private val cacheCountMap = ConcurrentHashMap<String, Long>()
    private val retainSizeMap = ConcurrentHashMap<String, Long>()
    private val retainCountMap = ConcurrentHashMap<String, Long>()

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
    fun setRetainCacheMetrics(storageKey: String, size: Long, count: Long) {
        retainSizeMap[storageKey] = size
        gauge(CACHE_RETAIN_SIZE, storageKey, retainSizeMap, "storage cache retain size", BaseUnits.BYTES)
        retainCountMap[storageKey] = count
        gauge(CACHE_RETAIN_COUNT, storageKey, retainCountMap, "storage cache retain count")
    }

    private fun gauge(name: String, storageKey: String, data: Map<String, Long>, des: String, unit: String? = null) {
        Gauge.builder(name, data) { it.getOrDefault(storageKey, 0L).toDouble() }
            .baseUnit(unit)
            .tag(TAG_STORAGE_KEY, storageKey)
            .description(des)
            .register(registry)
    }

    companion object {
        const val CACHE_SIZE = "storage.cache.size"
        const val CACHE_COUNT = "storage.cache.count"
        const val CACHE_RETAIN_SIZE = "storage.cache.retain.size"
        const val CACHE_RETAIN_COUNT = "storage.cache.retain.count"
        const val TAG_STORAGE_KEY = "storageKey"
    }
}
