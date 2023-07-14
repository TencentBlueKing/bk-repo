/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.frpc

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.util.SnowflakeIdWorker
import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.frpc.event.EventProcess
import com.tencent.bkrepo.common.frpc.event.RegEvent
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ServiceRegistrarProcess(
    val eventBus: EventBus
) : EventProcess<RegEvent>, ServiceRegistry<StorageServiceInstance> {
    private val serviceMap = ConcurrentHashMap<Long, StorageServiceInstance>()
    private val host = InetAddress.getLocalHost().hostAddress
    val id: Long
    val service: StorageServiceInstance

    init {
        // 随机启动时间，保证id的随机性，减少id冲突
        Thread.sleep(Random.nextLong(MAX_START_DELAY_MILLIS))
        id = idWorker.nextId()
        service = addService(id, host)
        executor.scheduleAtFixedRate(this::refresh, REFRESH_SERVICES_TIME, REFRESH_SERVICES_TIME, TimeUnit.MILLISECONDS)
        executor.scheduleAtFixedRate(this::registerSelf, HEART_BEAT_TIME, HEART_BEAT_TIME, TimeUnit.MILLISECONDS)
    }

    private fun addService(id: Long, host: String): StorageServiceInstance {
        val service = serviceMap[id]
        return if (service != null) {
            // update heartbeat time
            logger.trace("Service $id is active.")
            service.lastHeartBeatTime = System.currentTimeMillis()
            service
        } else {
            // register service
            val newService = StorageServiceInstance(
                id = id,
                registerTime = System.currentTimeMillis(),
                lastHeartBeatTime = System.currentTimeMillis(),
                host = host,
            )
            logger.info("Register service $id")
            serviceMap[id] = newService
            newService
        }
    }

    private fun registerSelf() {
        val event = RegEvent(
            serviceId = id,
            host = host,
        )
        call(event)
    }

    private fun refresh() {
        val it = serviceMap.iterator()
        while (it.hasNext()) {
            val instance = it.next().value
            // 逐出空闲服务
            val now = System.currentTimeMillis()
            if (now - instance.lastHeartBeatTime > MAX_KEEPALIVE_TIME) {
                logger.info("Evict service ${instance.id}.")
                it.remove()
            }
        }
    }

    override fun call(event: RegEvent) {
        eventBus.publish(event)
    }

    override fun supportEvent(event: Event): Boolean {
        return event is RegEvent
    }

    override fun handler(event: Event) {
        require(event is RegEvent)
        addService(event.serviceId, event.host)
    }

    override fun getLocalService(): StorageServiceInstance {
        return service
    }

    override fun getServices(): Set<StorageServiceInstance> {
        return serviceMap.values.toSet()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceRegistrarProcess::class.java)
        private val idWorker = SnowflakeIdWorker(0, 0)
        private val threadFactory = ThreadFactoryBuilder().setDaemon(true)
            .setNameFormat("service-register")
            .build()
        private val executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        private const val MAX_KEEPALIVE_TIME = 5000
        private const val REFRESH_SERVICES_TIME = 3000L
        private const val HEART_BEAT_TIME = 1000L
        private const val MAX_START_DELAY_MILLIS = 2000L
    }
}
