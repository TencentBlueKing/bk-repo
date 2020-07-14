package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.permission.Principal
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.log.ReplicationTaskLog
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationTaskInfo
import com.tencent.bkrepo.replication.service.TaskLogService
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/task")
class TaskController @Autowired constructor(
    private val taskService: TaskService,
    private val taskLogService: TaskLogService
) {
    @PostMapping("/connect/test")
    fun testConnect(@RequestBody remoteClusterInfo: RemoteClusterInfo): Response<Void> {
        taskService.tryConnect(remoteClusterInfo)
        return ResponseBuilder.success()
    }

    @PostMapping("/create")
    fun create(@RequestAttribute userId: String, @RequestBody request: ReplicationTaskCreateRequest): Response<ReplicationTaskInfo> {
        return ResponseBuilder.success(taskService.create(userId, request))
    }

    @GetMapping("/list")
    fun list(): Response<List<ReplicationTaskInfo>> {
        return ResponseBuilder.success(taskService.list())
    }

    @GetMapping("/log/list/{taskKey}")
    fun listLog(@PathVariable taskKey: String): Response<List<ReplicationTaskLog>> {
        return ResponseBuilder.success(taskLogService.list(taskKey))
    }

    @GetMapping("/log/latest/{taskKey}")
    fun getLatestLog(@PathVariable taskKey: String): Response<ReplicationTaskLog?> {
        return ResponseBuilder.success(taskLogService.latest(taskKey))
    }

    @GetMapping("/detail/{taskKey}")
    fun detail(@PathVariable taskKey: String): Response<ReplicationTaskInfo?> {
        return ResponseBuilder.success(taskService.detail(taskKey))
    }

    @PostMapping("/interrupt/{taskKey}")
    fun interrupt(@PathVariable taskKey: String): Response<Void> {
        taskService.interrupt(taskKey)
        return ResponseBuilder.success()
    }

    @DeleteMapping("/delete/{taskKey}")
    fun delete(@PathVariable taskKey: String): Response<Void> {
        taskService.delete(taskKey)
        return ResponseBuilder.success()
    }
}
