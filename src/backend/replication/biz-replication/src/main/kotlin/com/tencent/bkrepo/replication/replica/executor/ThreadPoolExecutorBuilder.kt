/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.executor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 线程池执行器构建器
 * 用于统一创建和配置各种类型的线程池执行器
 */
object ThreadPoolExecutorBuilder {

    /**
     * 线程池配置
     */
    data class ThreadPoolConfig(
        val threadNamePrefix: String,
        val corePoolSizeMultiplier: Int = 2,
        val maxPoolSizeMultiplier: Int = 4,
        val keepAliveTime: Long = 60,
        val keepAliveTimeUnit: TimeUnit = TimeUnit.SECONDS,
        val queueType: QueueType = QueueType.ARRAY_BLOCKING,
        val queueSize: Int = 1024,
        val allowCoreThreadTimeOut: Boolean = true
    )

    /**
     * 队列类型枚举
     */
    enum class QueueType {
        ARRAY_BLOCKING,
        SYNCHRONOUS
    }

    /**
     * 构建线程池执行器
     */
    fun build(config: ThreadPoolConfig): ThreadPoolExecutor {
        val namedThreadFactory = ThreadFactoryBuilder()
            .setNameFormat("${config.threadNamePrefix}-%d")
            .build()
        
        val corePoolSize = Runtime.getRuntime().availableProcessors() * config.corePoolSizeMultiplier
        val maxPoolSize = Runtime.getRuntime().availableProcessors() * config.maxPoolSizeMultiplier
        
        val workQueue: BlockingQueue<Runnable> = when (config.queueType) {
            QueueType.ARRAY_BLOCKING -> ArrayBlockingQueue(config.queueSize)
            QueueType.SYNCHRONOUS -> SynchronousQueue()
        }
        
        return ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            config.keepAliveTime,
            config.keepAliveTimeUnit,
            workQueue,
            namedThreadFactory,
            ThreadPoolExecutor.CallerRunsPolicy()
        ).apply { 
            this.allowCoreThreadTimeOut(config.allowCoreThreadTimeOut) 
        }
    }

    /**
     * 预定义的线程池配置
     */
    object Configs {
        val REPLICA = ThreadPoolConfig(
            threadNamePrefix = "replica-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4,
            keepAliveTime = 30,
            keepAliveTimeUnit = TimeUnit.SECONDS
        )

        val FEDERATION = ThreadPoolConfig(
            threadNamePrefix = "federation-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val FEDERATION_FILE = ThreadPoolConfig(
            threadNamePrefix = "federation-file-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val FEDERATION_FULL_SYNC = ThreadPoolConfig(
            threadNamePrefix = "fullSync-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val OCI = ThreadPoolConfig(
            threadNamePrefix = "oci-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4,
            keepAliveTime = 30,
            keepAliveTimeUnit = TimeUnit.SECONDS
        )

        val EVENT_CONSUMER = ThreadPoolConfig(
            threadNamePrefix = "event-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val MANUAL = ThreadPoolConfig(
            threadNamePrefix = "manual-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val RUN_ONCE = ThreadPoolConfig(
            threadNamePrefix = "runOnce-worker",
            corePoolSizeMultiplier = 2,
            maxPoolSizeMultiplier = 4
        )

        val EDGE_PULL = ThreadPoolConfig(
            threadNamePrefix = "edge-pull-worker",
            corePoolSizeMultiplier = 1,
            maxPoolSizeMultiplier = 1,
            queueType = QueueType.SYNCHRONOUS
        )
    }
}