package com.tencent.bkrepo.helm.pool

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * helm线程池，用于同步索引文件
 */
object HelmThreadPoolExecutor {
    /**
     * 线程池实例
     */
    val instance: ThreadPoolExecutor = buildThreadPoolExecutor()

    /**
     * 创建线程池
     */
    private fun buildThreadPoolExecutor(): ThreadPoolExecutor {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("helm-worker-%d").build()
        return ThreadPoolExecutor(
            100, 500, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(10), namedThreadFactory, ThreadPoolExecutor.CallerRunsPolicy()
        )
    }
}
