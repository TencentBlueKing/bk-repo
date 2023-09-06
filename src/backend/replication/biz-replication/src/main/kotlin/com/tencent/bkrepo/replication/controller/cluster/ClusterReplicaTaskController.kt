/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.controller.cluster

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.replicator.commitedge.EdgeReplicaContextHolder
import com.tencent.bkrepo.replication.service.EdgeReplicaTaskRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult

@RestController
@Principal(PrincipalType.ADMIN)
class ClusterReplicaTaskController(
    private val replicaTaskService: ReplicaTaskService,
    private val edgeReplicaTaskRecordService: EdgeReplicaTaskRecordService
) : ClusterReplicaTaskClient {
    override fun info(taskId: String): Response<ReplicaTaskInfo?> {
        return ResponseBuilder.success(replicaTaskService.getByTaskId(taskId))
    }

    override fun list(
        replicaType: ReplicaType,
        lastId: String,
        size: Int
    ): Response<List<ReplicaTaskInfo>> {
        return ResponseBuilder.success(replicaTaskService.listTaskByType(replicaType, lastId, size))
    }

    override fun listObject(taskKey: String): Response<List<ReplicaObjectInfo>> {
        return ResponseBuilder.success(replicaTaskService.listTaskObject(taskKey))
    }

    override fun getEdgeReplicaTask(
        clusterName: String,
        replicatingNum: Int
    ): DeferredResult<Response<EdgeReplicaTaskRecord>> {
        val deferredResult = DeferredResult<Response<EdgeReplicaTaskRecord>>(30000L)
        EdgeReplicaContextHolder.addDeferredResult(clusterName, replicatingNum, deferredResult)
        return deferredResult
    }

    override fun reportEdgeReplicaTaskResult(edgeReplicaTaskRecord: EdgeReplicaTaskRecord): Response<Void> {
        edgeReplicaTaskRecordService.updateStatus(
            id = edgeReplicaTaskRecord.id!!,
            status = edgeReplicaTaskRecord.status,
            errorReason = edgeReplicaTaskRecord.errorReason
        )
        return ResponseBuilder.success()
    }
}
