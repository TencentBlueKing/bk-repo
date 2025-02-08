package com.tencent.bkrepo.job.metrics

import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.file.NodeRetainResolver
import com.tencent.bkrepo.job.batch.file.RetainNode
import com.tencent.bkrepo.job.metrics.StorageCacheMetrics.Companion.CACHE_RETAIN_COUNT
import com.tencent.bkrepo.job.metrics.StorageCacheMetrics.Companion.CACHE_RETAIN_SIZE
import com.tencent.bkrepo.job.metrics.StorageCacheMetrics.Companion.TAG_PROJECT_ID
import com.tencent.bkrepo.job.metrics.StorageCacheMetrics.Companion.TAG_STORAGE_KEY
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageCacheMetricsTest {

    private lateinit var retainResolver: NodeRetainResolver

    @BeforeAll
    fun beforeAll() {
        retainResolver = mock<NodeRetainResolver>()
        whenever(retainResolver.getRetainNode(anyString())).thenReturn(
            RetainNode(
                UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c.txt", UT_SHA256, 100
            )
        )
    }

    @Test
    fun test() {
        val storageKey = "testKey"
        val registry = SimpleMeterRegistry()
        val metrics = StorageCacheMetrics(registry, retainResolver)
        metrics.setCacheMetrics(storageKey, 1000L, 20L)
        metrics.setProjectRetainCacheMetrics(storageKey, setOf(UT_SHA256))
        val sizeMeter = registry.getMeter(StorageCacheMetrics.CACHE_SIZE, storageKey)
        val retainSizeMeter = registry.getProjectMeter(CACHE_RETAIN_SIZE, storageKey, UT_PROJECT_ID)
        val countMeter = registry.getMeter(StorageCacheMetrics.CACHE_COUNT, storageKey)
        val retainCountMeter = registry.getProjectMeter(CACHE_RETAIN_COUNT, storageKey, UT_PROJECT_ID)
        Assertions.assertEquals(1000.0, sizeMeter.value())
        Assertions.assertEquals(100.0, retainSizeMeter.value())
        Assertions.assertEquals(20.0, countMeter.value())
        Assertions.assertEquals(1.0, retainCountMeter.value())

        // 测试更新后的统计值
        metrics.setCacheMetrics(storageKey, 2000L, 40L)
        metrics.setProjectRetainCacheMetrics(storageKey, setOf("sha256-1", "sha256-2"))
        Assertions.assertEquals(2000.0, sizeMeter.value())
        Assertions.assertEquals(200.0, retainSizeMeter.value())
        Assertions.assertEquals(40.0, countMeter.value())
        Assertions.assertEquals(2.0, retainCountMeter.value())
    }

    private fun MeterRegistry.getMeter(name: String, storageKey: String) = get(name)
        .tags(TAG_STORAGE_KEY, storageKey)
        .gauge()

    private fun MeterRegistry.getProjectMeter(name: String, storageKey: String, projectId: String) = get(name)
        .tags(TAG_STORAGE_KEY, storageKey, TAG_PROJECT_ID, projectId)
        .gauge()
}
