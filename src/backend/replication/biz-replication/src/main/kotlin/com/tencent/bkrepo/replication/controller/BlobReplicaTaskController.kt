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

package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.log.ReplicationTaskLog
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.service.TaskLogService
import com.tencent.bkrepo.replication.service.TaskService
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * blob数据同步任务接口
 */
@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/task/blob")
class BlobReplicaTaskController(
    private val taskService: TaskService,
    private val taskLogService: TaskLogService
) {
    @ApiOperation("远程连接测试")
    @PostMapping("/connect/test")
    fun testConnect(@RequestBody remoteClusterInfo: RemoteClusterInfo): Response<Void> {
        taskService.tryConnect(remoteClusterInfo)
        return ResponseBuilder.success()
    }

    @ApiOperation("创建任务")
    @PostMapping("/create")
    fun create(@RequestBody request: ReplicaTaskCreateRequest): Response<ReplicaTaskInfo> {
        return ResponseBuilder.success(taskService.create(request))
    }

    @ApiOperation("列表查询任务")
    @GetMapping("/list")
    fun list(): Response<List<ReplicaTaskInfo>> {
        return ResponseBuilder.success(taskService.list())
    }

    @ApiOperation("分页查询任务")
    @GetMapping("/page")
    fun listReplicationTaskInfoPage(
        @ApiParam(value = "当前页", required = true, example = "1")
        @RequestParam pageNumber: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @RequestParam pageSize: Int,
        @ApiParam(value = "任务名称", required = false)
        @RequestParam name: String? = null,
        @ApiParam(value = "任务状态", required = false)
        @RequestParam enabled: Boolean? = null
    ): Response<Page<ReplicaTaskInfo>> {
        return ResponseBuilder.success(
            taskService.listReplicationTaskInfoPage(name, enabled, pageNumber, pageSize)
        )
    }

    @ApiOperation("查询任务详情")
    @GetMapping("/detail/{taskKey}")
    fun detail(@PathVariable taskKey: String): Response<ReplicaTaskInfo?> {
        return ResponseBuilder.success(taskService.detail(taskKey))
    }

    @ApiOperation("中断任务")
    @PostMapping("/interrupt/{taskKey}")
    fun interrupt(@PathVariable taskKey: String): Response<Void> {
        taskService.interrupt(taskKey)
        return ResponseBuilder.success()
    }

    @ApiOperation("删除任务")
    @DeleteMapping("/delete/{taskKey}")
    fun delete(@PathVariable taskKey: String): Response<Void> {
        taskService.delete(taskKey)
        return ResponseBuilder.success()
    }

    @ApiOperation("任务启停状态切换")
    @PostMapping("/toggle/status/{taskKey}")
    fun toggleStatus(@PathVariable taskKey: String): Response<Void> {
        taskService.toggleStatus(taskKey)
        return ResponseBuilder.success()
    }

    @ApiOperation("执行任务")
    @PostMapping("/execute/{taskKey}")
    fun execute(@PathVariable taskKey: String): Response<Void> {
        taskService.execute(taskKey)
        return ResponseBuilder.success()
    }

    @ApiOperation("任务是否可以编辑")
    @GetMapping("/canUpdated/{taskKey}")
    fun isUpdated(@PathVariable taskKey: String): Response<Boolean> {
        return ResponseBuilder.success(taskService.canUpdated(taskKey))
    }

    @ApiOperation("更新任务")
    @PostMapping("/update")
    fun updated(@RequestBody request: ReplicationTaskUpdateRequest): Response<ReplicaTaskInfo> {
        return ResponseBuilder.success(taskService.update(request))
    }

    @ApiOperation("列表查询任务执行日志")
    @GetMapping("/log/list/{taskKey}")
    fun listLog(@PathVariable taskKey: String): Response<List<ReplicationTaskLog>> {
        return ResponseBuilder.success(taskLogService.list(taskKey))
    }

    @ApiOperation("分页查询任务执行日志")
    @GetMapping("/log/page/{taskKey}")
    fun listLogPage(
        @ApiParam(value = "当前页", required = true, example = "1")
        @RequestParam pageNumber: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @RequestParam pageSize: Int,
        @ApiParam(value = "任务key", required = true)
        @PathVariable taskKey: String
    ): Response<Page<ReplicationTaskLog>> {
        return ResponseBuilder.success(taskLogService.listLogPage(taskKey, pageNumber, pageSize))
    }

    @ApiOperation("查询最新的执行日志")
    @GetMapping("/log/latest/{taskKey}")
    fun getLatestLog(@PathVariable taskKey: String): Response<ReplicationTaskLog?> {
        return ResponseBuilder.success(taskLogService.latest(taskKey))
    }
}

