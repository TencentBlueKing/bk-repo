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

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail

/**
 * 构件事件消费者，用于实时同步
 * 对应binding name为artifactEvent-in-0
 */
open class EventConsumer {

    /**
     * 消息来源判断
     */
    open fun sourceCheck(message: ArtifactEvent): Boolean = false

    /**
     * 允许接收的事件类型
     */
    open fun getAcceptTypes(): Set<EventType> = emptySet()

    fun accept(event: ArtifactEvent) {
        if (!getAcceptTypes().contains(event.type)) {
            return
        }
        if (sourceCheck(event)) {
            return
        }
        action(event)
    }

    /**
     * 执行具体的业务
     */
    open fun action(event: ArtifactEvent) {}


    protected fun storeEventRecord(
        tasks: List<ReplicaTaskDetail>,
        eventRecordDao: EventRecordDao,
        event: ArtifactEvent,
        eventType: String
    ): Map<String, String?> {
        // 为每个taskKey创建一条独立的event记录，并执行任务
        val keyMap = mutableMapOf<String, String?>()
        tasks.forEach { task ->
            // 在消费前保存事件到数据库，每个taskKey单独记录一条
            val eventRecordId = saveEventRecord(eventRecordDao, event, task.task.key, eventType)
            keyMap[task.task.key] = eventRecordId
        }
        return keyMap
    }

    /**
     * 保存事件记录到数据库
     * 为每个taskKey创建一条独立的记录
     * @param eventRecordDao 事件记录数据访问对象
     * @param event 事件对象
     * @param taskKey 任务key
     * @param eventType 事件类型：NORMAL 或 FEDERATION
     * @return record id
     */
    private fun saveEventRecord(
        eventRecordDao: EventRecordDao,
        event: ArtifactEvent,
        taskKey: String,
        eventType: String
    ): String? {
        // 为每个taskKey创建一条独立的记录
        val eventRecord = TEventRecord(
            eventType = eventType,
            event = event,
            taskKey = taskKey,
            taskCompleted = false,
            taskSucceeded = false,
        )

        return try {
            val record = eventRecordDao.saveEventRecord(eventRecord)
            record.id
        } catch (e: Exception) {
            // 即使保存失败，也继续处理事件，避免阻塞
            null
        }
    }
}
