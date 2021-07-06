/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.job.replicator.ClusterReplicator
import com.tencent.bkrepo.replication.job.replicator.EdgeNodeReplicator
import com.tencent.bkrepo.replication.job.replicator.Replicator
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

/**
 * 绒布任务抽象实现类
 */
open class AbstractReplicaJobExecutor(
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager
) {

    protected val threadPoolExecutor: ThreadPoolExecutor = ReplicaThreadPoolExecutor.instance

    /**
     * 提交任务到线程池执行
     * @param taskDetail 任务详情
     * @param taskRecord 执行记录
     * @param clusterNodeName 远程集群
     */
    protected fun submit(
        taskDetail: ReplicaTaskDetail,
        taskRecord: ReplicaRecordInfo,
        clusterNodeName: ClusterNodeName
    ): Future<ExecutionResult> {
        return threadPoolExecutor.submit<ExecutionResult> {
            try {
                val clusterNode = clusterNodeService.getByClusterId(clusterNodeName.id)
                require(clusterNode != null) { "Cluster[${clusterNodeName.id}] does not exist." }
                var status = ExecutionStatus.SUCCESS
                taskDetail.objects.map { taskObject ->
                    val localRepo = localDataManager.findRepoByName(
                        taskDetail.task.projectId,
                        taskObject.localRepoName,
                        taskObject.repoType.toString()
                    )
                    val context = ReplicaContext(taskDetail, taskObject, taskRecord, localRepo, clusterNode)
                    chooseReplicator(context).replica(context)
                    if (context.status == ExecutionStatus.FAILED) {
                        status = context.status
                    }
                }
                ExecutionResult(status)
            } catch (exception: Throwable) {
                ExecutionResult.fail(exception.message)
            }
        }
    }

    /**
     * 根据context选择合适的数据同步类
     */
    private fun chooseReplicator(context: ReplicaContext): Replicator {
        return when (context.remoteCluster.type) {
            ClusterNodeType.STANDALONE -> SpringContextUtils.getBean<ClusterReplicator>()
            ClusterNodeType.EDGE -> SpringContextUtils.getBean<EdgeNodeReplicator>()
            else -> throw UnsupportedOperationException()
        }
    }

}
