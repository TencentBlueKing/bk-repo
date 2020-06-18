package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Base
import com.tencent.bkrepo.docker.response.DockerResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class BaseImpl : Base {
    override fun ping(): DockerResponse {
        return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }
}
