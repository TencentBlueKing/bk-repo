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
import com.tencent.bkrepo.replication.replica.executor.EventConsumerThreadPoolExecutor
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.springframework.stereotype.Component

/**
 * 构件事件消费者，用于实时同步
 * 对应binding name为artifactEvent-in-0
 */
@Component
class ArtifactEventConsumer(
    private val replicaTaskService: ReplicaTaskService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor,
    private val eventRecordDao: EventRecordDao,
) : EventConsumer() {

    private val executors = EventConsumerThreadPoolExecutor.instance

    /**
     * 允许接收的事件类型
     */
    override fun getAcceptTypes(): Set<EventType> {
        return setOf(
            EventType.NODE_CREATED,
            EventType.VERSION_CREATED,
            EventType.VERSION_UPDATED
        )
    }

    override fun action(event: ArtifactEvent) {
        // 获取所有相关的任务
        val tasks = replicaTaskService.listRealTimeTasks(event.projectId, event.repoName)
        if (tasks.isEmpty()) {
            return
        }
        // 为每个taskKey创建一条独立的event记录，并执行任务
        val keyMap = storeEventRecord(tasks, eventRecordDao, event, "NORMAL")

        executors.execute(
            Runnable {
                tasks.forEach { task ->
                    // 执行任务，传递事件ID用于跟踪
                    eventBasedReplicaJobExecutor.execute(task, event, keyMap[task.task.key])
                }
            }.trace()
        )
    }
}
