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

package com.tencent.bkrepo.replication.replica.type.event

import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.util.MessageHelper
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import java.util.concurrent.Executor


/**
 * 构件事件消费者，用于实时同步
 * 对应binding name为artifactEvent-in-0
 *
 * 幂等机制：
 * - 使用消息唯一标识（messageId）+ 任务key（taskKey）作为去重依据
 * - 通过 MongoDB 唯一索引保证原子性检查和插入
 * - 当 ACK 失败或超时导致消息重新推送时，会因为唯一索引冲突而跳过重复消息
 */
open class EventConsumer {

    companion object {
        private val logger = LoggerFactory.getLogger(EventConsumer::class.java)
    }

    /**
     * 消息来源判断
     */
    open fun sourceCheck(message: ArtifactEvent): Boolean = false

    /**
     * 允许接收的事件类型
     */
    open fun getAcceptTypes(): Set<EventType> = emptySet()

    fun accept(message: Message<ArtifactEvent>) {
        if (!getAcceptTypes().contains(message.payload.type)) {
            // 不处理的消息立即ack
            ackMessage(message)
            return
        }
        if (sourceCheck(message.payload)) {
            // 来源检查不通过的消息立即ack
            ackMessage(message)
            return
        }
        action(message)
    }

    /**
     * 执行具体的业务
     */
    open fun action(message: Message<ArtifactEvent>) {}

    /**
     * 处理事件消息的通用模板方法
     *
     * @param message 消息对象
     * @param eventRecordDao 事件记录数据访问对象
     * @param getTasks 获取任务列表的函数
     * @param eventType 事件类型字符串
     * @param executor 线程池执行器
     * @param executeTask 执行任务的函数
     */
    protected fun processAction(
        message: Message<ArtifactEvent>,
        eventRecordDao: EventRecordDao,
        getTasks: (String, String) -> List<ReplicaTaskDetail>,
        eventType: String,
        executor: Executor,
        executeTask: (ReplicaTaskDetail, ArtifactEvent, String) -> Unit
    ) {
        val event = message.payload
        // 获取所有相关的任务
        val tasks = getTasks(event.projectId, event.repoName)
        if (tasks.isEmpty()) {
            // 没有任务需要处理，立即ack
            ackMessage(message)
            return
        }
        // 为每个taskKey创建一条独立的event记录（幂等检查）
        val tasksToProcess = storeEventRecord(tasks, eventRecordDao, message, eventType)

        // 只处理非重复的任务
        if (tasksToProcess.isEmpty()) {
            // 所有任务都是重复的，无需处理
            return
        }

        executor.execute(
            Runnable {
                tasksToProcess.forEach { taskWithRecordId ->
                    // 执行任务，传递事件ID用于跟踪
                    executeTask(taskWithRecordId.task, event, taskWithRecordId.recordId)
                }
            }.trace()
        )
    }

    private fun ackMessage(message: Message<ArtifactEvent>) {
        MessageHelper.acknowledge(message)
    }


    /**
     * 存储事件记录并返回需要处理的任务
     *
     * @param tasks 任务列表
     * @param eventRecordDao 事件记录数据访问对象
     * @param message 消息对象
     * @param eventType 事件类型
     * @return 需要处理的任务列表，每个任务包含对应的recordId（recordId 不为 null）
     */
    private fun storeEventRecord(
        tasks: List<ReplicaTaskDetail>,
        eventRecordDao: EventRecordDao,
        message: Message<ArtifactEvent>,
        eventType: String
    ): List<TaskWithRecordId> {
        val messageId = MessageHelper.getMessageId(message)
        val event = message.payload

        // 为每个taskKey尝试创建一条独立的event记录
        val tasksToProcess = mutableListOf<TaskWithRecordId>()

        tasks.forEach { task ->
            val recordId = tryStoreEventRecord(eventRecordDao, messageId, event, task.task.key, eventType)
            if (recordId != null) {
                // 新记录，需要处理
                tasksToProcess.add(TaskWithRecordId(task, recordId))
            } else {
                // 重复消息，跳过（可能是消息重投导致的幂等）
                logger.info("Skipping duplicate message [$messageId] for task [${task.task.key}]")
            }
        }

        // 所有任务记录都成功持久化到 DB，可以安全 ACK 消息
        // 后续即使执行失败，也可以从 DB 进行重试
        ackMessage(message)
        logger.debug(
            "Message [$messageId] persisted successfully for [${tasksToProcess.size}] tasks. " +
                "Message ACKed. Future failures will be retried from DB."
        )

        return tasksToProcess
    }

    /**
     * 尝试存储事件记录
     *
     * @return 新插入记录的id，如果记录已存在则返回null
     * @throws Exception 存储失败时抛出
     */
    private fun tryStoreEventRecord(
        eventRecordDao: EventRecordDao,
        messageId: String,
        event: ArtifactEvent,
        taskKey: String,
        eventType: String
    ): String? {
        val eventRecord = TEventRecord(
            messageId = messageId,
            eventType = eventType,
            event = event,
            taskKey = taskKey,
            taskCompleted = false,
            taskSucceeded = false,
        )
        return eventRecordDao.tryInsertIfAbsent(eventRecord)
    }

    /**
     * 任务及其对应的事件记录ID
     *
     * @param task 需要处理的任务
     * @param recordId 事件记录ID，用于后续更新任务状态。优化后保证不为 null（存储失败的任务不会加入处理队列）
     */
    data class TaskWithRecordId(
        val task: ReplicaTaskDetail,
        val recordId: String  // 优化后保证不为 null，确保所有任务都能从 DB 重试
    )
}

