/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.job

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.job.replicator.ArtifactReplicator
import com.tencent.bkrepo.replication.job.replicator.BlobReplicator
import com.tencent.bkrepo.replication.job.replicator.Replicator
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.schedule.ReplicaTaskScheduler
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 调度类型同步任务逻辑实现类
 * 任务由线程池执行
 */
@Suppress("TooGenericExceptionCaught")
@Component
class ScheduledReplicaJobBean(
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val replicaTaskScheduler: ReplicaTaskScheduler
) {
    private val threadPoolExecutor: ThreadPoolExecutor = buildThreadPoolExecutor()

    /**
     * 执行同步任务
     * @param taskId 任务id
     * 该任务只能由一个节点执行，已经成功抢占到锁才能执行到此处
     */
    fun execute(taskId: String) {
        logger.info("Start to execute replication task[$taskId].")
        val task = findAndCheckTask(taskId) ?: return
        var status = ExecutionStatus.SUCCESS
        var errorReason: String? = null
        var recordId: String? = null
        try {
            // 查询同步对象
            val taskDetail = replicaTaskService.getDetailByTaskKey(task.key)
            // 开启新的同步记录
            val taskRecord = replicaTaskService.startNewRecord(task.key).apply { recordId = id }
            val result = task.remoteClusters.map { submit(taskDetail, taskRecord, it) }.map { it.get() }
            result.forEach {
                if (it.status == ExecutionStatus.FAILED) {
                    status = ExecutionStatus.FAILED
                    errorReason = "部分数据同步失败"
                }
            }
        } catch (exception: Exception) {
            // 记录异常
            status = ExecutionStatus.FAILED
            errorReason = exception.message.orEmpty()
        } finally {
            // 保存结果
            replicaRecordService.completeRecord(recordId!!, status, errorReason)
            logger.info("Replica task[$taskId], record[$recordId] finished")
        }
    }

    /**
     * 提交任务到线程池执行
     * @param taskDetail 任务详情
     * @param taskRecord 执行记录
     * @param clusterNodeName 远程集群
     */
    private fun submit(
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
                    val context = ReplicaContext(taskDetail, taskObject, taskRecord, clusterNode)
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
        return when (context.clusterNodeInfo.type) {
            ClusterNodeType.STANDALONE -> SpringContextUtils.getBean<ArtifactReplicator>()
            ClusterNodeType.EDGE -> SpringContextUtils.getBean<BlobReplicator>()
            else -> throw UnsupportedOperationException()
        }
    }

    /**
     * 查找并检查任务状态
     * @return 如果任务不存在或不能被执行，返回null，否则返回任务信息
     */
    private fun findAndCheckTask(taskId: String): ReplicaTaskInfo? {
        // 任务不存在，删除任务
        val task = replicaTaskService.getByTaskId(taskId) ?: run {
            logger.warn("Task[$taskId] does not exist, delete job and trigger.")
            replicaTaskScheduler.deleteJob(taskId)
            return null
        }
        // 任务未开启，跳过
        if (!task.enabled) {
            logger.info("Task[$taskId] status is paused, ignore executing.")
            return null
        }
        // 任务正在执行，跳过
        if (task.lastExecutionStatus == ExecutionStatus.RUNNING) {
            logger.info("Task[$taskId] status is running, ignore executing.")
            return null
        }
        return task
    }

    /**
     * 创建线程池
     */
    private fun buildThreadPoolExecutor(): ThreadPoolExecutor {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("replica-worker-%d").build()
        return ThreadPoolExecutor(
            100, 500, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(10), namedThreadFactory, ThreadPoolExecutor.AbortPolicy()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduledReplicaJobBean::class.java)
    }
}
