/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.manual

import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.base.AbstractReplicaService
import com.tencent.bkrepo.replication.replica.base.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.base.executor.ManualThreadPoolExecutor
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.Semaphore

/**
 * 基于手动执行的一次性任务同步器
 */
@Component
class ManualBasedReplicaService(
    replicaRecordService: ReplicaRecordService,
    localDataManager: LocalDataManager,
    private val replicationProperties: ReplicationProperties
) : AbstractReplicaService(replicaRecordService, localDataManager) {
    private val executor = ManualThreadPoolExecutor.instance
    override fun replica(context: ReplicaContext) {
        val semaphore = Semaphore(replicationProperties.manualConcurrencyNum)
        with(context) {
            // 按包同步
            val futureList = mutableListOf<Future<*>>()
            taskObject.packageConstraints.orEmpty().forEach {
                semaphore.acquire()
                futureList.add(
                    executor.submit(
                        Callable{
                            try {
                                replicaByPackageConstraint(this, it)
                            } finally {
                                semaphore.release()
                            }
                        }.trace()
                )
                )
            }
            // 按路径同步
            taskObject.pathConstraints.orEmpty().forEach {
                semaphore.acquire()
                futureList.add(
                    executor.submit(
                        Callable {
                            try {
                                replicaByPathConstraint(this, it)
                            } finally {
                                semaphore.release()
                            }
                        }.trace()
                    )
                )
            }
            futureList.forEach { it.get() }
        }
    }
}
