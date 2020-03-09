package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.pojo.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.ReplicaTaskInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/task")
interface TaskResource {

    @PostMapping("/create")
    fun create(
        @RequestAttribute userId: String,
        @RequestBody request: ReplicaTaskCreateRequest
    ): Response<Void>

    @GetMapping("/list")
    fun list(): Response<List<ReplicaTaskInfo>>

    @GetMapping("/detail/{id}")
    fun detail(@PathVariable id: String): Response<ReplicaTaskInfo?>

    @GetMapping("/pause")
    fun pause(): Response<Any>

    @GetMapping("/resume")
    fun resume(): Response<Any>

    @GetMapping("/stop")
    fun stop(): Response<Any>
}
