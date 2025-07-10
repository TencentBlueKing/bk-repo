/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.base.process

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class ProgressListener {

    private val replicaTaskDao by lazy { SpringContextUtils.getBean<ReplicaTaskDao>() }
    private val contextMap = ConcurrentHashMap<String, Progress>()

    fun onStart(task: ReplicaTaskInfo, key: String, miscLength: Long) {
        if (task.replicaType != ReplicaType.RUN_ONCE) {
            return
        }
        if (contextMap.containsKey(task.id)) {
            val progress = contextMap[task.id]!!
            progress.totalBytes.addAndGet(miscLength)
            progress.replicatedBytes[key] = AtomicLong(0)
        } else {
            contextMap[task.id] = Progress(
                replicatedBytes = ConcurrentHashMap(mapOf(key to AtomicLong(0))),
                totalBytes = AtomicLong(task.totalBytes!! + miscLength),
                lastRecordTime = LocalDateTime.now()
            )
        }
    }

    fun onProgress(task: ReplicaTaskInfo, key: String, written: Long) {
        if (task.replicaType != ReplicaType.RUN_ONCE) {
            return
        }
        val progress = contextMap[task.id]!!
        progress.replicatedBytes[key]?.addAndGet(written)
        if (LocalDateTime.now().minusSeconds(RECORD_TIME_INTERVAL).isAfter(progress.lastRecordTime)) {
            progress.lastRecordTime = LocalDateTime.now()
            updateProgress(task)
        }
    }

    fun onSuccess(task: ReplicaTaskInfo) {
        if (task.replicaType != ReplicaType.RUN_ONCE) {
            return
        }
        updateProgress(task)
    }

    fun onFailed(task: ReplicaTaskInfo, key: String) {
        if (task.replicaType != ReplicaType.RUN_ONCE) {
            return
        }
        val progress = contextMap[task.id]!!
        progress.replicatedBytes[key] = AtomicLong(0)
        updateProgress(task)
    }

    fun onFinish(taskId: String) {
        contextMap.remove(taskId)
    }

    private fun updateProgress(task: ReplicaTaskInfo) {
        val query = Query.query(where(TReplicaTask::key).isEqualTo(task.key))
        val update = Update()
            .set(TReplicaTask::replicatedBytes.name, contextMap[task.id]!!.replicatedBytes.values.sumOf { it.get() })
            .set(TReplicaTask::totalBytes.name, contextMap[task.id]!!.totalBytes.get())
        replicaTaskDao.updateFirst(query, update)
    }

    companion object {
        private const val RECORD_TIME_INTERVAL = 5L
    }
}
