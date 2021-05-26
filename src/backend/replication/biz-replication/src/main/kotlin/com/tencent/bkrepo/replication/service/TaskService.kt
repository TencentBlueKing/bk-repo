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

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicationInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import org.springframework.data.mongodb.core.query.Query

interface TaskService {

    /**
     * 查询任务详情
     */
    fun detail(taskKey: String): ReplicaTaskInfo?
    fun listAllRemoteTask(type: ReplicationType): List<TReplicaTask>
    fun listUndoFullTask(): List<TReplicaTask>
    fun list(): List<ReplicaTaskInfo>
    fun listReplicationTaskInfoPage(
        userId: String,
        name: String?,
        enabled: Boolean?,
        pageNumber: Int,
        pageSize: Int
    ): Page<ReplicaTaskInfo>

    fun buildListQuery(userId: String, name: String?, enabled: Boolean?): Query
    fun isAdminUser(userId: String): Boolean
    fun interrupt(taskKey: String)
    fun delete(taskKey: String)
    fun toggleStatus(userId: String, taskKey: String)
    fun execute(taskKey: String)
    fun canUpdated(taskKey: String): Boolean
    fun update(userId: String, request: ReplicationTaskUpdateRequest): ReplicaTaskInfo

    @Suppress("TooGenericExceptionCaught")
    fun tryConnect(remoteClusterInfo: RemoteClusterInfo)
    fun validate(setting: ReplicaSetting, replicationInfo: List<ReplicationInfo>)
}
