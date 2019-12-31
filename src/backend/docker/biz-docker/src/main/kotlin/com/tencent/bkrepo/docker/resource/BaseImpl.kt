package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Base
import javax.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

@RestController
class BaseImpl : Base {
    override fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }

    override fun ping2(request: HttpServletRequest): ResponseEntity<Any> {
        val data = request.getHeader("test")
        val result = String.format("{\"test\":\"%s\"}",data)
        return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body(result)

    }
}
