package com.tencent.bkrepo.replication.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.constant.SERVICE_NAME
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/replica")
@FeignClient(SERVICE_NAME, contextId = "PingResource")
interface PingResource {
    @GetMapping("/ping")
    fun ping(): Response<Void>

    @GetMapping("/version")
    fun version(): Response<String>
}
