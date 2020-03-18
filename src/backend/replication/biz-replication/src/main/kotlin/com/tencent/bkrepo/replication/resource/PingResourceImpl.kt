package com.tencent.bkrepo.replication.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cluster")
class PingResourceImpl {

    @Value("\${spring.application.version}")
    private var version: String = ""

    @GetMapping("/ping")
    fun ping(): Response<Void> {
        return ResponseBuilder.success()
    }

    @GetMapping("/version")
    fun version(): Response<String> {
        return ResponseBuilder.success(version)
    }
}
