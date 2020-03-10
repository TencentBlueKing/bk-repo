package com.tencent.bkrepo.replication.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.TaskResource
import com.tencent.bkrepo.replication.pojo.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.ReplicaTaskInfo
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class TaskResourceImpl @Autowired constructor(
    private val taskService: TaskService
) : TaskResource {
    override fun create(userId: String, request: ReplicaTaskCreateRequest): Response<Void> {
        taskService.createTask(userId, request)
        return ResponseBuilder.success()
    }

    override fun list(): Response<List<ReplicaTaskInfo>> {
        return ResponseBuilder.success(taskService.list())
    }

    override fun detail(id: String): Response<ReplicaTaskInfo?> {
        return ResponseBuilder.success(taskService.detail(id))
    }

    override fun pause(): Response<Any> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun resume(): Response<Any> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun stop(): Response<Any> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
