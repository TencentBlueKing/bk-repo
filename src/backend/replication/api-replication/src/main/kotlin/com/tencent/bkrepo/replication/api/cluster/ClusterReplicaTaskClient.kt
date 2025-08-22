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

package com.tencent.bkrepo.replication.api.cluster

import com.tencent.bkrepo.common.api.constant.REPLICATION_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import io.swagger.v3.oas.annotations.Operation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.context.request.async.DeferredResult

@RequestMapping("/cluster/task")
@FeignClient(REPLICATION_SERVICE_NAME, contextId = "ClusterReplicaTaskClient")
interface ClusterReplicaTaskClient {

    @Operation(summary = "查询同步任务")
    @GetMapping("/info/{taskId}")
    fun info(@PathVariable taskId: String): Response<ReplicaTaskInfo?>

    @Operation(summary = "查询同步任务列表")
    @GetMapping("/list/{replicaType}")
    fun list(
        @PathVariable replicaType: ReplicaType,
        @RequestParam lastId: String,
        @RequestParam size: Int
    ): Response<List<ReplicaTaskInfo>>

    @Operation(summary = "查询同步任务对象")
    @GetMapping("/object/list/{taskKey}")
    fun listObject(@PathVariable taskKey: String): Response<List<ReplicaObjectInfo>>

    @Operation(summary = "认领Edge分发任务")
    @GetMapping("/edge/claim")
    fun getEdgeReplicaTask(
        @RequestParam clusterName: String,
        @RequestParam replicatingNum: Int
    ): DeferredResult<Response<EdgeReplicaTaskRecord>>

    @Operation(summary = "上报Edge分发任务结果")
    @PostMapping("/edge/report")
    fun reportEdgeReplicaTaskResult(@RequestBody edgeReplicaTaskRecord: EdgeReplicaTaskRecord): Response<Void>
}
