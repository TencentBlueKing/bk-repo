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
import com.tencent.bkrepo.common.frpc.event.GcPrepareEvent
import com.tencent.bkrepo.common.frpc.event.GcRecoverEvent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class GcProcessTest : EventBusBaseTest() {
    @Test
    fun gc() {
        val gcProcess = createGcProcesss(10, 1000)
        // 产生足够的垃圾事件
        repeat(10) {
            fileEventBus.publish(AckEvent("id"))
        }
        val file = gcProcess.getFile()
        val preLogFileSize = file.length()
        gcProcess.gc()
        val curLogFileSize = file.length()
        Assertions.assertTrue(curLogFileSize < preLogFileSize)
    }

    @Test
    fun suspend() {
        val gcProcess = createGcProcesss(Long.MAX_VALUE, 1000)
        // 发布一个gc prepare事件，接受到的服务开始进入gc状态，这个时候停止任何新事件的发布
        fileEventBus.publish(GcPrepareEvent(fileEventBus.logFile.absolutePath))
        // 等待事件被消费
        Thread.sleep(2000)
        Assertions.assertTrue(gcProcess.isInGc())
        thread {
            // 发布不出去
            Assertions.assertFalse(fileEventBus.publish(AckEvent("id")))
        }
        // gc 2s
        Thread.sleep(2000)
        fileEventBus.publish(GcRecoverEvent())
        // 等待事件被消费
        Thread.sleep(2000)
        Assertions.assertTrue(fileEventBus.publish(AckEvent("id")))
    }

    private fun createGcProcesss(maxLogSize: Long, timeout: Long): GcProcess {
        val serviceRegistrarProcess = ServiceRegistrarProcess(fileEventBus)
        val gcProcess = GcProcess(
            fileEventBus,
            fileEventBus.eventMessageConverter,
            maxLogSize,
            timeout,
            serviceRegistrarProcess
        )
        fileEventBus.register(gcProcess)
        fileEventBus.register(serviceRegistrarProcess)
        return gcProcess
    }
}
