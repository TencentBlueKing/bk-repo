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

package com.tencent.bkrepo.common.service.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import javax.management.MBeanServer
import javax.management.ObjectName

@Component
class UndertowMetrics : MeterBinder {
    private val platformMBeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer()
    private val workerObjectName by lazy { ObjectName(OBJECT_NAME) }

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder(UNDERTOW_WORKER_QUEUE_SIZE, platformMBeanServer) { getWorkerQueueSize(it) }
            .description("Undertow worker queue size")
            .register(registry)
    }

    private fun getWorkerQueueSize(mBeanServer: MBeanServer): Double {
        try {
            val attributeValue = mBeanServer.getAttribute(workerObjectName, ATTR_WORKER_QUEUE_SIZE)
            if (attributeValue is Number) {
                return attributeValue.toDouble()
            }
        } catch (e: Exception) {
            logger.warn("Unable to get {} from JMX", ATTR_WORKER_QUEUE_SIZE)
        }
        return 0.0
    }


    companion object {
        private val logger = LoggerFactory.getLogger(UndertowMetrics::class.java)

        private const val OBJECT_NAME = "org.xnio:type=Xnio,provider=\"nio\",worker=\"XNIO-1\""

        /**
         * Worker线程池队列当前大小
         */
        private const val UNDERTOW_WORKER_QUEUE_SIZE = "undertow.worker.queue.size"
        private const val ATTR_WORKER_QUEUE_SIZE = "WorkerQueueSize"
    }
}
