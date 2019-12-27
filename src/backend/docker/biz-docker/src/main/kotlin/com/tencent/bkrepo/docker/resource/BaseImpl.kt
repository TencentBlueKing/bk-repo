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

    override fun ping2(request: HttpServletRequest, projectId: String, repoName: String, name: String): ResponseEntity<Any> {
        val restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        println(restOfTheUrl.replaceAfterLast("/bb", "").removePrefix("/v3/$projectId/$repoName/"))
        return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }
}
