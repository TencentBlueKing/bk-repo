package com.tencent.bkrepo.common.storage.monitor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 存储监控助手
 * */
class StorageHealthMonitorHelper(private val monitorMap: ConcurrentHashMap<String, StorageHealthMonitor>) {

    private val monitorCounter = AtomicInteger()

    /**
     * 获取存储相对应的监控
     * */
    fun getMonitor(properties: StorageProperties, storageCredentials: StorageCredentials): StorageHealthMonitor {
        val location = storageCredentials.upload.location
        return monitorMap[location] ?: synchronized(location.intern()) {
            monitorMap[location]?.let { return it }
            val executorService = createExecutorService(monitorCounter.getAndIncrement())
            val storageHealthMonitor = StorageHealthMonitor(properties, location, executorService)
            monitorMap.putIfAbsent(
                location,
                storageHealthMonitor
            )
            storageHealthMonitor
        }
    }

    /**
     * 获取目前所有的监控
     * */
    fun all(): List<StorageHealthMonitor> {
        return monitorMap.values.toList()
    }

    private fun createExecutorService(number: Int): ExecutorService {
        return ThreadPoolExecutor(
            0, 1,
            60L, TimeUnit.SECONDS, SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("storage-monitor-$number-%d").build()
        )
    }
}
