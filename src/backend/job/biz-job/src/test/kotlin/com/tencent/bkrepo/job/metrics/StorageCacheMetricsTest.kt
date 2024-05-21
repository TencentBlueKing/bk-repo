package com.tencent.bkrepo.job.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StorageCacheMetricsTest {
    @Test
    fun test() {
        val storageKey = "testKey"
        val registry = SimpleMeterRegistry()
        val metrics = StorageCacheMetrics(registry)
        metrics.setCacheSize(storageKey, 1000L)
        metrics.setCacheCount(storageKey, 20L)
        val sizeMeter = registry
            .get(StorageCacheMetrics.CACHE_SIZE)
            .tags(StorageCacheMetrics.TAG_STORAGE_KEY, storageKey)
            .gauge()
        val countMeter = registry
            .get(StorageCacheMetrics.CACHE_COUNT)
            .tags(StorageCacheMetrics.TAG_STORAGE_KEY, storageKey)
            .gauge()
        Assertions.assertEquals(1000.0, sizeMeter.value())
        Assertions.assertEquals(20.0, countMeter.value())

        // 测试更新后的统计值
        metrics.setCacheSize(storageKey, 2000L)
        metrics.setCacheCount(storageKey, 40L)
        Assertions.assertEquals(2000.0, sizeMeter.value())
        Assertions.assertEquals(40.0, countMeter.value())
    }
}
