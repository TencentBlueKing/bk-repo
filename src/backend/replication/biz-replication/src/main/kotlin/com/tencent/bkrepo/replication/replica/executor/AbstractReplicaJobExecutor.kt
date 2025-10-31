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

import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaOverview
import com.tencent.bkrepo.replication.pojo.record.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.record.ResultsSummary
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.TaskExecuteType
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.type.ReplicaService
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.impl.failure.FailureRecordRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

/**
 * 同步任务抽象实现类
 */
open class AbstractReplicaJobExecutor(
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager,
    private val replicaService: ReplicaService,
    private val replicationProperties: ReplicationProperties,
    private val failureRecordRepository: FailureRecordRepository,
) {

    private val threadPoolExecutor: ThreadPoolExecutor = ReplicaThreadPoolExecutor.instance

    /**
     * 提交任务到线程池执行
     * @param taskDetail 任务详情
     * @param taskRecord 执行记录
     * @param clusterNodeName 远程集群
     * @param event 事件
     */
    protected fun submit(
        taskDetail: ReplicaTaskDetail,
        taskRecord: ReplicaRecordInfo,
        clusterNodeName: ClusterNodeName,
        event: ArtifactEvent? = null
    ): Future<ExecutionResult> {
        return threadPoolExecutor.submit<ExecutionResult>(
            Callable {
                var replicaProgress = ReplicaProgress()
                var context: ReplicaContext? = null
                try {
                    val clusterNode = clusterNodeService.getByClusterId(clusterNodeName.id)
                    require(clusterNode != null) { "Cluster[${clusterNodeName.id}] does not exist." }
                    var status = ExecutionStatus.SUCCESS
                    var message: String? = null
                    taskDetail.objects.map { taskObject ->
                        val localRepo = localDataManager.findRepoByName(
                            taskDetail.task.projectId,
                            taskObject.localRepoName,
                            taskObject.repoType.toString()
                        )
                        context = ReplicaContext(
                            taskDetail = taskDetail,
                            taskObject = taskObject,
                            taskRecord = taskRecord,
                            localRepo = localRepo,
                            remoteCluster = clusterNode,
                            replicationProperties = replicationProperties
                        )
                        event?.let {
                            context!!.event = it
                            context!!.executeType = TaskExecuteType.DELTA
                        }
                        replicaService.replica(context!!)
                        replicaProgress = replicaProgress.plus(context!!.replicaProgress)
                        if (context!!.status == ExecutionStatus.FAILED) {
                            status = context!!.status
                            message = context!!.errorMessage
                        }
                    }
                    ExecutionResult(status = status, errorReason = message, progress = replicaProgress)
                } catch (exception: Throwable) {
                    logger.error("${taskDetail.task.name}/$clusterNodeName] replica exception:${exception}")
                    // 记录分发失败到数据库（针对抛出异常的情况）
                    recordFailureToDatabase(context, exception)
                    ExecutionResult.fail("${clusterNodeName.name}:${exception.message}\n", replicaProgress)
                }
            }.trace()
        )
    }

    /**
     * 记录分发失败到数据库（针对抛出异常的情况）
     */
    private fun recordFailureToDatabase(context: ReplicaContext?, exception: Throwable) {
        if (context == null) return
        with(context) {
            try {
                if (task.replicaType == ReplicaType.RUN_ONCE) {
                    return
                }
                val event = try {
                    if (event != null) event else null
                } catch (e: Exception) {
                    null
                }

                // 记录失败信息
                failureRecordRepository.recordFailure(
                    taskKey = task.key,
                    remoteClusterId = remoteCluster.id!!,
                    projectId = localProjectId,
                    localRepoName = localRepoName,
                    remoteProjectId = remoteProjectId ?: "",
                    remoteRepoName = remoteRepoName ?: "",
                    failureType = task.replicaObjectType,
                    packageConstraint = taskObject.packageConstraints?.firstOrNull(),
                    pathConstraint = taskObject.pathConstraints?.firstOrNull(),
                    failureReason = exception.message ?: "Unknown error",
                    event = event,
                    failedRecordId = failedRecordId
                )
                logger.info(
                    "Recorded failure for task[${task.key}], cluster[${remoteCluster.name}], " +
                        "reason[${exception.message}]"
                )
            } catch (e: Exception) {
                logger.warn("Failed to record failure to database in submit", e)
            }
        }

    }

    /**
     * 以Task维度，汇总线程执行结果
     */
    protected fun getResultsSummary(results: List<ExecutionResult>): ResultsSummary {
        val replicaOverview = ReplicaOverview()
        var status = ExecutionStatus.SUCCESS
        var errorReason = ""
        results.forEach { result ->
            if (result.status == ExecutionStatus.FAILED) {
                status = ExecutionStatus.FAILED
                errorReason = "部分数据同步失败 "
                errorReason += result.errorReason ?: ""
            }
            result.progress?.let { progress ->
                replicaOverview.success += (progress.success + progress.skip)
                replicaOverview.failed += progress.failed
                replicaOverview.conflict += progress.conflict
                replicaOverview.fileSuccess += progress.fileSuccess
                replicaOverview.fileFailed += progress.fileFailed
            }
        }
        return ResultsSummary(replicaOverview, errorReason, status)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractReplicaJobExecutor::class.java)
    }
}
