package com.tencent.bkrepo.job.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StorageCacheMetricsTest {
    @Test
    fun test() {
        val storageKey = "testKey"
        val registry = SimpleMeterRegistry()
        val metrics = StorageCacheMetrics(registry)
        metrics.setCacheMetrics(storageKey, 1000L, 20L)
        metrics.setRetainCacheMetrics(storageKey, 500L, 10L)
        val sizeMeter = registry.getMeter(StorageCacheMetrics.CACHE_SIZE, storageKey)
        val retainSizeMeter = registry.getMeter(StorageCacheMetrics.CACHE_RETAIN_SIZE, storageKey)
        val countMeter = registry.getMeter(StorageCacheMetrics.CACHE_COUNT, storageKey)
        val retainCountMeter = registry.getMeter(StorageCacheMetrics.CACHE_RETAIN_COUNT, storageKey)
        Assertions.assertEquals(1000.0, sizeMeter.value())
        Assertions.assertEquals(500.0, retainSizeMeter.value())
        Assertions.assertEquals(20.0, countMeter.value())
        Assertions.assertEquals(10.0, retainCountMeter.value())

        // 测试更新后的统计值
        metrics.setCacheMetrics(storageKey, 2000L, 40L)
        metrics.setRetainCacheMetrics(storageKey, 1000L, 30L)
        Assertions.assertEquals(2000.0, sizeMeter.value())
        Assertions.assertEquals(1000.0, retainSizeMeter.value())
        Assertions.assertEquals(40.0, countMeter.value())
        Assertions.assertEquals(30.0, retainCountMeter.value())
    }

    private fun MeterRegistry.getMeter(name: String, storageKey: String) = get(name)
        .tags(StorageCacheMetrics.TAG_STORAGE_KEY, storageKey)
        .gauge()
}
