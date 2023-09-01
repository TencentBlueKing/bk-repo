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

package com.tencent.bkrepo.replication.replica.type.manual

import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.ManualThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.AbstractReplicaService
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
        replicaTaskObjects(context)
    }

    /**
     * 是否包含所有仓库数据
     */
    override fun includeAllData(context: ReplicaContext): Boolean {
        return context.taskObject.packageConstraints.isNullOrEmpty() &&
            context.taskObject.pathConstraints.isNullOrEmpty() &&
            context.task.replicaObjectType == ReplicaObjectType.REPOSITORY
    }

    /**
     * 同步task object 中的包列表或者paths
     */
    override fun replicaTaskObjectConstraints(replicaContext: ReplicaContext) {
        with(replicaContext) {
            val semaphore = Semaphore(replicationProperties.manualConcurrencyNum)

            // 按包同步
            val futureList = mutableListOf<Future<*>>()
            // 按包同步
            mapEachTaskObject(semaphore, futureList, taskObject.packageConstraints.orEmpty(), replicaContext)
            // 按路径同步
            mapEachTaskObject(semaphore, futureList, taskObject.pathConstraints.orEmpty(), replicaContext)

            futureList.forEach { it.get() }
        }
    }


    /**
     * 遍历执行所有分发任务
     */
    private fun mapEachTaskObject(
        semaphore: Semaphore, futureList: MutableList<Future<*>>,
        taskObjects: List<Any>, context: ReplicaContext
    ) {
        for (taskObject in taskObjects) {
            semaphore.acquire()
            futureList.add(
                executor.submit(
                    Callable{
                        try {
                            replicaTaskObject(context, taskObject)
                        } finally {
                            semaphore.release()
                        }
                    }.trace()
                )
            )
        }
    }

    /**
     * 分发具体内容
     */
    private fun replicaTaskObject(replicaContext: ReplicaContext, constraint: Any) {
        if (constraint is PathConstraint) {
            replicaByPathConstraint(replicaContext, constraint)
        }
        if (constraint is PackageConstraint) {
            replicaByPackageConstraint(replicaContext, constraint)
        }
    }

}
