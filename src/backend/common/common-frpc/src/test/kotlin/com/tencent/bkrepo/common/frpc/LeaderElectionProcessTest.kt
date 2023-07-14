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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LeaderElectionProcessTest : EventBusBaseTest() {
    @Test
    fun test() {
        // 开启三个选举进程
        val process1 = createLeaderElectionProcess()
        val process2 = createLeaderElectionProcess()
        val process3 = createLeaderElectionProcess()
        // 隔一定时间等待选举结束
        process1.await(60000)
        process2.await(60000)
        process3.await(60000)
        Assertions.assertNotNull(process1.leader)
        Assertions.assertNotNull(process2.leader)
        Assertions.assertNotNull(process3.leader)
        Assertions.assertEquals(process1.localServiceId, process1.leader)
        Assertions.assertTrue(process1.leader == process2.leader)
        Assertions.assertTrue(process2.leader == process3.leader)
    }

    private fun createLeaderElectionProcess(): LeaderElectionProcess {
        val serviceRegistrarProcess = ServiceRegistrarProcess(fileEventBus)
        val leaderElectionProcess = LeaderElectionProcess(fileEventBus, serviceRegistrarProcess)
        // 添加了多个handler
        fileEventBus.register(serviceRegistrarProcess)
        fileEventBus.register(leaderElectionProcess)
        return leaderElectionProcess
    }
}
