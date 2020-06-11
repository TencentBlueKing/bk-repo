package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.permission.Principal
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationTaskInfo
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/task")
class TaskController @Autowired constructor(
    private val taskService: TaskService
) {
    @PostMapping("/connect/test")
    fun testConnect(@RequestBody remoteClusterInfo: RemoteClusterInfo): Response<Void> {
        taskService.testConnect(remoteClusterInfo)
        return ResponseBuilder.success()
    }

    @PostMapping("/create")
    fun create(@RequestAttribute userId: String, @RequestBody request: ReplicationTaskCreateRequest): Response<ReplicationTaskInfo> {
        return ResponseBuilder.success(taskService.create(userId, request))
    }

    @PostMapping("/create/full")
    fun createFull(@RequestAttribute userId: String, @RequestParam username: String, @RequestParam password: String, @RequestParam url: String): Response<Boolean> {
        return ResponseBuilder.success(taskService.createFull(userId, username, password, url))
    }

    @GetMapping("/list")
    fun list(): Response<List<ReplicationTaskInfo>> {
        return ResponseBuilder.success(taskService.list())
    }

    @GetMapping("/detail/{id}")
    fun detail(@PathVariable id: String): Response<ReplicationTaskInfo?> {
        return ResponseBuilder.success(taskService.detail(id))
    }

    @PutMapping("/pause/{id}")
    fun pause(@PathVariable id: String): Response<Void> {
        taskService.pause(id)
        return ResponseBuilder.success()
    }

    @PutMapping("/resume/{id}")
    fun resume(@PathVariable id: String): Response<Void> {
        taskService.resume(id)
        return ResponseBuilder.success()
    }

    @PutMapping("/delete/{id}")
    fun delete(@PathVariable id: String): Response<Void> {
        taskService.delete(id)
        return ResponseBuilder.success()
    }
}
