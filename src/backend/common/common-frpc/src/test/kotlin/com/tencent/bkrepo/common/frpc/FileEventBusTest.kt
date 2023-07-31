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

import com.tencent.bkrepo.common.frpc.event.AckEvent
import com.tencent.bkrepo.common.frpc.event.Event
import com.tencent.bkrepo.common.frpc.event.EventType
import com.tencent.bkrepo.common.frpc.event.handler.EventHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FileEventBusTest : EventBusBaseTest() {

    @Test
    fun publish() {
        val event = AckEvent(id = "id")
        fileEventBus.publish(event)
        val countHandler = object : EventHandler {
            var counter = 0
            override fun supportEvent(event: Event): Boolean {
                return event.type == EventType.ACK.name
            }

            override fun handler(event: Event) {
                require(event is AckEvent)
                counter++
            }
        }
        fileEventBus.register(countHandler)
        // 等待消息传递
        Thread.sleep(1000)
        Assertions.assertEquals(1, countHandler.counter)
    }

    @Test
    fun mutiEventBusTest() {
        val logPath = createTempDir()
        val messageConverter = MessageConverterFactory.createSupportAllEventMessageConverter()
        val eventBus1 = FileEventBus(
            logDirPath = logPath.absolutePath,
            delayMillis = 200,
            eventMessageConverter = messageConverter,
            1000L
        )
        val eventBus2 = FileEventBus(
            logDirPath = logPath.absolutePath,
            delayMillis = 200,
            eventMessageConverter = messageConverter,
            1000L
        )
        val eventBus3 = FileEventBus(
            logDirPath = logPath.absolutePath,
            delayMillis = 200,
            eventMessageConverter = messageConverter,
            1000L
        )
        // 等待刷新
        Thread.sleep(2000)
        // 确定所有的eventBus监听相同的日志文件
        Assertions.assertEquals(3, eventBus1.listeners.size)
        Assertions.assertEquals(3, eventBus2.listeners.size)
        Assertions.assertEquals(3, eventBus3.listeners.size)
        Assertions.assertArrayEquals(
            eventBus1.listeners.keys.sorted().toTypedArray(),
            eventBus2.listeners.keys.sorted().toTypedArray()
        )
        Assertions.assertArrayEquals(
            eventBus3.listeners.keys.sorted().toTypedArray(),
            eventBus2.listeners.keys.sorted().toTypedArray()
        )
        // 任何一个eventBus发送的事件都能被监听
        val countHandler = object : EventHandler {
            val counter = AtomicInteger()
            override fun supportEvent(event: Event): Boolean {
                return event.type == EventType.ACK.name
            }

            override fun handler(event: Event) {
                require(event is AckEvent)
                counter.incrementAndGet()
            }
        }
        eventBus2.register(countHandler)
        eventBus3.register(countHandler)
        val event = AckEvent(id = "id")
        eventBus1.publish(event)
        // 等待消息传递
        Thread.sleep(1000)
        Assertions.assertEquals(2, countHandler.counter.get())

        while (!eventBus2.logFile.delete()) {
            Thread.sleep(200)
        }
        // 测试退出
        Thread.sleep(1000)
        Assertions.assertEquals(2, eventBus1.listeners.size)
        Assertions.assertEquals(2, eventBus2.listeners.size)
        Assertions.assertEquals(2, eventBus3.listeners.size)
    }
}
